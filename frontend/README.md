# CodeGen frontend

React, TypeScript, Vite, shadcn/ui, and Tailwind frontend for the distributed CodeGen services.

## Local development

Use Node.js 20 and npm:

```sh
cp .env.example .env.local
npm ci
npm run dev
```

Leave `VITE_API_URL` empty so the browser uses same-origin API paths. During development, Vite proxies `/account/`, `/workspace/`, and `/intelligence/` to `API_PROXY_TARGET` (default `http://localhost:8080`). Set that variable to the gateway address reachable from the machine running Vite, including when the browser is on another machine. Restart Vite after changing it.

In the production container, nginx proxies those same paths using `API_GATEWAY_UPSTREAM`. This is a runtime setting; the frontend does not need rebuilding when the internal gateway address changes. Only set `VITE_API_URL` for an intentional direct browser-to-gateway deployment with matching gateway CORS configuration.

`VITE_PREVIEW_ENABLED` defaults to `false`. Keep it disabled until preview workloads have per-project sandboxing, quotas, cleanup, and network restrictions.

## Verification

```sh
npm run lint
npm test
npm run test:proxy
npm run build
```

## Container

The multi-stage Dockerfile builds the static application and serves it with unprivileged nginx on port `8080`. Configure `API_GATEWAY_UPSTREAM` at runtime with the internal API gateway URL.
