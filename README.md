# CodeGen — AI-powered project builder

Build and edit React applications with AI, preview them in the browser, share
projects with collaborators, and publish saved versions through public links.
This repository includes the Java 21 microservices, React frontend, Docker
Compose setup, Kubernetes manifests, and Basic OKE infrastructure templates.

## Features

- Signup and login, private projects, and AI generation with streamed updates.
- A code editor with save support and an isolated React/TypeScript preview,
  including common UI packages, Tailwind, daisyUI and UUID identifiers.
- Owner-controlled sharing with editor and viewer permissions.
- Public snapshots with republish and unpublish controls.
- Plan and daily AI usage display; paid upgrades remain disabled.

See [the feature guide](deployment/FEATURES.md) for supported preview packages,
permissions and publishing behavior. A deployed AI provider is required for
generation; model usage is separate from application hosting.

## Architecture

Take the [UI screenshot tour](docs/screenshots/README.md) to see the dashboard,
editor, preview, sharing, publishing and usage screens with safe demo data.

![CodeGen workspace and browser preview with fictional demo data](docs/screenshots/images/06-workspace-preview.jpg)

The frontend proxies same-origin requests through the API gateway to the
account, workspace and intelligence services. PostgreSQL stores application
records, Kafka carries file-delivery messages, and MinIO stores generated file
contents. Redis supports the optional server-preview routing components.
Config Server and Eureka are retained in the source but are not required by
the Compose or demo deployments.

Explore the [illustrated architecture guide](docs/architecture/README.md) for
all 26 diagrams, the complete project flow, and the editable
[draw.io source](docs/architecture/diagrams/CodeGen-Architecture-and-Flows.drawio).
The [High Level Design](docs/architecture/HIGH-LEVEL-DESIGN.md) explains the architecture
and tradeoffs; the [Kubernetes inventory](docs/architecture/kubernetes-components.md)
covers both application objects and cluster platform components.

For local startup and public HTTPS setup, follow
[deployment/README.md](deployment/README.md). No publicly reachable demo
endpoint is included in this repository.

## Communication repairs

Shared security auto-configuration is now loaded from Spring Boot's canonical
resource location. Each service scans its own application package, and Feign
calls carry both the internal service key and the authenticated user's JWT.
Asynchronous AI file reads pass that user identity explicitly across thread
boundaries. Kafka file request/reply messages use JSON serializers and save
their tracking records before publishing. Database-backed lazy reads stay
inside transactions, and the Boot 4 Flyway starter initializes fresh schemas.

The frontend uses the gateway through a same-origin proxy in development and
in its nginx container. Generated file changes update the file tree while
streaming, including repeated edits to a file.

For deployment and the distinction between verified behavior and outstanding
live checks, read [deployment/README.md](deployment/README.md).

## Build

Use JDK 21 and run the reactor build from this directory:

```bash
./common-lib/mvnw --batch-mode --no-transfer-progress -f pom.xml clean verify
```

CI runs the same clean build on JDK 21. The manual image-publication workflow
accepts a reviewed release tag; deploy its resulting immutable image digests.

Build and test the frontend with Node.js 20:

```bash
cd frontend
npm ci
npm run lint
npm test
npm run build
```

## Runtime configuration

The services are self-contained and no longer require Config Server or Eureka
in the production topology. Supply runtime values through a secret manager or
Kubernetes Secrets; never commit real values. Required settings include:

- `JWT_SECRET` and a separate `INTERNAL_SERVICE_KEY`
- database URLs, usernames and passwords
- Kafka, Redis and S3-compatible object-storage endpoints and credentials
- `AI_API_KEY`, plus optional `AI_MODEL` and `AI_MAX_COMPLETION_TOKENS`
- `AI_BASE_URL` and `AI_CHAT_COMPLETIONS_PATH` for a compatible provider;
  see [OCI GenAI configuration](deployment/OCI-TRIAL.md)
- Stripe keys and webhook secret when billing is enabled
- `APP_FRONTEND_URL` and `CORS_ALLOWED_ORIGINS` using HTTPS origins

The frontend uses same-origin `/account`, `/workspace`, and `/intelligence`
paths in production; its nginx container proxies those paths to the API gateway.

Start from `k8s/infra/app-secrets.example.yaml` and
`k8s/production/runtime-endpoints.example.yaml`, storing the real resources
outside Git.

## Production deployment

Read `k8s/production/README.md`. The production overlay deploys replicated,
probed stateless services behind TLS and expects managed PostgreSQL, Kafka,
Redis and object storage with backups. Server-side preview execution is
disabled by default and must remain disabled until projects run in dedicated
sandboxes. The included browser preview does not execute code on the server.

Validate against the destination cluster before applying:

```bash
kubectl kustomize k8s/production > /tmp/codegen-production.yaml
kubectl apply --server-side --dry-run=server -f /tmp/codegen-production.yaml
```

For a single-node OKE demonstration with bundled stateful dependencies, use
`k8s/demo` and its deployment instructions. A new OKE cluster plan is under
`deployment/oci-oke`. Creating worker nodes, volumes, public ingress, or making
AI API calls can incur costs; this package does not establish free eligibility.
