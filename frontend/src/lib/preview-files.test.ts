import { describe, expect, it } from "vitest";
import { normalizePreviewPath, previewEntry, resolvePreviewFile } from "./preview-files";

describe("preview file resolution", () => {
  const files = { "src/main.tsx": "", "src/App.tsx": "", "src/components/Button/index.tsx": "", "src/data.json": "" };
  it("resolves the Vite entry, relative imports, aliases and directory imports", () => {
    expect(previewEntry(files, "/src/main.tsx")).toBe("src/main.tsx");
    expect(resolvePreviewFile("./App", "src/main.tsx", files)).toBe("src/App.tsx");
    expect(resolvePreviewFile("@/components/Button", "src/App.tsx", files)).toBe("src/components/Button/index.tsx");
    expect(resolvePreviewFile("../data.json", "src/components/Test.tsx", files)).toBe("src/data.json");
  });
  it("rejects missing dependencies and paths outside the project", () => {
    expect(() => normalizePreviewPath("../../etc/passwd")).toThrow("escapes");
    expect(() => resolvePreviewFile("./Missing", "src/App.tsx", files)).toThrow("Cannot find");
  });
});
