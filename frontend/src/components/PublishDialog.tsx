import { useEffect, useState } from "react";
import { Copy, ExternalLink, Loader2 } from "lucide-react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { api, type Publication } from "@/lib/api";
import { useToast } from "@/hooks/use-toast";

export function PublishDialog({ projectId, disabled }: { projectId: string; disabled: boolean }) {
  const [open, setOpen] = useState(false);
  const [publication, setPublication] = useState<Publication | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const { toast } = useToast();
  useEffect(() => {
    if (!open) return;
    let active = true; setBusy(true); setError("");
    api.getPublication(projectId).then(value => { if (active) setPublication(value); })
      .catch(error => { if (active) setError(error.message); }).finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [open, projectId]);
  const url = publication?.active && publication.slug ? `${window.location.origin}/p/${publication.slug}` : "";
  const publish = async () => {
    setBusy(true); setError("");
    try { setPublication(await api.publishProject(projectId)); toast({ title: "App published", description: "Your saved app is available at its public link." }); }
    catch (error) { setError(error instanceof Error ? error.message : "Publishing failed"); }
    finally { setBusy(false); }
  };
  const unpublish = async () => {
    setBusy(true); setError("");
    try { await api.unpublishProject(projectId); setPublication(value => value ? { ...value, active: false } : null); toast({ title: "App unpublished" }); }
    catch (error) { setError(error instanceof Error ? error.message : "Unpublishing failed"); }
    finally { setBusy(false); }
  };
  const copy = async () => {
    try { await navigator.clipboard.writeText(url); toast({ title: "Public link copied" }); }
    catch { toast({ title: "Select and copy the link below", variant: "destructive" }); }
  };
  return <Dialog open={open} onOpenChange={setOpen}>
    <DialogTrigger asChild><Button size="sm" className="h-8 text-xs" disabled={disabled}>Publish</Button></DialogTrigger>
    <DialogContent><DialogHeader><DialogTitle>Publish your app</DialogTitle></DialogHeader>
      <p className="text-sm text-muted-foreground">Publish a snapshot of your saved app. Anyone with the link can run it and access its frontend source. Private project access and chat history stay separate.</p>
      <p className="text-xs text-muted-foreground">Test Preview first. New edits go live only when you publish again. Browser-only apps are supported; external APIs and server processes are unavailable.</p>
      {url && <div className="space-y-2"><Input readOnly value={url} aria-label="Published app URL" onFocus={event => event.target.select()} />
        <div className="flex gap-2"><Button variant="outline" onClick={copy}><Copy className="mr-2 h-4 w-4" />Copy link</Button>
          <Button asChild variant="outline"><a href={url} target="_blank" rel="noopener noreferrer"><ExternalLink className="mr-2 h-4 w-4" />Open app</a></Button></div></div>}
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      <div className="flex justify-end gap-2">{url && <Button variant="outline" disabled={busy} onClick={unpublish}>Unpublish</Button>}
        <Button disabled={busy} onClick={publish}>{busy && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}{url ? "Publish latest version" : "Publish app"}</Button></div>
    </DialogContent>
  </Dialog>;
}
