import { act, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { CodePanel } from "./CodePanel";

vi.mock("./CodeEditor", () => ({ CodeEditor: () => null }));
vi.mock("./FileTabs", () => ({ FileTabs: () => null }));
vi.mock("./FileTree", () => ({
  FileTree: ({ files }: { files: unknown }) => <output data-testid="file-tree">{JSON.stringify(files)}</output>,
}));

describe("CodePanel generated files", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it("merges streamed files with persisted files without requiring a reload", async () => {
    const getFiles = vi.spyOn(api, "getFiles").mockResolvedValue([
      { path: "README.md", name: "README.md", type: "file" },
    ]);
    const { rerender } = render(<CodePanel projectId="7" updatedFiles={new Map()} />);
    await waitFor(() => expect(screen.getByTestId("file-tree")).toHaveTextContent("README.md"));

    act(() => rerender(<CodePanel projectId="7" updatedFiles={new Map([["src/New.tsx", "new content"]])} />));

    expect(screen.getByTestId("file-tree")).toHaveTextContent("README.md");
    expect(screen.getByTestId("file-tree")).toHaveTextContent("src/New.tsx");
    expect(getFiles).toHaveBeenCalledTimes(1);
  });
});
