export type PreviewFiles = Record<string, string>;

export function normalizePreviewPath(path: string): string {
  const parts: string[] = [];
  for (const part of path.replace(/\\/g, "/").split("/")) {
    if (!part || part === ".") continue;
    if (part === "..") {
      if (!parts.length) throw new Error("File path escapes the project");
      parts.pop();
    } else parts.push(part);
  }
  return parts.join("/");
}

export function resolvePreviewFile(specifier: string, importer: string, files: PreviewFiles): string {
  const base = specifier.startsWith("@/") ? "src/" + specifier.slice(2)
    : specifier.startsWith("/") ? specifier.slice(1)
    : importer.split("/").slice(0, -1).concat(specifier).join("/");
  const path = normalizePreviewPath(base);
  const candidates = [path, ...[".tsx", ".ts", ".jsx", ".js", ".json", ".css"].map(ext => path + ext),
    ...[".tsx", ".ts", ".jsx", ".js"].map(ext => path + "/index" + ext)];
  const found = candidates.find(candidate => Object.prototype.hasOwnProperty.call(files, candidate));
  if (!found) throw new Error(`Cannot find ${specifier} imported by ${importer || "index.html"}`);
  return found;
}

export function previewEntry(files: PreviewFiles, htmlEntry?: string): string | undefined {
  if (htmlEntry) return resolvePreviewFile(htmlEntry.startsWith("/") ? htmlEntry : "./" + htmlEntry, "", files);
  return ["src/main.tsx", "src/main.jsx", "src/main.ts", "src/main.js", "src/index.tsx", "src/index.jsx", "src/App.tsx", "src/App.jsx", "App.tsx", "App.jsx", "main.tsx", "main.jsx", "main.ts", "main.js", "index.tsx", "index.jsx", "index.ts", "index.js"].find(path => Object.prototype.hasOwnProperty.call(files, path));
}
