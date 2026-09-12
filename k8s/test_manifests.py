#!/usr/bin/env python3
"""Regression checks for both real overlays. All kubectl invocations are offline."""
import copy
import json
import os
import subprocess
import unittest

import yaml

from render import ROOT, render
from validate import validate


KUBECTL = os.environ.get("KUBECTL", "kubectl")


class ManifestTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.overlays = {}
        for name in ("production", "demo"):
            result = subprocess.run([KUBECTL, "kustomize", str(ROOT / name)], check=True, capture_output=True, text=True, env=dict(os.environ, KUBECONFIG=os.devnull))
            cls.overlays[name] = list(yaml.safe_load_all(result.stdout))

    def test_both_overlays_render_and_resolve_dependencies(self):
        for name, items in self.overlays.items():
            with self.subTest(overlay=name):
                validate(items, overlay=name)

    def test_invalid_merged_endpoint_is_rejected(self):
        items = copy.deepcopy(self.overlays["production"])
        account = next(item for item in items if item["kind"] == "Deployment" and item["metadata"]["name"] == "account-service")
        database = next(env for env in account["spec"]["template"]["spec"]["containers"][0]["env"] if env["name"] == "DB_URL")
        database["value"] = "jdbc:postgresql://stale:5432/account_db"
        with self.assertRaisesRegex(AssertionError, "both value and valueFrom"):
            validate(items)

    def test_short_image_names_are_rejected(self):
        items = copy.deepcopy(self.overlays["demo"])
        dependency = next(item for item in items if item["kind"] == "StatefulSet" and item["metadata"]["name"] == "kafka")
        dependency["spec"]["template"]["spec"]["containers"][0]["image"] = "apache/kafka:3.9.1"
        with self.assertRaisesRegex(AssertionError, "explicit registry"):
            validate(items, overlay="demo")

    def test_kafka_readiness_cannot_hide_controller_dns(self):
        items = copy.deepcopy(self.overlays["demo"])
        kafka = next(item for item in items if item["kind"] == "Service" and item["metadata"]["name"] == "kafka")
        kafka["spec"]["publishNotReadyAddresses"] = False
        with self.assertRaisesRegex(AssertionError, "controller DNS must publish"):
            validate(items, overlay="demo")

    def test_kafka_startup_is_not_interrupted_by_liveness(self):
        items = copy.deepcopy(self.overlays["demo"])
        kafka = next(item for item in items if item["kind"] == "StatefulSet" and item["metadata"]["name"] == "kafka")
        kafka["spec"]["template"]["spec"]["containers"][0].pop("startupProbe")
        with self.assertRaisesRegex(AssertionError, "broker startup grace period"):
            validate(items, overlay="demo")

    def test_missing_shared_config_is_rejected(self):
        items = [item for item in self.overlays["production"] if item["kind"] != "ConfigMap"]
        with self.assertRaisesRegex(AssertionError, "Missing ConfigMap"):
            validate(items)

    def test_broken_service_target_port_is_rejected(self):
        items = copy.deepcopy(self.overlays["production"])
        service = next(item for item in items if item["kind"] == "Service" and item["metadata"]["name"] == "account-service")
        service["spec"]["ports"][0]["targetPort"] = 9999
        with self.assertRaisesRegex(AssertionError, "targetPort mismatch"):
            validate(items)

    def test_stale_cors_is_rejected(self):
        items = copy.deepcopy(self.overlays["production"])
        config = next(item for item in items if item["kind"] == "ConfigMap")
        config["data"]["CORS_ALLOWED_ORIGINS"] = "https://stale.example.com"
        with self.assertRaisesRegex(AssertionError, "CORS origins differ"):
            validate(items)

    def test_portable_render_and_config_rollout_for_both_overlays(self):
        for name in ("production", "demo"):
            with self.subTest(overlay=name):
                settings = json.loads((ROOT / name / "settings.example.json").read_text())
                settings.update(frontend_host="app.codegen.test", www_host="www.codegen.test", api_host="api.codegen.test", ingress_controller_namespace="custom-ingress", image_pull_secret="registry-pull")
                settings["images"] = {service: f"registry.test/codegen-{service}@sha256:{'1' * 64}" for service in settings["images"]}
                output = list(yaml.safe_load_all(render(settings, KUBECTL, name)))
                validate(output, require_pinned_images=True, overlay=name)
                original = next(item["metadata"]["name"] for item in self.overlays[name] if item["kind"] == "ConfigMap" and item["metadata"]["name"].startswith("codegen-shared-config"))
                configured = next(item["metadata"]["name"] for item in output if item["kind"] == "ConfigMap" and item["metadata"]["name"].startswith("codegen-shared-config"))
                self.assertNotEqual(original, configured)
                for deployment in (item for item in output if item["kind"] == "Deployment"):
                    self.assertEqual(deployment["spec"]["template"]["spec"]["imagePullSecrets"], [{"name": "registry-pull"}])
                policy = next(item for item in output if item["kind"] == "NetworkPolicy" and item["metadata"]["name"] == "allow-nginx-ingress")
                self.assertEqual(policy["spec"]["ingress"][0]["from"][0]["namespaceSelector"]["matchLabels"]["kubernetes.io/metadata.name"], "custom-ingress")

    def test_placeholder_settings_cannot_be_rendered_for_deployment(self):
        settings = json.loads((ROOT / "production/settings.example.json").read_text())
        with self.assertRaisesRegex(ValueError, "placeholder"):
            render(settings, KUBECTL)


if __name__ == "__main__":
    unittest.main()
