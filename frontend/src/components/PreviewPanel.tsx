import { useState, useEffect, useRef } from "react";
import { Play, Loader2, RefreshCw, Globe } from "lucide-react";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api";
import { RuntimeErrorAlert, RuntimeError } from "@/components/RuntimeErrorAlert";
import { SandboxPreview } from "./SandboxPreview";
import type { PreviewFiles } from "@/lib/preview-files";

interface PreviewPanelProps {
  projectId: string;
  updatedFiles: Map<string, string>;
  isStreaming: boolean;
  runtimeError: RuntimeError | null;
  onDismiss: () => void;
  onFix: (error: RuntimeError) => void;
  onRuntimeError: (error: RuntimeError) => void;
}

export function PreviewPanel({ projectId, updatedFiles, isStreaming, runtimeError, onDismiss, onFix, onRuntimeError }: PreviewPanelProps) {
  const [preview, setPreview] = useState<{ files: PreviewFiles; runId: string } | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isRunning, setIsRunning] = useState(false);
  const [retry, setRetry] = useState(0);
  const [loadError, setLoadError] = useState("");
  const sequence = useRef(0);
  const dismiss = useRef(onDismiss); dismiss.current = onDismiss;
  useEffect(() => { setPreview(null); setIsRunning(false); }, [projectId]);
  useEffect(() => {
    if (isStreaming) return;
    const requestId = ++sequence.current;
    const controller = new AbortController();
    setIsLoading(true); setLoadError(""); setIsRunning(false); dismiss.current();
    const timer = setTimeout(async () => {
      try {
        const bundle = await api.getPreview(projectId, controller.signal);
        if (requestId !== sequence.current || controller.signal.aborted) return;
        const files = { ...bundle.files, ...Object.fromEntries(updatedFiles) };
        setPreview(Object.keys(files).length ? { files, runId: `${projectId}-${requestId}` } : null);
      } catch (error) {
        if (!controller.signal.aborted) setLoadError(error instanceof Error ? error.message : "Could not load preview files");
      } finally { if (!controller.signal.aborted) setIsLoading(false); }
    }, 300);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [projectId, updatedFiles, isStreaming, retry]);
  const refresh = () => { setPreview(null); setRetry(value => value + 1); };
  return (
    <div className="flex flex-col h-full bg-background relative">
      <div className="h-12 shrink-0 flex items-center gap-2 px-3 border-b border-border/50 bg-panel">
        <Button variant="ghost" size="icon" onClick={refresh} disabled={isLoading || isStreaming} aria-label="Refresh preview" className="h-7 w-7"><RefreshCw className="w-3.5 h-3.5" /></Button>
        <div className="flex-1 flex items-center h-8 px-3 rounded-md bg-muted/50 text-sm text-muted-foreground">
          <Globe className="w-3.5 h-3.5 mr-2" /><span className="truncate">{isStreaming ? "Waiting for generated files…" : isRunning ? "Live app preview" : "Preview your generated app"}</span>
        </div>
        <Button onClick={refresh} disabled={isLoading || isStreaming} size="sm" className="h-7 px-3 text-xs">
          {isLoading ? <Loader2 className="w-3 h-3 mr-1.5 animate-spin" /> : <Play className="w-3 h-3 mr-1.5" />}{isLoading ? "Loading" : "Run Preview"}
        </Button>
      </div>
      <div className="flex-1 min-h-0 bg-white">
        {preview && !loadError ? <SandboxPreview key={preview.runId} {...preview}
          onRunning={() => setIsRunning(true)} onError={message => { setIsRunning(false); onRuntimeError({ message, source: "Preview" }); }} />
          : <div className="flex h-full items-center justify-center p-8 text-sm text-slate-600">{loadError || (isLoading ? "Loading project files…" : "Generate an app to see its preview here.")}</div>}
      </div>
      <RuntimeErrorAlert error={runtimeError} onDismiss={onDismiss} onFix={onFix} />
    </div>
  );
}
