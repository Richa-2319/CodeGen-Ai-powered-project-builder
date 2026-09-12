export interface CompletedFileEdit {
  path: string;
  content: string;
}

const COMPLETE_FILE_PATTERN = /<file\s+path="([^"]+)">([\s\S]*?)<\/file>/gi;

export function extractCompletedFileEdits(streamContent: string): CompletedFileEdit[] {
  const edits: CompletedFileEdit[] = [];
  let match: RegExpExecArray | null;

  COMPLETE_FILE_PATTERN.lastIndex = 0;
  while ((match = COMPLETE_FILE_PATTERN.exec(streamContent)) !== null) {
    edits.push({ path: match[1], content: match[2] });
  }

  return edits;
}

export function parseSseDataLine(line: string): string | null {
  const trimmedLine = line.trim();
  if (!trimmedLine.startsWith("data:")) {
    return null;
  }

  const serializedData = trimmedLine.slice(5).trim();
  if (!serializedData) {
    return null;
  }

  const parsed: unknown = JSON.parse(serializedData);
  if (
    typeof parsed !== "object" ||
    parsed === null ||
    !("text" in parsed) ||
    typeof parsed.text !== "string"
  ) {
    throw new Error("SSE event is missing text");
  }

  return parsed.text;
}
