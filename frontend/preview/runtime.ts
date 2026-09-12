import type * as TypeScript from "typescript";
import { normalizePreviewPath, previewEntry, resolvePreviewFile, type PreviewFiles } from "../src/lib/preview-files";

declare const __PREVIEW_MODULES__: Record<string, unknown>;
declare const __PREVIEW_FALLBACKS__: PreviewFiles;
const compiler = (window as unknown as { ts: typeof TypeScript }).ts;
let runId = "";
let started = false;
const report = (type: string, extra: Record<string, unknown> = {}) => parent.postMessage({ type, runId, ...extra }, "*");
const reportError = (error: unknown) => report("codegen-preview-error", {
  message: error instanceof Error ? error.message.slice(0, 2000) : String(error).slice(0, 2000),
});
window.addEventListener("error", event => reportError(event.error || event.message));
window.addEventListener("unhandledrejection", event => reportError(event.reason));

// Opaque sandbox origins cannot use browser storage. Generated UI state stays in
// this preview instance, never in the editor's storage or authentication context.
for (const key of ["localStorage", "sessionStorage"]) {
  const values = new Map<string, string>();
  Object.defineProperty(window, key, { value: {
    getItem: (name: string) => values.get(String(name)) ?? null,
    setItem: (name: string, value: string) => { values.set(String(name), String(value)); },
    removeItem: (name: string) => { values.delete(String(name)); },
    clear: () => values.clear(), key: (index: number) => [...values.keys()][index] ?? null,
    get length() { return values.size; },
  } });
}

function runProject(projectFiles: PreviewFiles) {
  const files: PreviewFiles = Object.create(null);
  for (const [path, content] of Object.entries({ ...__PREVIEW_FALLBACKS__, ...projectFiles })) {
    files[normalizePreviewPath(path)] = content;
  }
  const cache = new Map<string, { exports: unknown }>();
  const styled = new Set<string>();
  function addStyle(path: string, content: string) {
    if (styled.has(path)) return;
    styled.add(path);
    content = content.replace(/@import\s+["'](\.[^"']+)["']\s*;/g, (_match, relative) => {
      const resolved = resolvePreviewFile(relative, path, files);
      addStyle(resolved, files[resolved]); return "";
    });
    const style = document.createElement("style");
    style.type = "text/tailwindcss";
    // Tailwind and daisyUI are pinned local assets. Do not run package plugins or
    // fetch arbitrary styles from project package/config files.
    style.textContent = content.replace(/@tailwind\s+(base|components|utilities)\s*;/g, "")
      .replace(/@import\s+["']tailwindcss["']\s*;/g, "")
      .replace(/@plugin\s+["']daisyui["'](?:\s*\{[^}]*\})?\s*;?/g, "");
    document.head.append(style);
  }
  function load(path: string): unknown {
    if (cache.has(path)) return cache.get(path)!.exports;
    const source = files[path];
    if (path.endsWith(".css")) { addStyle(path, source); return {}; }
    if (path.endsWith(".json")) return JSON.parse(source);
    if (path.endsWith(".svg")) return "data:image/svg+xml;charset=utf-8," + encodeURIComponent(source);
    const module = { exports: {} as unknown }; cache.set(path, module);
    const result = compiler.transpileModule(source, {
      fileName: path, reportDiagnostics: true,
      compilerOptions: { module: compiler.ModuleKind.CommonJS, target: compiler.ScriptTarget.ES2020,
        jsx: compiler.JsxEmit.ReactJSX, esModuleInterop: true, isolatedModules: true },
      transformers: { before: [context => node => {
        const visit: TypeScript.Visitor = child => compiler.isMetaProperty(child)
          && child.keywordToken === compiler.SyntaxKind.ImportKeyword
          ? compiler.factory.createIdentifier("__previewImportMeta") : compiler.visitEachChild(child, visit, context);
        return compiler.visitNode(node, visit) as TypeScript.SourceFile;
      }] },
    });
    const diagnostic = result.diagnostics?.find(item => item.category === compiler.DiagnosticCategory.Error && item.code !== 1343);
    if (diagnostic) throw new Error(`${path}: ${compiler.flattenDiagnosticMessageText(diagnostic.messageText, " ")}`);
    const requireFile = (specifier: string) => {
      if (Object.hasOwn(__PREVIEW_MODULES__, specifier)) return __PREVIEW_MODULES__[specifier];
      if (!specifier.startsWith(".") && !specifier.startsWith("/") && !specifier.startsWith("@/")) {
        throw new Error(`Preview does not include package '${specifier}'. Use the supported React UI packages or include its browser source.`);
      }
      return load(resolvePreviewFile(specifier, path, files));
    };
    // Generated code is evaluated only in this CSP-sandboxed, opaque-origin frame.
    new Function("require", "module", "exports", "__previewImportMeta", result.outputText)(
      requireFile, module, module.exports, { env: { MODE: "development", DEV: true, PROD: false, BASE_URL: "/" }, url: "about:blank" });
    return module.exports;
  }
  const html = new DOMParser().parseFromString(files["index.html"] || '<div id="root"></div>', "text/html");
  const scripts = [...html.querySelectorAll("script")];
  const htmlEntry = scripts.find(script => script.type === "module" && script.getAttribute("src"))?.getAttribute("src") || undefined;
  for (const element of html.querySelectorAll("style")) addStyle("inline-style-" + styled.size, element.textContent || "");
  for (const link of html.querySelectorAll('link[rel="stylesheet"]')) {
    const href = link.getAttribute("href") || "";
    if (href && !/^(https?:|\/\/)/.test(href)) {
      const path = resolvePreviewFile(href.startsWith("/") ? href : "./" + href, "", files); addStyle(path, files[path]);
    }
  }
  html.querySelectorAll("script, base, meta, link, style, iframe, object, embed").forEach(node => node.remove());
  html.querySelectorAll("*").forEach(element => [...element.attributes].forEach(attribute => {
    if (/^on/i.test(attribute.name)) element.removeAttribute(attribute.name);
  }));
  document.body.innerHTML = html.body.innerHTML;
  document.body.dataset.theme = html.documentElement.dataset.theme || html.body.dataset.theme || "light";
  if (!document.getElementById("root")) {
    const root = document.createElement("div"); root.id = "root"; document.body.append(root);
  }
  const entry = previewEntry(projectFiles, htmlEntry);
  if (entry) {
    const exported = load(entry) as { default?: unknown };
    if (/(^|\/)App\.(t|j)sx$/.test(entry) && exported?.default) {
      const react = __PREVIEW_MODULES__.react as typeof import("react");
      const dom = __PREVIEW_MODULES__["react-dom/client"] as typeof import("react-dom/client");
      dom.createRoot(document.getElementById("root")!).render(react.createElement(exported.default as import("react").ComponentType));
    }
  } else if (!projectFiles["index.html"]) throw new Error("No app entry found. Generate src/main.tsx, src/App.tsx or index.html first.");
  if (!entry) scripts.filter(script => !script.src && script.textContent?.trim()).forEach((script, index) => {
    const path = `inline-${index}.js`; files[path] = script.textContent!; load(path);
  });
  requestAnimationFrame(() => requestAnimationFrame(() => report("codegen-preview-running")));
}

window.addEventListener("message", event => {
  if (started || event.source !== parent || event.origin !== location.origin || event.data?.type !== "codegen-preview-run") return;
  started = true; runId = event.data.runId;
  try { runProject(event.data.files); } catch (error) { reportError(error); }
});
report("codegen-preview-ready");
