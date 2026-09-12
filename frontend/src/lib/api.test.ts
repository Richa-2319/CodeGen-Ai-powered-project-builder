import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "./api";

const fetchMock = vi.fn<typeof fetch>();

describe("API client", () => {
  beforeEach(() => {
    localStorage.clear();
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("uses the gateway account route without a legacy prefix", async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ token: "test-token", user: { id: 1, username: "a@b.test", name: "A" } }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await api.login({ username: "a@b.test", password: "password" });

    expect(fetchMock).toHaveBeenCalledWith(
      "/account/auth/login",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("normalizes the backend project role field", async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify([
          { userId: 2, username: "member@example.test", name: "Member", projectRole: "EDITOR" },
        ]),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );

    await expect(api.getProjectMembers("7")).resolves.toEqual([
      { userId: 2, username: "member@example.test", name: "Member", role: "EDITOR" },
    ]);
  });

  it("sends signup to the account service through the gateway", async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ user: { id: 1 } })));

    await api.signup({ username: "person@example.test", name: "Person", password: "example-password" });

    expect(fetchMock).toHaveBeenCalledWith(
      "/account/auth/signup",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ username: "person@example.test", name: "Person", password: "example-password" }),
      }),
    );
  });

  it("preserves file paths with query characters and spaces", async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ content: "export default {};" })));

    await expect(api.getFileContent("7", "src/a & b.ts")).resolves.toBe("export default {};");

    expect(fetchMock).toHaveBeenCalledWith(
      "/workspace/projects/7/files/content?path=src%2Fa+%26+b.ts",
      expect.any(Object),
    );
  });

  it("reports failed file requests even when the gateway returns plain text", async () => {
    fetchMock.mockResolvedValue(new Response("Gateway unavailable", { status: 502 }));

    await expect(api.getFileContent("7", "src/App.tsx")).rejects.toThrow("Failed to fetch file content");
  });

  it("reads fragmented SSE data and delivers successive edits to the same file", async () => {
    const edits = ['<file path="src/App.tsx">first</file>', '<file path="src/App.tsx">second ☀</file>'];
    const encoded = new TextEncoder().encode(edits.map((text) => `data:${JSON.stringify({ text })}\r\n\r\n`).join(""));
    fetchMock.mockResolvedValue(new Response(new ReadableStream({
      start(controller) {
        // Split every byte, including JSON delimiters and a multibyte UTF-8 character.
        for (const byte of encoded) controller.enqueue(Uint8Array.of(byte));
        controller.close();
      },
    }), { headers: { "Content-Type": "text/event-stream;charset=UTF-8" } }));
    const onChunk = vi.fn();
    const onFile = vi.fn();
    const onError = vi.fn();

    await new Promise<void>((resolve, reject) => {
      api.streamChat("7", "Update the app", onChunk, onFile, resolve, (error) => {
        onError(error);
        reject(error);
      });
    });

    expect(onChunk.mock.calls.map(([text]) => text).join("")).toBe(edits.join(""));
    expect(onFile.mock.calls).toEqual([["src/App.tsx", "first"], ["src/App.tsx", "second ☀"]]);
    expect(onError).not.toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledWith("/intelligence/chat/stream", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({ Accept: "text/event-stream" }),
      body: JSON.stringify({ message: "Update the app", projectId: "7" }),
    }));
  });

  it("rejects an HTML fallback instead of reporting a successful empty chat", async () => {
    fetchMock.mockResolvedValue(new Response("<html>Frontend</html>", {
      headers: { "Content-Type": "text/html" },
    }));
    const onComplete = vi.fn();
    const error = await new Promise<Error>((resolve) => {
      api.streamChat("7", "Hello", vi.fn(), vi.fn(), onComplete, resolve);
    });

    expect(error.message).toContain("API gateway connection");
    expect(onComplete).not.toHaveBeenCalled();
  });

  it("provides immediate cancellation before the server responds", () => {
    fetchMock.mockReturnValue(new Promise(() => {}));
    const cancel = api.streamChat("7", "Hello", vi.fn(), vi.fn(), vi.fn(), vi.fn());

    cancel();

    expect(fetchMock.mock.calls[0][1]?.signal?.aborted).toBe(true);
  });
});
