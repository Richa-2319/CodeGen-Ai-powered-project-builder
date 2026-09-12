import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react-swc";
import path from "path";
import { componentTagger } from "lovable-tagger";

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "API_PROXY_TARGET");
  const gatewayTarget = env.API_PROXY_TARGET || "http://localhost:8080";

  return {
    server: {
      host: "::",
      port: 5173,
      allowedHosts: true,
      hmr: {
        overlay: false,
      },
      proxy: {
        "^/(account|workspace|intelligence)/": {
          target: gatewayTarget,
          changeOrigin: true,
        },
      },
    },
    plugins: [react(), mode === "development" && componentTagger()].filter(Boolean),
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "./src"),
      },
    },
  };
});
