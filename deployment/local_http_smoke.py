#!/usr/bin/env python3
"""Run built service JARs with ephemeral H2 databases to check real HTTP routing.

This exercises application/security code, not PostgreSQL migrations, Kafka,
Redis, object storage, billing, previews, or AI generation. It never uses live
credentials or writes process logs. Only child processes created here are stopped.
"""
import argparse
from collections import deque
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request

sys.dont_write_bytecode = True
from http_smoke import smoke


MODULES = ("account-service", "workspace-service", "intelligence-service", "api-gateway")
EXCEPTION_PATTERN = re.compile(r"\b(?:[a-zA-Z_$][\w$]*\.)+[A-Za-z_$][\w$]*(?:Exception|Error)\b")
BEAN_PATTERN = re.compile(r"(?:bean with name|creating bean with name) '([A-Za-z_$][\w$.-]*)'")


def collect_diagnostics(process, diagnostics):
    """Keep only class/bean names, never raw config, credentials, or log lines."""
    for line in process.stdout:
        for match in EXCEPTION_PATTERN.findall(line):
            diagnostics.append("exception=" + match)
        for match in BEAN_PATTERN.findall(line):
            diagnostics.append("bean=" + match)


def wait_ready(module, process, port, deadline):
    prefix = "" if module == "api-gateway" else "/" + module.removesuffix("-service")
    health = f"http://127.0.0.1:{port}{prefix}/actuator/health/liveness"
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"{module} exited during startup (exit {process.returncode})")
        try:
            with opener.open(health, timeout=2) as response:
                if response.status == 200:
                    print(f"PASS: {module} is listening on loopback", flush=True)
                    return
        except (urllib.error.URLError, TimeoutError, OSError):
            pass
        time.sleep(0.25)
    raise RuntimeError(f"{module} did not become ready before the startup deadline")


def run(args):
    urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
    root = Path(__file__).resolve().parents[1]
    java = args.java_home / "bin/java"
    if not java.is_file() or not args.h2_jar.is_file():
        raise RuntimeError("Java executable or H2 driver is missing; set --java-home and --h2-jar")
    jars = {}
    for module in MODULES:
        candidates = sorted((root / module / "target").glob(f"{module}-*.jar"))
        if len(candidates) != 1:
            raise RuntimeError(f"Expected one executable JAR for {module}; run Maven package first")
        jars[module] = candidates[0]

    # Reserve each loopback port until immediately before its own child starts.
    reservations = {}
    for module in (*MODULES, "unused-integrations"):
        reservation = socket.socket()
        reservation.bind(("127.0.0.1", 0))
        reservations[module] = reservation
    ports = {module: sock.getsockname()[1] for module, sock in reservations.items()}
    base_url = f"http://127.0.0.1:{ports['api-gateway']}"
    offline_url = f"http://127.0.0.1:{ports['unused-integrations']}"

    # Deliberately avoid inheriting cloud credentials, JVM injection options,
    # proxies, or application environment from the caller's shell.
    environment = {
        "PATH": "/usr/bin:/bin",
        "LANG": "en_US.UTF-8",
        "TZ": "UTC",
        "SERVER_ADDRESS": "127.0.0.1",
        "JWT_SECRET": secrets.token_urlsafe(64),
        "INTERNAL_SERVICE_KEY": secrets.token_urlsafe(48),
        "DB_PASSWORD": secrets.token_urlsafe(24),
        "SPRING_DATASOURCE_USERNAME": "sa",
        "SPRING_DATASOURCE_DRIVER_CLASS_NAME": "org.h2.Driver",
        "SPRING_JPA_HIBERNATE_DDL_AUTO": "create-drop",
        "SPRING_FLYWAY_ENABLED": "false",
        "SPRING_KAFKA_LISTENER_AUTO_STARTUP": "false",
        "SPRING_KAFKA_ADMIN_AUTO_CREATE": "false",
        "KAFKA_BOOTSTRAP_SERVERS": f"127.0.0.1:{ports['unused-integrations']}",
        "REDIS_URL": f"redis://127.0.0.1:{ports['unused-integrations']}",
        "MANAGEMENT_HEALTH_REDIS_ENABLED": "false",
        "STRIPE_API_SECRET": "offline-smoke-placeholder",
        "STRIPE_WEBHOOK_SECRET": "offline-smoke-placeholder",
        "AI_API_KEY": "offline-smoke-placeholder",
        "SPRING_AI_OPENAI_BASE_URL": offline_url,
        "MINIO_URL": offline_url,
        "MINIO_ROOT_USER": "offline-smoke",
        "MINIO_ROOT_PASSWORD": secrets.token_urlsafe(24),
        "PREVIEW_ENABLED": "false",
        "KUBERNETES_MASTER": offline_url,
        "KUBERNETES_AUTH_TRYKUBECONFIG": "false",
        "KUBERNETES_AUTH_TRYSERVICEACCOUNT": "false",
        "KUBECONFIG": os.devnull,
        "CORS_ALLOWED_ORIGINS": base_url,
        "APP_FRONTEND_URL": base_url,
        "SPRING_MAIN_BANNER_MODE": "off",
        "LOGGING_LEVEL_ROOT": "WARN",
        "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_GATEWAY": "WARN",
        "LOGGING_LEVEL_REACTOR_NETTY_HTTP_CLIENT": "WARN",
    }
    for module in MODULES[:-1]:
        environment[module.replace("-", "_").upper() + "_URI"] = f"http://127.0.0.1:{ports[module]}"

    processes = {}
    threads = []
    diagnostics = {module: deque(maxlen=40) for module in MODULES}
    try:
        with tempfile.TemporaryDirectory(prefix="codegen-http-smoke-") as temporary:
            for module in MODULES:
                module_env = environment | {
                    "PORT": str(ports[module]),
                    "SPRING_DATASOURCE_URL": (
                        f"jdbc:h2:mem:{module.replace('-', '_')};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
                    ),
                }
                command = [
                    str(java), "-Xmx256m", "-XX:ActiveProcessorCount=2",
                    "-XX:-HeapDumpOnOutOfMemoryError", f"-XX:ErrorFile={os.devnull}",
                    f"-Dloader.path={args.h2_jar.resolve()}",
                    "-cp", str(jars[module]),
                    "org.springframework.boot.loader.launch.PropertiesLauncher",
                ]
                reservations[module].close()
                process = subprocess.Popen(
                    command, env=module_env, cwd=temporary,
                    stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT, text=True, errors="replace",
                )
                processes[module] = process
                thread = threading.Thread(target=collect_diagnostics, args=(process, diagnostics[module]), daemon=True)
                thread.start()
                threads.append(thread)

            deadline = time.monotonic() + args.startup_timeout
            for module, process in processes.items():
                wait_ready(module, process, ports[module], deadline)

            # The shared helper sends a browser Origin and keeps synthetic
            # passwords and bearer tokens in memory without printing them.
            smoke(base_url, create_test_data=True)
            print("PASS: real gateway/service HTTP smoke using ephemeral H2 databases", flush=True)
            print("Not exercised: PostgreSQL migrations, Kafka, Redis, MinIO contents, billing, previews, AI.", flush=True)
    except Exception:
        for module, items in diagnostics.items():
            if items:
                print(f"DIAGNOSTIC: {module}: " + ", ".join(dict.fromkeys(items)), flush=True)
        raise
    finally:
        for reservation in reservations.values():
            reservation.close()
        for process in processes.values():
            if process.poll() is None:
                process.terminate()
        for process in processes.values():
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
        for thread in threads:
            thread.join(timeout=1)
        print("Stopped only the child services created by this smoke run.", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", type=Path, required=True, help="JDK 21 installation directory")
    parser.add_argument("--h2-jar", type=Path, default=Path.home() / ".m2/repository/com/h2database/h2/2.4.240/h2-2.4.240.jar")
    parser.add_argument("--startup-timeout", type=float, default=120, help="Total startup deadline in seconds")
    options = parser.parse_args()
    try:
        run(options)
    except KeyboardInterrupt:
        print("FAIL: smoke run interrupted")
        raise SystemExit(130)
    except Exception as error:
        if isinstance(error, (RuntimeError, AssertionError)):
            print("FAIL:", error)
        else:
            print("FAIL:", type(error).__name__)
        raise SystemExit(1)
