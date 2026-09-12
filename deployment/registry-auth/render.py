#!/usr/bin/env python3
"""Render the narrowly scoped registry refresh job; contains no credentials."""
import argparse
import json
import re


def render(image, tenancy):
    if not re.fullmatch(r"iad\.ocir\.io/[a-z0-9]+/codegen-demo/runtime-images@sha256:[a-f0-9]{64}", image):
        raise ValueError("Expected a digest-pinned project runtime image")
    if not re.fullmatch(r"ocid1\.tenancy\.oc1\.\.[a-z0-9]+", tenancy):
        raise ValueError("Expected an OCI tenancy OCID")
    account = "codegen-registry-refresh"
    objects = [{"apiVersion": "v1", "kind": "ServiceAccount", "metadata": {"name": account, "namespace": "codegen-build"}}]
    for namespace in ("kube-system", "codegen-build", "codegen-core"):
        metadata = {"name": account, "namespace": namespace}
        objects.append({"apiVersion": "rbac.authorization.k8s.io/v1", "kind": "Role", "metadata": metadata,
                        "rules": [{"apiGroups": [""], "resources": ["secrets"], "resourceNames": ["codegen-registry-pull"], "verbs": ["patch"]}]})
        objects.append({"apiVersion": "rbac.authorization.k8s.io/v1", "kind": "RoleBinding", "metadata": metadata,
                        "roleRef": {"apiGroup": "rbac.authorization.k8s.io", "kind": "Role", "name": account},
                        "subjects": [{"kind": "ServiceAccount", "name": account, "namespace": "codegen-build"}]})
    objects.append({"apiVersion": "batch/v1", "kind": "CronJob", "metadata": {"name": account, "namespace": "codegen-build"},
                    "spec": {"schedule": "*/5 * * * *", "concurrencyPolicy": "Forbid", "startingDeadlineSeconds": 120,
                             "successfulJobsHistoryLimit": 1, "failedJobsHistoryLimit": 2,
                             "jobTemplate": {"spec": {"backoffLimit": 1, "activeDeadlineSeconds": 150,
                                "template": {"spec": {"serviceAccountName": account, "restartPolicy": "Never",
                                    "imagePullSecrets": [{"name": "codegen-registry-pull"}],
                                    "securityContext": {"runAsNonRoot": True, "runAsUser": 65532, "runAsGroup": 65532,
                                                        "seccompProfile": {"type": "RuntimeDefault"}},
                                    "containers": [{"name": "refresh", "image": image, "imagePullPolicy": "IfNotPresent",
                                        "args": ["-mode=refresh", "-tenancy=" + tenancy],
                                        "resources": {"requests": {"cpu": "50m", "memory": "64Mi"}, "limits": {"cpu": "200m", "memory": "128Mi"}},
                                        "securityContext": {"allowPrivilegeEscalation": False, "readOnlyRootFilesystem": True, "capabilities": {"drop": ["ALL"]}}}]}}}}}})
    return {"apiVersion": "v1", "kind": "List", "items": objects}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image")
    parser.add_argument("tenancy")
    args = parser.parse_args()
    print(json.dumps(render(args.image, args.tenancy), indent=2))
