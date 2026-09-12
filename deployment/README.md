# Running and verifying the repaired project

The primary deployment target is OKE. Start with `oci-oke/README.md` for a new
cluster and `../k8s/demo/README.md` for a small self-contained application
deployment. The managed-dependency production overlay is a separate option:
`../k8s/production/README.md`.

This repository contains reusable deployment configuration. Supply your own
cloud account, registry, HTTPS hostname and protected runtime settings.
Historical tests against a restricted demonstration environment do not establish
public availability for a new deployment. Read
[the Basic OKE deployment guide](OCI-BASIC-OKE.md) and
[verification limits](VALIDATION.md).
See [feature instructions](FEATURES.md) for Preview, Save, Share, Publish and Plan & usage.
See the [capacity assessment](FREE-TIER-ASSESSMENT.md) before choosing hosting
and [OCI GenAI configuration](OCI-TRIAL.md) when using that provider.

## Verification

Use JDK 21. Run from the repository root:

```sh
mvn --batch-mode --no-transfer-progress clean verify
cd frontend
npm ci --ignore-scripts --no-audit --no-fund
npm run typecheck
npm run lint
npm test
npm run build
npm run test:proxy
```

`python3 deployment/local_http_smoke.py --java-home /path/to/jdk21` starts the
four application JARs on temporary loopback ports with separate in-memory H2
databases. It exercises real HTTP requests through the gateway, including
browser Origin headers, signup/login, JWT propagation, workspace/account plan
lookup, project and file metadata, and intelligence history. It generates
synthetic credentials only in process memory and stops only its own child
processes. Build the JARs first. H2 must be in the Maven dependency cache (the
test build downloads it).

This local HTTP check does not validate PostgreSQL migrations, Kafka delivery,
Redis, MinIO object writes, external AI generation, billing, or generated-app
previews. Run those checks against the deployed stack before calling the full
application operational.

After deployment, the following command checks gateway protection without
creating data:

```sh
python3 deployment/http_smoke.py https://YOUR_CONFIRMED_HOST
```

Adding `--create-test-data` exercises signup/login, creates a synthetic account
and project, and deletes the project afterward. The synthetic account remains;
use this option in the intended test deployment only. No credentials or tokens
are printed. Full AI validation additionally requires a configured provider
key and should confirm streamed output, persisted file contents, and the Kafka
acknowledgement; provider calls may be billable.

## Docker Compose alternative

`compose.yaml` runs the same four application services and frontend with
PostgreSQL, Kafka, Redis, and MinIO on one Docker host. Config Server and Eureka
are not needed: containers use Compose DNS names and explicit application
ports. The PostgreSQL initialization script creates each database with its
own owner; the MinIO initialization container creates the `projects` bucket.
Readiness dependencies sequence application startup. Data is stored in named
volumes; existing volumes are not reset by startup.

Supply the settings listed in `.env.example` through your own secret manager
or a protected environment file outside this source directory. JWT and
internal service keys must be different. Use at least 32 bytes for the JWT
key. The optional `disabled` Stripe values are for demos without billing;
they do not enable successful payment operations.

```sh
docker compose --env-file /protected/path/codegen.env config --quiet
docker compose --env-file /protected/path/codegen.env build
docker compose --env-file /protected/path/codegen.env up -d --wait --wait-timeout 600
python3 deployment/http_smoke.py http://localhost:8088 --create-test-data
```

Only the frontend is bound on `127.0.0.1:8088`. Internal application and
infrastructure ports are not published. On a confirmed server with DNS pointing
to it, set `APP_DOMAIN` and `APP_URL=https://YOUR_CONFIRMED_HOST`, and include
`-f compose.yaml -f compose.https.yaml` in the Compose commands. The HTTPS
overlay exposes Caddy on ports 80/443 and obtains certificates for the specified
hostname. It requires those inbound ports to be reachable. The production
frontend and gateway CORS settings must agree on that HTTPS origin.

To stop the stack while preserving data, use `docker compose ... stop`.
Before an upgrade, back up the database and object-storage volumes and retain
the previous application image references. Rollback of application images does
not automatically undo database migrations. Do not delete volumes as a
troubleshooting step.

## Runtime boundaries

- The single-node deployment is a demonstration, without node redundancy.
- React app previews run in an isolated browser frame. The Kubernetes execution
  runner remains disabled; arbitrary packages, servers and external APIs are unsupported.
- Tracking is saved before Kafka publication, but a process crash between the
  database commit and publish can still require reconciliation. A transactional
  outbox is needed for stronger delivery guarantees.
- Static manifests and passing unit/HTTP checks do not establish live cloud
  health. Verify rollout status, fresh PostgreSQL migrations, durable writes,
  Kafka roundtrips, external AI, and public TLS in the actual target.

## Public references

- [Spring Boot shared auto-configuration registration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html)
- [Spring Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [OCI Always Free limits](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)
- [OKE basic and enhanced clusters](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contengworkingwithenhancedclusters.htm)
- [OKE block-volume PVC provisioning](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contengcreatingpersistentvolumeclaim_topic-Provisioning_PVCs_on_BV.htm)
