# Hosting requirements and free-tier assessment

The current application has nine steady components. Static-site hosting can
serve the React frontend, but the Java services and durable dependencies still
require a reachable backend. Moving only the frontend does not solve a backend
network restriction.

## Application requirements

| Component | Purpose | Demo configuration |
| --- | --- | --- |
| Gateway, account, workspace, intelligence | Routing, identity, projects and AI generation | Four Java 21 applications, one replica each |
| Frontend | React UI and same-origin proxy | One nginx container |
| PostgreSQL 16 | Accounts, projects, chat and delivery tracking | Three databases, one 50Gi PVC |
| Kafka | Generated-file delivery and acknowledgements | One broker, one 50Gi PVC |
| MinIO | Generated-file contents | One server, one 50Gi PVC; bucket initialization Job |
| Redis | Optional server-preview routes | Ephemeral instance; server execution disabled |
| AI provider | Model inference | Authorized compatible endpoint and provider service key |

Config Server and Eureka are optional; the deployed services use explicit DNS
URLs. Ordinary PostgreSQL supports the migrations; the `pgvector` Service name
is retained for compatibility. Browser previews and published snapshots use
the frontend runtime without dedicated project workers. Paid upgrades remain
disabled.

## Capacity planning

The nine demo containers request a total of 1.05 CPU and 2112Mi memory, with
limits of 3 CPU and 4224Mi. These are configured allocations, not measured
minimum requirements or load-test results. Add OS, Kubernetes, DNS, ingress
and temporary startup/rollout capacity. Build images outside a small worker.

The OKE template selects one A1 worker with 2 OCPUs, 12 GB RAM and a 50 GB boot
volume. Three 50Gi claims add 150Gi application storage. Verify actual cloud
volume sizes, backups and other account usage before comparing these with a
free allowance. A single-node deployment has no high availability.

## Choosing hosting

The [Docker Compose setup](README.md#docker-compose-alternative) runs the same
application on one Docker host, with separate services and persistent named
volumes. OKE is another option when the account has the required entitlement,
capacity and budget.

Check current RAM, CPU, service-count, storage, outbound-traffic and inactivity
limits. Time-limited credits do not establish six months of free availability.
An expiring database or ephemeral filesystem is unsuitable for durable project
data without another storage design. This repository establishes no free
hosting or uptime guarantee.

For OCI, confirm [Always Free allowances](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm),
[trial terms](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier.htm),
[OKE service limits](https://docs.oracle.com/en-us/iaas/Content/General/service-limits/default.htm)
and [block-volume provisioning](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contengcreatingpersistentvolumeclaim_topic-Provisioning_PVCs_on_BV.htm)
for the selected account and region before provisioning. Available quota is not
a reservation of physical host capacity.

## AI usage

The application's Free plan sets internal usage limits; it does not grant
provider credit. Generation needs an authorized provider account with available
usage. The provider adapter supports configurable compatible endpoints,
including the exercised OCI GenAI integration. Hosting a local language model
is outside this configuration.

Keep inference costs separate from hosting and verify provider billing
attribution. See [provider setup](OCI-TRIAL.md), [feature boundaries](FEATURES.md)
and [validation scope](VALIDATION.md).
