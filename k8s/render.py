#!/usr/bin/env python3
"""Render a reviewed production deployment locally without using a kube context."""
import argparse
import json
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

import yaml

from validate import PINNED_IMAGE, validate


ROOT = Path(__file__).resolve().parent
SERVICES = ("account-service", "workspace-service", "intelligence-service", "api-gateway", "frontend")
DNS_NAME = re.compile(r"(?=.{1,253}$)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")


def render(settings, kubectl="kubectl", overlay_name="production"):
    required = {"frontend_host", "www_host", "api_host", "ingress_class", "ingress_controller_namespace", "tls_cluster_issuer", "tls_secret_name", "image_pull_secret", "images"}
    if overlay_name == "demo":
        required.add("storage_class")
    if set(settings) != required:
        raise ValueError("Settings must contain exactly the keys in settings.example.json")
    for key in required - {"images", "image_pull_secret"}:
        value = settings[key]
        if not isinstance(value, str) or not DNS_NAME.fullmatch(value) or ".." in value:
            raise ValueError(f"Expected a DNS name for {key}")
        if key.endswith("_host") and value.endswith(".example.com"):
            raise ValueError(f"Replace the placeholder with your confirmed deployment hostname: {key}")
    pull_secret = settings["image_pull_secret"]
    if pull_secret and not DNS_NAME.fullmatch(pull_secret):
        raise ValueError("image_pull_secret must be a Kubernetes Secret name or empty")
    if set(settings["images"]) != set(SERVICES):
        raise ValueError("Provide all five images from settings.example.json")
    for service, image in settings["images"].items():
        if not PINNED_IMAGE.fullmatch(image) or "example.com" in image or image.endswith("0" * 64):
            raise ValueError(f"Provide a published registry image with its real SHA256 digest: {service}")

    # Only copy known nonsecret manifests; never copy local Secret files.
    files = [f"infra/{name}.yaml" for name in ("kustomization", "namespaces", "core-network-policies", "ingress", "pod-disruption-budgets")]
    files += [f"services/{name}.yaml" for name in ("kustomization", *SERVICES)]
    files += [f"{overlay_name}/kustomization.yaml"]
    if overlay_name == "production":
        files += ["production/managed-endpoints-patch.yaml"]
    else:
        files += ["demo/minio-init.yaml"]
        files += [f"stateful/{name}.yaml" for name in ("kustomization", "pgvector", "kafka", "redis", "minio")]
    with tempfile.TemporaryDirectory(prefix="codegen-k8s-") as temp:
        root = Path(temp)
        for file in files:
            destination = root / file
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / file, destination)
        config_path = root / "infra/kustomization.yaml"
        config = yaml.safe_load(config_path.read_text())
        values = {
            "FRONTEND_HOST": settings["frontend_host"],
            "FRONTEND_WWW_HOST": settings["www_host"],
            "API_HOST": settings["api_host"],
            "APP_FRONTEND_URL": f"https://{settings['frontend_host']}",
            "CORS_ALLOWED_ORIGINS": f"https://{settings['frontend_host']},https://{settings['www_host']}",
        }
        generator = config["configMapGenerator"][0]
        generator["literals"] = [f"{key}={values.get(key, value)}" for key, value in (literal.split("=", 1) for literal in generator["literals"])]
        config_path.write_text(yaml.safe_dump(config, sort_keys=False))

        overlay_path = root / overlay_name / "kustomization.yaml"
        overlay = yaml.safe_load(overlay_path.read_text())
        if overlay_name == "demo":
            operations = yaml.safe_load(overlay["patches"][0]["patch"])
            operations[0]["value"] = settings["storage_class"]
            overlay["patches"][0]["patch"] = json.dumps(operations)
        overlay["images"] = [{"name": f"docker.io/richak2319/codegen-{service}", "newName": image.split("@", 1)[0], "digest": image.split("@", 1)[1]} for service, image in settings["images"].items()]
        ingress_patch = {
            "apiVersion": "networking.k8s.io/v1", "kind": "Ingress",
            "metadata": {"name": "codegen-main-ingress", "namespace": "codegen-core", "annotations": {"cert-manager.io/cluster-issuer": settings["tls_cluster_issuer"]}},
            "spec": {"ingressClassName": settings["ingress_class"], "tls": [{"hosts": [settings["frontend_host"], settings["www_host"], settings["api_host"]], "secretName": settings["tls_secret_name"]}]},
        }
        overlay["patches"].append({"patch": yaml.safe_dump(ingress_patch)})
        overlay["patches"].append({
            "target": {"kind": "NetworkPolicy", "name": "allow-nginx-ingress"},
            "patch": json.dumps([{"op": "replace", "path": "/spec/ingress/0/from/0/namespaceSelector/matchLabels/kubernetes.io~1metadata.name", "value": settings["ingress_controller_namespace"]}]),
        })
        if pull_secret:
            overlay["patches"].append({
                "target": {"kind": "Deployment"},
                "patch": json.dumps([{"op": "add", "path": "/spec/template/spec/imagePullSecrets", "value": [{"name": pull_secret}]}]),
            })
        overlay_path.write_text(yaml.safe_dump(overlay, sort_keys=False))
        environment = dict(os.environ, KUBECONFIG=os.devnull)
        output = subprocess.run([kubectl, "kustomize", str(overlay_path.parent)], check=True, capture_output=True, text=True, env=environment).stdout
        validate(list(yaml.safe_load_all(output)), require_pinned_images=True, overlay=overlay_name)
        return output


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("settings", type=Path, help="Nonsecret deployment settings JSON")
    parser.add_argument("output", type=Path, help="Rendered manifest path")
    parser.add_argument("--kubectl", default="kubectl", help="kubectl executable used only for offline kustomize")
    parser.add_argument("--overlay", choices=("production", "demo"), default="production")
    args = parser.parse_args()
    output = render(json.loads(args.settings.read_text()), args.kubectl, args.overlay)
    args.output.write_text(output)
    print("Rendered and validated all five applications. No cluster was contacted.")
