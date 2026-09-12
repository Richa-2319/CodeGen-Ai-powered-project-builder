# Basic OKE demonstration deployment

This repository supports a single-node OKE demonstration and a separate
production overlay with managed dependencies. Keep cloud identifiers,
credentials, applied manifests and Terraform state in a protected operator
directory outside Git.

## Architecture

The [infrastructure template](oci-oke/README.md) selects a Basic OKE cluster
and one private ARM64 A1 worker with 2 OCPUs, 12 GB RAM and a 50 GB boot volume.
The [demo overlay](../k8s/demo/README.md) runs four Java services, the React
frontend, PostgreSQL, Kafka, MinIO and Redis. PostgreSQL, Kafka and MinIO each
request a 50Gi volume. Redis is ephemeral.

Frontend nginx proxies application paths to the API gateway. User requests
require JWT authentication and internal service routes are restricted. Browser
previews and published snapshots render inside isolated frames; server-side
execution of generated projects remains disabled.

## Deployment sequence

1. Select the tenancy, region, compartment and authenticated OCI profile.
   Verify permissions, service access, physical capacity and the full estimate.
2. Follow the infrastructure guide to validate and review a fresh Terraform
   plan. Keep target settings and state outside Git.
3. Build reviewed images for the worker architecture, publish them to an
   authorized registry, configure pull access and retain immutable digests.
4. Follow the demo overlay guide to supply protected runtime settings, prepare
   storage, validate service DNS and deploy the application.
5. Configure public HTTPS and matching frontend/gateway origins. Test login and
   generation from an independent internet connection before sharing the URL.

The generic demo overlay expects an ingress controller and certificate manager.
OCI API Gateway with managed TLS is an alternative, but its resource, routes
and private worker backend must be configured separately. Retain each target's
exact applied manifests: applying the generic overlay over a customized
installation can change its ingress design.

## Runtime access and operations

Use a dedicated provider service key with only the required model/compartment
permissions; see [OCI GenAI configuration](OCI-TRIAL.md). Operator session
credentials do not belong in the application. Record key expiry and renewal
procedures privately.

For private OCIR images, the [registry helper](registry-auth/README.md) supports
worker-identity-based pull-Secret renewal. Scope repository access and Kubernetes
permissions to the deployment. Recheck identity rules and any IP-based gateway
backend when replacing the worker.

Verify readiness, PVC attachment, DNS, registry renewal, public HTTPS, login
and generated-file retrieval for each release. If image downloads are
restricted, use authorized private mirrors and check provider connectivity.
External reachability must be tested independently of an operator VPN.

This demonstration has one failure domain. Node restarts and upgrades can
interrupt service. Establish database/object-storage backups, test restoration,
and retain previous images. Application rollback does not reverse migrations.

A Basic control plane does not make workers, disks, networking or inference
free. Review current account-specific pricing and usage; application quotas do
not enforce a cloud spending ceiling. See [capacity planning](FREE-TIER-ASSESSMENT.md)
and [validation scope](VALIDATION.md).
