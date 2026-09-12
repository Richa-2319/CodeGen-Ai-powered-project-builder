import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { api, type PreviewBundle } from "@/lib/api";
import { SandboxPreview } from "@/components/SandboxPreview";

export function PublishedApp() {
  const { slug } = useParams<{ slug: string }>();
  const [bundle, setBundle] = useState<PreviewBundle | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController(); setBundle(null); setError("");
    if (!slug || !/^[a-f0-9-]{36}$/i.test(slug)) { setError("Invalid app link"); return; }
    api.getPublicApp(slug, controller.signal).then(value => { if (!controller.signal.aborted) { setBundle(value); document.title = value.name; } })
      .catch(error => { if (!controller.signal.aborted) setError(error.message); });
    return () => { controller.abort(); document.title = "CodeGen Workspace"; };
  }, [slug]);
  return <main className="h-screen w-screen bg-white">
    {error ? <div role="alert" className="flex h-full items-center justify-center p-8 text-slate-700">{error}</div>
      : bundle ? <SandboxPreview key={slug} files={bundle.files} runId={`published-${slug}`} onError={setError} />
      : <div className="flex h-full items-center justify-center text-slate-600">Loading app…</div>}
  </main>;
}
