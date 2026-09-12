import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { CodePanel } from "./CodePanel";
vi.mock("./CodeEditor", () => ({ CodeEditor: ({ content, onCodeChange, readOnly }: { content: string; onCodeChange: (value: string) => void; readOnly: boolean }) => <textarea aria-label="Code" value={content} readOnly={readOnly} onChange={event => onCodeChange(event.target.value)} /> }));
vi.mock("./FileTabs", () => ({ FileTabs: () => null }));
vi.mock("./FileTree", () => ({ FileTree: ({ onSelectFile }: { onSelectFile: (path: string) => void }) => <button onClick={() => onSelectFile("src/App.tsx")}>App.tsx</button> }));
describe("CodePanel saving", () => {
  beforeEach(() => {
    localStorage.clear(); vi.restoreAllMocks();
    vi.spyOn(api, "getFiles").mockResolvedValue([{ path: "src/App.tsx", name: "App.tsx", type: "file" }]);
    vi.spyOn(api, "getFileContent").mockResolvedValue("original");
  });
  it("persists edits and updates preview only after a successful save", async () => {
    const save = vi.spyOn(api, "saveFile").mockResolvedValue(); const onSaved = vi.fn();
    render(<CodePanel projectId="7" updatedFiles={new Map()} readOnly={false} onSaved={onSaved} />);
    fireEvent.click(screen.getByText("App.tsx"));
    await waitFor(() => expect(screen.getByLabelText("Code")).toHaveValue("original"));
    fireEvent.change(screen.getByLabelText("Code"), { target: { value: "edited" } });
    expect(onSaved).not.toHaveBeenCalled(); fireEvent.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(onSaved).toHaveBeenCalledWith("src/App.tsx", "edited"));
    expect(save).toHaveBeenCalledWith("7", "src/App.tsx", "edited");
  });
  it("keeps viewer code read-only without a save action", () => {
    render(<CodePanel projectId="7" updatedFiles={new Map()} readOnly />);
    expect(screen.getByLabelText("Code")).toHaveAttribute("readonly");
    expect(screen.queryByRole("button", { name: "Save" })).not.toBeInTheDocument();
  });
});
