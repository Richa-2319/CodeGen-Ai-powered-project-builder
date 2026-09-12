# Self-contained OKE demonstration

This overlay runs PostgreSQL, Kafka, Redis and MinIO inside `codegen-core`, plus
one replica of each of the five application services. It preserves the same
service DNS names and port mappings as production. It requires only
`app-secrets`, not the production `runtime-endpoints` Secret. Database creation
and ownership are initialized on the first empty PostgreSQL volume; Flyway then
creates each service's tables. The idempotent `minio-init` Job waits for MinIO
and creates the `projects` bucket before generated files can be written. Existing populated volumes are never reset by
this configuration.

The `pgvector` Service name is retained for compatibility, but its image is
PostgreSQL 16 because the current migrations contain no vector extension or
vector columns. Kafka uses the same Apache 3.9.1 image as the local Compose
setup. All five public dependency image manifests (including bucket initialization)
were verified to include `linux/arm64` during the historical deployment check.
Recheck current image availability and pull access in your target; see
[validation scope](../../deployment/VALIDATION.md). Kafka publishes its
headless address before readiness so broker startup can resolve its own name.

## Storage and capacity

PostgreSQL, Kafka and MinIO each request a 50Gi persistent volume. Redis uses a
256Mi `emptyDir` with AOF/snapshots disabled in this demo only. Source inspection
shows Redis stores six-hour server-preview route mappings; that execution runner
remains disabled, so restarting Redis cannot lose project, account or chat records.
The browser preview and published snapshots do not use Redis route mappings.
Do not enable server execution without reviewing persistence and runner isolation.

Oracle's [OKE block-volume documentation](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contengcreatingpersistentvolumeclaim_topic-Provisioning_PVCs_on_BV.htm)
documents `oci-bv` CSI provisioning and a 50Gi minimum per claim. Confirm the
actual storage class in the chosen cluster and set `storage_class` accordingly.
The demo requests 150Gi durable application storage. A 50Gi worker boot volume
would bring that allocation to 200Gi before backups, extra workers or other
existing volumes. This is an allocation calculation, not confirmation of free
capacity or a zero-cost deployment. Applying the manifests can provision billed
volumes. No infrastructure is created by the offline rendering commands.

Application and dependency requests total 1.05 CPU and 2112Mi memory; limits
total 3 CPU and 4224Mi memory. Add worker/OS, Kubernetes, DNS, ingress and
cert-manager overhead, and allow temporary capacity for rolling updates. The bucket-init Job briefly adds 25m CPU/64Mi requested and 100m CPU/128Mi limited
capacity. There is no application high availability: an application/node restart interrupts
service, and dependency recovery requires persistent-volume attachment. Demo
PodDisruptionBudgets allow voluntary disruption of the single replicas.

## Alternative managed HTTPS deployment

OCI API Gateway can be configured instead of the generic Ingress objects in
this overlay. Keep the exact applied manifests, image pins and origin settings
outside Git for each installation. Do not apply this generic overlay unchanged
over an environment with custom public/private Services or gateway routes;
review its ingress and authentication settings first. See
[the deployment guide](../../deployment/OCI-BASIC-OKE.md).

## Prepare a new deployment

1. Start with the prerequisites and offline tool setup in
   `../production/README.md`. Use confirmed OKE context, registry/architecture,
   ingress, DNS and certificate settings. Copy `settings.example.json` outside
   Git and fill every value, including real application image digests and the
   confirmed storage class. No load balancer, cluster or worker is defined here.
2. Provision `codegen-core/app-secrets` from the example key names. Include
   `POSTGRES_PASSWORD`, all three application DB passwords, Redis and MinIO
   credentials, shared JWT/internal keys, and the intended AI/Stripe keys. GIT
   keys are unnecessary. Never apply example placeholder secrets. The demo DB
   usernames are `account_user`, `workspace_user`, `intelligence_user`.
3. Render offline, review the three volume requests, then validate against the
   confirmed cluster before applying:

```bash
/tmp/codegen-k8s-venv/bin/python k8s/render.py \
  /absolute/path/to/codegen-demo-settings.json /tmp/codegen-demo.yaml --overlay demo
CODEGEN_CONTEXT='the-confirmed-oke-context'
kubectl --context "$CODEGEN_CONTEXT" apply --server-side -f k8s/infra/namespaces.yaml
# Provision the real app-secrets and image-pull Secret before the application.
kubectl --context "$CODEGEN_CONTEXT" apply --server-side --dry-run=server -f /tmp/codegen-demo.yaml
kubectl --context "$CODEGEN_CONTEXT" apply --server-side -f /tmp/codegen-demo.yaml
for dependency in pgvector kafka redis minio; do
  kubectl --context "$CODEGEN_CONTEXT" -n codegen-core rollout status "statefulset/$dependency" --timeout=600s
done
kubectl --context "$CODEGEN_CONTEXT" -n codegen-core wait --for=condition=complete job/minio-init --timeout=600s
for app in account-service workspace-service intelligence-service api-gateway codegen-frontend; do
  kubectl --context "$CODEGEN_CONTEXT" -n codegen-core rollout status "deployment/$app" --timeout=600s
done
kubectl --context "$CODEGEN_CONTEXT" -n codegen-core get pvc,pods,services,endpointslices,ingress
```

The application pods may retry while dependencies initialize. Wait for both
sets of rollouts before exercising the flow checks in the production guide.
For a private check through BOAT access, forward the frontend Service from the
confirmed cluster, then test login/project/chat through its service-path proxies:

```bash
kubectl --context "$CODEGEN_CONTEXT" -n codegen-core port-forward service/codegen-frontend 18080:80
```

Keep public-host settings for later public access; external Stripe redirects
use the configured public frontend URL. Ingress will only serve traffic after
the matching controller, DNS and certificate are ready. Persistent-volume
cleanup is intentionally not automated; deleting a namespace/PVC or changing a
storage class can destroy data and requires a separate explicit decision.
