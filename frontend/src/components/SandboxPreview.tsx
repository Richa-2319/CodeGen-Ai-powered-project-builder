import { useEffect, useRef } from "react";
import type { PreviewFiles } from "@/lib/preview-files";

interface Props {
  files: PreviewFiles;
  runId: string;
  onError: (message: string) => void;
  onRunning?: () => void;
}

export function SandboxPreview({ files, runId, onError, onRunning }: Props) {
  const frame = useRef<HTMLIFrameElement>(null);
  const callbacks = useRef({ onError, onRunning });
  callbacks.current = { onError, onRunning };
  useEffect(() => {
    let delivered = false;
    const timer = window.setTimeout(() => callbacks.current.onError("Preview took too long to start. Try Run Preview again."), 45000);
    const receive = (event: MessageEvent) => {
      if (event.source !== frame.current?.contentWindow || event.origin !== "null") return;
      const data = event.data;
      if (data?.type === "codegen-preview-ready" && !delivered) {
        delivered = true;
        // A sandboxed opaque origin requires '*'; the destination is the exact
        // frame reference. Only source files are sent, never auth or user state.
        frame.current.contentWindow.postMessage({ type: "codegen-preview-run", runId, files }, "*");
      } else if (data?.runId === runId && data.type === "codegen-preview-running") {
        clearTimeout(timer); callbacks.current.onRunning?.();
      } else if (data?.runId === runId && data.type === "codegen-preview-error" && typeof data.message === "string") {
        clearTimeout(timer); callbacks.current.onError(data.message.slice(0, 2000));
      }
    };
    window.addEventListener("message", receive);
    return () => { clearTimeout(timer); window.removeEventListener("message", receive); };
  }, [files, runId]);
  return <iframe ref={frame} src="/preview/index.html" title="App preview" className="w-full h-full border-0 bg-white"
    sandbox="allow-scripts" referrerPolicy="no-referrer" />;
}
