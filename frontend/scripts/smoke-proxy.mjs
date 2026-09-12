import assert from "node:assert/strict";
import { createServer as createHttpServer } from "node:http";
import { fileURLToPath } from "node:url";
import { createServer as createViteServer } from "vite";

const root = fileURLToPath(new URL("../", import.meta.url));
const upstream = createHttpServer(async (request, response) => {
  let body = "";
  for await (const chunk of request) body += chunk;
  response.setHeader("Content-Type", "application/json");
  response.end(JSON.stringify({ path: request.url, method: request.method, body }));
});
await new Promise((resolve) => upstream.listen(0, "127.0.0.1", resolve));
const originalTarget = process.env.API_PROXY_TARGET;
process.env.API_PROXY_TARGET = `http://127.0.0.1:${upstream.address().port}`;
let vite;

try {
  vite = await createViteServer({
    root,
    mode: "test",
    logLevel: "error",
    server: { host: "127.0.0.1", port: 0, open: false },
  });
  await vite.listen();
  const origin = `http://127.0.0.1:${vite.httpServer.address().port}`;

  const probes = [
    { path: "/account/auth/signup", method: "POST", body: JSON.stringify({ probe: true }) },
    { path: "/workspace/projects/7/files/content?path=src%2FApp.tsx", method: "GET" },
    { path: "/intelligence/chat/stream", method: "POST", body: JSON.stringify({ message: "probe", projectId: 7 }) },
  ];
  for (const { path, method, body } of probes) {
    const response = await fetch(`${origin}${path}`, {
      method,
      headers: { "Content-Type": "application/json" },
      body,
    });
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { path, method, body: body || "" });
  }
  console.log("Gateway proxy smoke passed: account, workspace, intelligence prefixes and request bodies preserved.");
} finally {
  await vite?.close();
  await new Promise((resolve) => upstream.close(resolve));
  if (originalTarget === undefined) delete process.env.API_PROXY_TARGET;
  else process.env.API_PROXY_TARGET = originalTarget;
}
