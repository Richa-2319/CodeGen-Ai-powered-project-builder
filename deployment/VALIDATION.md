# Repair and validation status

## Repository publication checks

Checked on 12 September 2026 before the initial GitHub publication:

- All 433 release source files are included. Runtime credentials, cloud state,
  local dependencies and generated build artifacts are excluded.
- JDK 21 compiled the full Maven reactor. The initial `clean verify` passed
  every module except intelligence, whose mock HTTP server could not bind in
  the local sandbox. A focused intelligence/common-library `verify` with
  loopback access passed. The resulting reports contain 43 passing Java tests
  with no failures, errors or skipped tests.
- Frontend lint passed with seven existing Fast Refresh warnings. All 17 tests
  passed; the TypeScript/production build passed with a bundle-size warning.
  The proxy smoke passed with local loopback access enabled.
- All 10 Kubernetes manifest regression tests passed without a cluster.
- The staged whitespace check, source allowlist, secret-pattern scan and local
  Markdown link checks passed.

The publication changes only documentation, ignore rules and whitespace from
the verified release source. These local checks do not deploy the application
or verify current cloud health. GitHub Actions results are separate evidence.

## Historical deployment scope

The historical runtime results below were collected on 12 September 2026
(IST) in a restricted Basic OKE demonstration environment. They describe that
specific test run, not current health or the readiness of a new deployment.
Cloud identifiers, exact endpoints and operator evidence are kept outside Git.

The demonstrated stack used one ARM64 worker, nine application/dependency pods
and three 50Gi PVCs. Application behavior passed through the operator network;
independent external probes could not establish a connection. No publicly
reachable hosted endpoint is provided by this repository.

## Live verification

The application results below were collected from the operator's Oracle VPN network and in-app browser. They do not establish internet reachability.

| Check | Observed result |
| --- | --- |
| HTTPS UI, signup page and health endpoint | HTTP 200 with certificate verification enabled; browser rendered login and signup forms |
| Old HTTP signup address | Redirected to the managed HTTPS signup page |
| Signup/login with browser Origin | Passed through the configured HTTPS URL |
| JWT and internal-route protection | Unauthenticated project requests rejected; internal service routes unavailable externally |
| Workspace to account Feign request | Project creation and account-plan lookup passed |
| Project create/list/detail/delete; file tree; chat history | Passed through the configured HTTPS endpoint |
| Fresh PostgreSQL initialization and Flyway | Services started against new persistent PostgreSQL databases; account/project/chat records persisted |
| OCI GenAI application bearer key and SSE | Passed through the configured HTTPS endpoint using `openai.gpt-oss-120b` in Chicago; 229 SSE chunks in the final feature-release test |
| AI generated file to Kafka to MinIO | README.md generated, delivered, retrieved by query-string path and content verified; chat history persisted |
| Registry credential renewal | Scheduled refresh Job succeeded independently of the operator BOAT session |
| Single ARM64 worker and storage | One Ready A1 worker, 2 OCPU/12 GB, private VNIC, IMDSv2; three Bound application PVCs |
| Browser preview | React/TypeScript app rendered through the configured UI and created valid UUID task IDs on button clicks |
| Code editor Save | Saved changed source, verified persisted content and refreshed the private preview |
| Publish and republish | Anonymous public app rendered; edits stayed private until republish; public link remained stable |
| Unpublish and delete | Both revoked future anonymous access; deleted-project private preview was denied |
| Sharing | Real member identity, editor/viewer permissions, owner protection, removal and private link copy passed |
| Viewer browser UI | Read-only editor; Save and Publish controls unavailable |
| Plan and usage dialog | Displayed plan, allowance, project count and usage; paid upgrades disabled |
| Daily AI usage recording | Fresh synthetic account increased from zero to 175 estimated tokens after the real AI request; generated file and chat history persisted |

Synthetic test credentials existed only in memory. Smoke projects were removed; synthetic accounts remain.

Independent external probes resolved the demonstration hostname but timed out
before TLS or HTTP. Passing tests from the operator network therefore did not
establish public access. A deployment in a suitable public hosting environment
and an independent external retest are required before sharing a working URL.

## Communication and startup fixes

- Corrected shared Spring Boot auto-configuration registration, component scanning and duplicate security-filter registration.
- Propagated bearer authentication and the internal service key through Feign and asynchronous AI tool calls.
- Updated Kafka JSON serializers for Spring Kafka 4, committed delivery tracking before publish, waited for publication acknowledgement, and surfaced failures instead of swallowing them. Crash recovery still needs an outbox for stronger guarantees.
- Added the Spring Boot 4 Flyway starter, corrected subscription defaults, and kept lazy database access inside transactions.
- Added all three development proxy routes, repaired streamed file-tree updates, rejected invalid stream responses, and cancelled obsolete requests during navigation.
- Added Compose startup/DNS/health configuration, PostgreSQL ownership initialization, and MinIO bucket initialization.
- Repaired Kubernetes overlay rendering, endpoint/config merging, service port checks, and a small OCI demo with explicit storage allocations. Added reviewable OKE Terraform and ARM image publication configuration.
- Qualified all Kubernetes image names with their registry after the OKE runtime rejected ambiguous short names. Updated digest replacement and added a regression that rejects unqualified images.

- Fixed Kafka bootstrap DNS by publishing its headless Service address before readiness and allowing a 300-second startup window. Two regression tests cover the startup contract.

## Build and configuration checks

| Check | Result and scope |
| --- | --- |
| Earlier JDK 21 Maven reactor `clean verify` and `verify` | 32 tests, zero failures/errors/skips across seven modules |
| Latest focused workspace/gateway/common verification | 24 tests passed, including snapshot publication and member permissions |
| Latest focused intelligence/common-library verification | 26 tests passed, including missing-provider-token-count regression and configured OCI-compatible streaming |
| Frontend test/typecheck/build/lint/proxy checks | 17 tests passed; typecheck/build/proxy passed; lint passed with seven existing Fast Refresh warnings |
| Local isolated browser runtime | React/TS, relative and alias imports, UUID task identifiers, Tailwind/daisyUI, interaction and temporary storage passed; parent access and network requests blocked |
| Final application image builds | Four Java services and frontend built for ARM64, published privately and run on OKE |
| Local real HTTP smoke | Passed with separate in-memory H2 databases before OKE rollout |
| Compose configuration and PostgreSQL shell syntax | Passed with synthetic settings; no full local Compose stack was started |
| Kubernetes manifest regressions | 10 tests passed, including explicit registry references and Kafka bootstrap DNS/startup |
| Registry authentication helper | Three Go tests and `go vet` passed |
| Terraform 1.12.2 / OCI provider 8.27.0 | Format/validate and six mocked plan tests passed; fresh read-only Ashburn plan reported zero infrastructure changes |

## Remaining limits

This is a single-node demonstration with no high availability or tested backup/restore. Volume reattachment, node replacement and crash-recovery tests have not run. A transactional outbox is still needed to eliminate the database-commit/Kafka-publication crash window.

The asynchronous `read_files` tool path has source/tests but was not independently exercised by the live AI smoke. Quota/provider error handling and actual billing-record attribution were not validated. Paid upgrades remain disabled by user choice. Browser previews and publications support the documented React frontend packages; external APIs, arbitrary npm installs and server processes are unavailable. Preview browser storage resets on restart. The separate Kubernetes execution runner remains disabled because Flannel does not enforce the included NetworkPolicy objects. See [feature behavior and boundaries](FEATURES.md).

Maintain provider-key renewal and least-privilege registry access for each
installation. Recheck worker-identity rules and IP-based gateway backends when
replacing nodes. Verify DNS and required outbound provider/image access in the
selected deployment environment.
