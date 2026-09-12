import { useState, useEffect, useCallback } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Copy, Lock } from "lucide-react";
import { api } from "@/lib/api";
import { ProjectMember, ProjectRole } from "@/lib/types";
import { useToast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";

interface ShareDialogProps {
    projectId: string;
    canManage?: boolean;
    trigger?: React.ReactNode;
    open?: boolean;
    onOpenChange?: (open: boolean) => void;
}

export function ShareDialog({ projectId, canManage = false, trigger, open, onOpenChange }: ShareDialogProps) {
    const { toast } = useToast();
    const [members, setMembers] = useState<ProjectMember[]>([]);
    const [inviteEmail, setInviteEmail] = useState("");
    const [inviteRole, setInviteRole] = useState<ProjectRole>("EDITOR");
    const [loading, setLoading] = useState(false);
    const [loadError, setLoadError] = useState("");
    const [internalOpen, setInternalOpen] = useState(false);

    // Use controlled open state if provided, otherwise use internal state
    const isOpen = open !== undefined ? open : internalOpen;
    const handleOpenChange = (newOpen: boolean) => {
        if (onOpenChange) {
            onOpenChange(newOpen);
        } else {
            setInternalOpen(newOpen);
        }
    };

    const loadMembers = useCallback(async () => {
        setLoadError("");
        try {
            const data = await api.getProjectMembers(projectId);
            setMembers(data);
        } catch (error) {
            setLoadError("Could not load project members. Try again.");
        }
    }, [projectId]);

    useEffect(() => {
        if (isOpen) {
            void loadMembers();
        }
    }, [isOpen, loadMembers]);

    const handleInvite = async () => {
        if (!canManage || !inviteEmail.trim()) return;
        setLoading(true);
        try {
            await api.inviteMember(projectId, inviteEmail.trim(), inviteRole);
            toast({ title: "Access granted", description: "The project now appears in this user’s dashboard. Copy the project link to share it with them." });
            setInviteEmail("");
            loadMembers();
        } catch (error) {
            console.error(error);
            toast({ title: "Could not grant access", description: error instanceof Error ? error.message : "This person needs a CodeGen account first.", variant: "destructive" });
        } finally {
            setLoading(false);
        }
    };

    const handleRoleChange = async (userId: number, newRole: ProjectRole) => {
        try {
            await api.updateMemberRole(projectId, userId, newRole);
            setMembers(members.map(m => m.userId === userId ? { ...m, role: newRole } : m));
            toast({ title: "Role updated" });
        } catch (error) {
            toast({ title: "Error", description: "Failed to update role.", variant: "destructive" });
        }
    };

    const handleRemoveMember = async (userId: number) => {
        try {
            await api.removeMember(projectId, userId);
            setMembers(members.filter(m => m.userId !== userId));
            toast({ title: "Member removed" });
        } catch (error) {
            toast({ title: "Error", description: "Failed to remove member.", variant: "destructive" });
        }
    };

    return (
        <Dialog open={isOpen} onOpenChange={handleOpenChange}>
            {trigger && <DialogTrigger asChild>{trigger}</DialogTrigger>}
            <DialogContent className="sm:max-w-md gap-0 p-0 overflow-hidden border-none shadow-2xl">
                <div className="min-w-0 p-6 pb-4">
                    <DialogHeader className="mb-4">
                        <DialogTitle className="text-xl">Share project</DialogTitle>
                    </DialogHeader>

                    <p className="text-sm text-muted-foreground mb-3"><Lock className="inline h-4 w-4 mr-1" />Only listed members can open this project. Add someone using their existing CodeGen account email.</p>
                    <div className="flex gap-2 mb-5"><Input className="min-w-0 flex-1" readOnly value={`${window.location.origin}/projects/${projectId}`} aria-label="Private project link" onFocus={event => event.target.select()} /><Button variant="outline" aria-label="Copy project link" onClick={async () => { try { await navigator.clipboard.writeText(`${window.location.origin}/projects/${projectId}`); toast({ title: "Project link copied" }); } catch { toast({ title: "Select and copy the project link" }); } }}><Copy className="h-4 w-4" /></Button></div>
                    {/* Existing-account access; no email is sent. */}
                    {canManage && <div className="space-y-3 mb-6">
                        <div className="flex gap-2">
                            <Input
                                placeholder="Email or username"
                                className="min-w-0 flex-1 bg-muted/50 border-input/50"
                                value={inviteEmail}
                                onChange={(e) => setInviteEmail(e.target.value)}
                                onKeyDown={(e) => e.key === "Enter" && handleInvite()}
                            />
                            <Button
                                onClick={handleInvite}
                                disabled={!inviteEmail.trim() || loading}
                                className="px-6"
                            >
                                Add
                            </Button>
                        </div>
                        <Select value={inviteRole} onValueChange={(val) => setInviteRole(val as ProjectRole)}>
                            <SelectTrigger className="w-full bg-muted/50 border-input/50">
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="VIEWER">Can view</SelectItem>
                                <SelectItem value="EDITOR">Can edit</SelectItem>
                            </SelectContent>
                        </Select>
                    </div>}

                    {/* Members List */}
                    <div className="space-y-4">
                        <h4 className="text-sm font-medium text-muted-foreground">People with access</h4>

                        <div className="space-y-2 max-h-[300px] overflow-y-auto pr-1">
                            {loadError && <p role="alert" className="text-sm text-destructive">{loadError}</p>}
                            {members.map(member => (
                                <div key={member.userId} className="flex items-center gap-3 p-2 rounded-lg hover:bg-muted/50 transition-colors">
                                    <Avatar className="h-9 w-9">
                                        <AvatarFallback className="text-xs font-medium">
                                            {member.name ? member.name.charAt(0).toUpperCase() : (member.username || 'U').slice(0, 2).toUpperCase()}
                                        </AvatarFallback>
                                    </Avatar>
                                    <div className="flex-1 min-w-0 text-sm">
                                        <div className="font-medium truncate">{member.name || member.username}</div>
                                        <div className="text-xs text-muted-foreground truncate">{member.username}</div>
                                    </div>

                                    {member.role === 'OWNER' || !canManage ? (
                                        <span className="text-xs text-muted-foreground px-2 whitespace-nowrap">{member.role}</span>
                                    ) : (
                                        <Select
                                            value={member.role}
                                            onValueChange={(val) => {
                                                if (val === 'REMOVE') handleRemoveMember(member.userId);
                                                else handleRoleChange(member.userId, val as ProjectRole);
                                            }}
                                        >
                                            <SelectTrigger className="h-8 w-[100px] text-xs border-none bg-transparent hover:bg-muted focus:ring-1 shadow-none">
                                                <SelectValue />
                                            </SelectTrigger>
                                            <SelectContent align="end">
                                                <SelectItem value="EDITOR">Can edit</SelectItem>
                                                <SelectItem value="VIEWER">Can view</SelectItem>
                                                <SelectItem value="REMOVE" className="text-destructive focus:text-destructive">Remove</SelectItem>
                                            </SelectContent>
                                        </Select>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            </DialogContent>
        </Dialog>
    );
}
