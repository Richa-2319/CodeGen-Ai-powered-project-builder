# Production deployment

This overlay runs the five application Deployments with two replicas each and
uses separately provisioned PostgreSQL, Kafka, Redis and S3-compatible storage.
The application configuration comes from environment variables; Config Server
and Eureka are not required by these applications. Keep previews disabled.

## Required target values

Confirm the OKE cluster/context, region, compartment, worker architecture,
registry access, DNS records, ingress controller namespace/class, and TLS issuer.
An NGINX-compatible ingress controller and cert-manager with the selected
ClusterIssuer must already exist. DNS must resolve the three public hosts to the
controller. The renderer only prepares manifests; it does not install these
components, create cloud resources, or connect to Kubernetes.

1. Copy `k8s/production/settings.example.json` outside Git and replace all three
   hosts and the five application images with published `repository@sha256:...`
   references. Configure the ingress namespace/class, TLS issuer/Secret name and,
   if required, an existing image-pull Secret in `codegen-core`. The frontend
   image uses the same-origin `/account`, `/workspace` and `/intelligence` proxies to the gateway.
2. In `codegen-core`, provision `app-secrets` using the key names in
   `k8s/infra/app-secrets.example.yaml`. All services must receive the same
   `JWT_SECRET` and `INTERNAL_SERVICE_KEY`. Provide real AI credentials and Stripe
   test or live credentials matching the intended environment. The `GIT_*` and
   `CONFIG_GIT_URI` keys are only for optional Config Server. `POSTGRES_PASSWORD`
   is only used by the demo's PostgreSQL instance.
3. Provision `runtime-endpoints` using
   `k8s/production/runtime-endpoints.example.yaml`, including all three database
   usernames and JDBC URLs. Create the databases and grant each application user
   database ownership/schema rights needed by Flyway; back up existing databases
   before the release. Configure the Redis URL/password and S3 access key/secret
   for those exact services. Object storage must support the MinIO client's S3
   API and allow the project bucket operations.
4. Configure provider-required Kafka SASL/TLS settings before starting services.
   The optional `runtime-extra-config` Secret is imported by account, workspace
   and intelligence. Its `SPRING_APPLICATION_JSON` key can supply Spring Kafka
   security properties without putting credentials in a manifest or command
   argument. Install any required trust-store mounts with a reviewed provider
   patch. The basic endpoint example alone does not configure secure Kafka.
5. Provision encrypted durable dependencies with backups and tested restores.
   Allow pod egress to their exact endpoints and required external AI/Stripe
   APIs, plus DNS. The included policies permit intra-namespace traffic and
   ingress-controller access; they do not configure cloud subnet/NSG rules.

## Offline preparation

Run from the repository root with Python 3 and kubectl installed:

```bash
python3 -m venv /tmp/codegen-k8s-venv
/tmp/codegen-k8s-venv/bin/pip install -r k8s/requirements.txt
/tmp/codegen-k8s-venv/bin/python k8s/render.py \
  /absolute/path/to/codegen-settings.json /tmp/codegen-production.yaml
/tmp/codegen-k8s-venv/bin/python k8s/test_manifests.py
```

The renderer derives ingress, TLS, frontend return URLs and gateway CORS from
one settings file, then validates selectors, service ports and Secret/ConfigMap
references. Configuration changes produce a new ConfigMap hash and roll the
consuming pods. Placeholder hosts and image tags are rejected. No secret values
are included in the generated manifest. Direct `kubectl kustomize k8s/production`
is useful for CI syntax/wiring checks but retains example hosts and image tags.

## Apply only to the confirmed target

After reviewing the render and any database migrations, use an explicit context
for every cluster operation. Replace the sample path values; never rely on the
current context.

```bash
CODEGEN_CONTEXT='the-confirmed-oke-context'
kubectl --context "$CODEGEN_CONTEXT" apply --server-side -f k8s/infra/namespaces.yaml
# Provision app-secrets, runtime-endpoints and any image-pull/extra-config Secrets
# through your approved secret-management process before proceeding.
kubectl --context "$CODEGEN_CONTEXT" apply --server-side --dry-run=server -f /tmp/codegen-production.yaml
kubectl --context "$CODEGEN_CONTEXT" apply --server-side -f /tmp/codegen-production.yaml
for app in account-service workspace-service intelligence-service api-gateway codegen-frontend; do
  kubectl --context "$CODEGEN_CONTEXT" -n codegen-core rollout status "deployment/$app" --timeout=600s
done
kubectl --context "$CODEGEN_CONTEXT" -n codegen-core get pods,services,endpointslices,ingress
```

Check the frontend, signup/login, project creation/read, streaming chat and
persisted generated files against the exact public hosts. Real AI/Stripe calls
require the intended credentials and may incur charges. Preview deployment
remains disabled until isolated runner infrastructure is configured.

Secret changes do not automatically restart pods. After an intentional secret
change, restart the affected Deployments explicitly and repeat rollout and
application checks. Rollback uses the previously reviewed image digests and
settings render. Database migrations require a separately reviewed recovery
plan; rolling back images does not roll back a database.

The three obsolete per-service GKE auto-deploy workflows were replaced by one
manual, explicit-target image publication workflow. It verifies code and builds
all five images for the selected architecture(s), records digests, and never
changes a cloud cluster. Local rendering was tested with kubectl 1.34.1 /
Kustomize 5.7.1; no target server validation is implied by that test.
