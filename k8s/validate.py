#!/usr/bin/env python3
"""Validate rendered application wiring offline; never read cluster state or secrets."""
import argparse
import re
from pathlib import Path
from urllib.parse import urlsplit

import yaml


ROOT = Path(__file__).resolve().parent
PINNED_IMAGE = re.compile(r"^[^\s@]+@sha256:[0-9a-f]{64}$")


def validate(documents, require_pinned_images=False, overlay="production"):
    resources = {}
    for item in documents:
        if not item:
            continue
        key = (item["kind"], item["metadata"].get("namespace", ""), item["metadata"]["name"])
        assert key not in resources, f"Duplicate resource: {key}"
        resources[key] = item

    def get(kind, namespace, name):
        assert (kind, namespace, name) in resources, f"Missing {kind}: {namespace}/{name}"
        return resources[(kind, namespace, name)]

    # Values from these tracked examples are never printed or included in output.
    secret_keys = {}
    for path in (ROOT / "infra/app-secrets.example.yaml", ROOT / "production/runtime-endpoints.example.yaml"):
        for item in yaml.safe_load_all(path.read_text()):
            if item and item.get("kind") == "Secret":
                secret_keys[(item["metadata"]["namespace"], item["metadata"]["name"])] = set(item.get("stringData", {})) | set(item.get("data", {}))

    deployments = [item for key, item in resources.items() if key[0] == "Deployment"]
    expected = {"account-service", "workspace-service", "intelligence-service", "api-gateway", "codegen-frontend"}
    assert {item["metadata"]["name"] for item in deployments} == expected, "Production must contain all five application deployments"
    stateful = [item for key, item in resources.items() if key[0] == "StatefulSet"]
    if overlay == "production":
        assert not stateful, "Production dependencies must be provisioned separately"
    else:
        assert {item["metadata"]["name"] for item in stateful} == {"pgvector", "kafka", "redis", "minio"}, "Demo must include all four dependencies"
        for item in stateful:
            if item["metadata"]["name"] == "redis":
                assert not item["spec"].get("volumeClaimTemplates"), "Demo Redis is ephemeral preview routing state"
                assert "emptyDir" in item["spec"]["template"]["spec"]["volumes"][0], "Demo Redis must use ephemeral storage"
                continue
            claim = item["spec"]["volumeClaimTemplates"][0]["spec"]
            assert claim["storageClassName"] != "standard-rwo", "Select a storage class confirmed in the target OKE cluster"
            assert claim["resources"]["requests"]["storage"] == "50Gi", "OKE demo requests 50Gi per volume"
        workspace = next(item for item in deployments if item["metadata"]["name"] == "workspace-service")
        assert any(env["name"] == "PREVIEW_ENABLED" and env.get("value") == "false" for env in workspace["spec"]["template"]["spec"]["containers"][0]["env"]), "Ephemeral demo Redis requires previews to stay disabled"
        kafka_service = get("Service", "codegen-core", "kafka")
        assert kafka_service["spec"].get("publishNotReadyAddresses") is True, "Kafka controller DNS must publish before broker readiness"
        kafka = next(item for item in stateful if item["metadata"]["name"] == "kafka")
        startup = kafka["spec"]["template"]["spec"]["containers"][0].get("startupProbe", {})
        assert startup.get("tcpSocket", {}).get("port") == 9092 and startup.get("failureThreshold", 0) * startup.get("periodSeconds", 0) >= 180, "Kafka requires a broker startup grace period"
    jobs = [item for key, item in resources.items() if key[0] == "Job"]
    if overlay == "demo":
        assert {item["metadata"]["name"] for item in jobs} == {"minio-init"}, "Demo requires the project-bucket initialization Job"
    workloads = deployments + stateful + jobs

    for deployment in workloads:
        namespace = deployment["metadata"]["namespace"]
        get("Namespace", "", namespace)
        name = deployment["metadata"]["name"]
        template = deployment["spec"]["template"]
        labels = template["metadata"]["labels"]
        selector = deployment["spec"].get("selector", {}).get("matchLabels", {})
        assert all(labels.get(key) == value for key, value in selector.items()), f"Deployment selector mismatch: {name}"
        spec = template["spec"]
        if "serviceAccountName" in spec:
            get("ServiceAccount", namespace, spec["serviceAccountName"])
        for container in spec.get("initContainers", []) + spec["containers"]:
            registry, separator, _ = container["image"].partition("/")
            assert separator and ("." in registry or ":" in registry or registry == "localhost"), f"Image needs an explicit registry: {name}/{container['name']}"
            if require_pinned_images and deployment["kind"] == "Deployment":
                assert PINNED_IMAGE.fullmatch(container["image"]), f"Image must use a digest: {name}"
            ports = {port["containerPort"] for port in container.get("ports", [])}
            for probe in ("startupProbe", "readinessProbe", "livenessProbe"):
                if probe in container and "httpGet" in container[probe]:
                    assert container[probe]["httpGet"]["port"] in ports, f"Probe port mismatch: {name}/{probe}"
            env_names = set()
            for env in container.get("env", []):
                assert env["name"] not in env_names, f"Duplicate environment variable: {name}/{env['name']}"
                env_names.add(env["name"])
                assert not ("value" in env and "valueFrom" in env), f"Environment variable has both value and valueFrom: {name}/{env['name']}"
                source = env.get("valueFrom", {})
                if "configMapKeyRef" in source:
                    ref = source["configMapKeyRef"]
                    config = get("ConfigMap", namespace, ref["name"])
                    assert ref["key"] in config["data"], f"Missing ConfigMap key: {name}/{env['name']}"
                if "secretKeyRef" in source:
                    ref = source["secretKeyRef"]
                    assert ref["key"] in secret_keys.get((namespace, ref["name"]), set()), f"Undocumented Secret key: {name}/{env['name']}"
                if env["name"].endswith("_SERVICE_URI") or env["name"] == "API_GATEWAY_UPSTREAM":
                    uri = urlsplit(env["value"])
                    service = get("Service", namespace, uri.hostname)
                    assert (uri.port or 80) in {port["port"] for port in service["spec"]["ports"]}, f"Service URI port mismatch: {name}/{env['name']}"
            for source in container.get("envFrom", []):
                if "configMapRef" in source:
                    get("ConfigMap", namespace, source["configMapRef"]["name"])
                if "secretRef" in source:
                    ref = source["secretRef"]
                    assert ref.get("optional") or (namespace, ref["name"]) in secret_keys, f"Undocumented required Secret: {name}"

    for key, service in resources.items():
        if key[0] != "Service":
            continue
        selector = service["spec"]["selector"]
        targets = [dep for dep in workloads if dep["metadata"]["namespace"] == key[1] and all(dep["spec"]["template"]["metadata"]["labels"].get(label) == value for label, value in selector.items())]
        assert targets, f"Service has no matching Deployment: {key[2]}"
        for port in service["spec"]["ports"]:
            for target in targets:
                ports = {entry["containerPort"] for container in target["spec"]["template"]["spec"]["containers"] for entry in container.get("ports", [])}
                assert port.get("targetPort", port["port"]) in ports, f"Service targetPort mismatch: {key[2]}"

    ingress = get("Ingress", "codegen-core", "codegen-main-ingress")
    hosts = [rule["host"] for rule in ingress["spec"]["rules"]]
    assert len(set(hosts)) == 3, "Ingress hosts must be distinct"
    assert set(hosts) == set(ingress["spec"]["tls"][0]["hosts"]), "Ingress TLS hosts differ from routing hosts"
    for rule in ingress["spec"]["rules"]:
        for path in rule["http"]["paths"]:
            backend = path["backend"]["service"]
            service = get("Service", "codegen-core", backend["name"])
            assert backend["port"]["number"] in {port["port"] for port in service["spec"]["ports"]}, "Ingress backend port mismatch"
    configs = [item for key, item in resources.items() if key[0] == "ConfigMap" and key[2].startswith("codegen-shared-config")]
    assert len(configs) == 1, "Expected one shared ConfigMap"
    config = configs[0]["data"]
    assert config["APP_FRONTEND_URL"] == f"https://{hosts[0]}", "Frontend URL differs from ingress"
    assert set(config["CORS_ALLOWED_ORIGINS"].split(",")) == {f"https://{hosts[0]}", f"https://{hosts[1]}"}, "CORS origins differ from frontend hosts"
    policy = get("NetworkPolicy", "codegen-core", "allow-internal-only")
    assert any(peer.get("namespaceSelector", {}).get("matchLabels", {}).get("kubernetes.io/metadata.name") == "codegen-core" for rule in policy["spec"].get("ingress", []) for peer in rule.get("from", [])), "Core service traffic is not allowed"
    return len(resources)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--require-pinned-images", action="store_true")
    parser.add_argument("--overlay", choices=("production", "demo"), default="production")
    args = parser.parse_args()
    count = validate(list(yaml.safe_load_all(args.manifest.read_text())), args.require_pinned_images, args.overlay)
    print(f"Validated {count} Kubernetes resources offline: service wiring, configuration references, ports, probes, ingress and CORS.")
