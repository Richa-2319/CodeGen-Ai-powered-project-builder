import { describe, expect, it } from "vitest";
import { extractCompletedFileEdits, parseSseDataLine } from "./stream-parser";

describe("stream parser", () => {
  it("extracts only completed file edits", () => {
    const content = [
      '<message phase="planning">Updating files</message>',
      '<file path="src/App.tsx">export default function App() { return null; }</file>',
      '<file path="src/index.css">incomplete',
    ].join("");

    expect(extractCompletedFileEdits(content)).toEqual([
      {
        path: "src/App.tsx",
        content: "export default function App() { return null; }",
      },
    ]);
  });

  it("parses a valid SSE data line", () => {
    expect(parseSseDataLine('data: {"text":"hello\\n"}')).toBe("hello\n");
  });

  it("rejects an SSE object without text", () => {
    expect(() => parseSseDataLine('data: {"value":"hello"}')).toThrow(
      "SSE event is missing text",
    );
  });
});
