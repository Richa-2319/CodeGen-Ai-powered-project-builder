import { useEffect, useState } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { api, type SubscriptionInfo } from "@/lib/api";

export function PlanDialog() {
  const [open, setOpen] = useState(false);
  const [subscription, setSubscription] = useState<SubscriptionInfo | null>(null);
  const [projects, setProjects] = useState(0);
  const [usage, setUsage] = useState<{ date: string; timeZone: string; tokensUsed: number } | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    if (!open) return;
    let active = true; setError(""); setSubscription(null);
    Promise.all([api.getSubscription(), api.getProjects(), api.getUsageToday()]).then(([current, accessible, daily]) => {
      if (active) { setSubscription(current); setUsage(daily); setProjects(accessible.filter(project => project.role === "OWNER").length); }
    }).catch(error => { if (active) setError(error.message); });
    return () => { active = false; };
  }, [open]);
  return <Dialog open={open} onOpenChange={setOpen}>
    <DialogTrigger asChild><Button variant="outline" size="sm" className="h-8 text-xs">Plan &amp; usage</Button></DialogTrigger>
    <DialogContent><DialogHeader><DialogTitle>Plan and usage</DialogTitle></DialogHeader>
      {error ? <p role="alert" className="text-sm text-destructive">{error}</p> : subscription ? <dl className="grid grid-cols-2 gap-3 text-sm">
        <dt className="text-muted-foreground">Current plan</dt><dd>{subscription.plan.name}</dd>
        <dt className="text-muted-foreground">Owned projects</dt><dd>{projects} / {subscription.plan.maxProjects}</dd>
        <dt className="text-muted-foreground">Daily AI allowance</dt><dd>{subscription.plan.unlimitedAi ? "Unlimited" : `${subscription.plan.maxTokensPerDay.toLocaleString()} tokens`}</dd>
        <dt className="text-muted-foreground">AI usage today</dt><dd>{(usage?.tokensUsed || 0).toLocaleString()} tokens</dd>
        <dt className="text-muted-foreground">Usage date</dt><dd>{usage?.date} ({usage?.timeZone})</dd>
        <dt className="text-muted-foreground">Status</dt><dd>{subscription.status}</dd>
      </dl> : <p className="text-sm text-muted-foreground">Loading usage…</p>}
      <p className="text-sm text-muted-foreground">Paid upgrades are disabled for this demo. The app records reported or estimated AI tokens. This is separate from the cloud provider bill.</p>
    </DialogContent>
  </Dialog>;
}
