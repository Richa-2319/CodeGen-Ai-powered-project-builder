# Worker registry credential renewal

This helper authenticates with OCI instance principals and patches only the
`codegen-registry-pull` image-pull Secret in `kube-system`, `codegen-build` and
`codegen-core`. Its dynamic group must match the single project worker, with
read access to the six named project repositories. The Kubernetes service
account receives `patch` on that one Secret in each namespace.

The helper uses the official OCI Go SDK, checks the expected tenancy, refuses
registry redirects, verifies TLS, and suppresses SDK diagnostics. No BOAT login
or user API key belongs in its container. The worker must reach OCI metadata,
OCI authentication, OCIR, and the Kubernetes API.

Run `go test ./...` and `go vet ./...`, then compile for the worker architecture:

```sh
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -trimpath -ldflags='-s -w' -o codegen-ocir-auth .
docker build --platform linux/arm64 -t YOUR_PRIVATE_RUNTIME_REPOSITORY:registry-auth-v1 .
```

Publish to the project runtime repository and deploy by the returned digest.
Precreate the three Secrets using worker-generated credentials captured directly
into the Kubernetes API, with server-side apply and no local credential files.
The `credential` mode is for that private bootstrap pipe only; never invoke it
through a terminal or expose its output. Use `check` for safe diagnostics.

Run `refresh` every five minutes with a two-minute deadline, one concurrent job,
read-only filesystem, no Linux capabilities, non-root UID 65532 and small
resource limits. Recreate the exact-instance dynamic-group match if replacing
the worker; a replacement instance is intentionally not granted access.

This is separate from application credentials. Failed refresh jobs must be
investigated before cached images or previously issued pull credentials expire.
