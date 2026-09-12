import { useState, useEffect, useCallback, useMemo } from "react";
import { FileTree } from "./FileTree";
import { CodeEditor } from "./CodeEditor";
import { FileTabs } from "./FileTabs";
import { api, buildFileTree, OPEN_TABS_KEY, ACTIVE_TAB_KEY } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import type { FileNode } from "@/lib/types";

interface CodePanelProps {
  projectId: string;
  readOnly?: boolean;
  onSaved?: (path: string, content: string) => void;
  updatedFiles: Map<string, string>;
}

// Helper to find a file by path in the tree
function findFileInTree(files: FileNode[], targetPath: string): boolean {
  for (const node of files) {
    if (node.path === targetPath) return true;
    if (node.children && findFileInTree(node.children, targetPath)) return true;
  }
  return false;
}

// Storage key helpers
const getTabsKey = (projectId: string) => `${OPEN_TABS_KEY}_${projectId}`;
const getActiveTabKey = (projectId: string) => `${ACTIVE_TAB_KEY}_${projectId}`;

export function CodePanel({ projectId, updatedFiles, readOnly = true, onSaved }: CodePanelProps) {
  const [drafts, setDrafts] = useState<Map<string, string>>(new Map());
  const [saving, setSaving] = useState(false);
  const { toast } = useToast();
  const [files, setFiles] = useState<FileNode[]>([]);
  const [openTabs, setOpenTabs] = useState<string[]>([]);
  const [activeTab, setActiveTab] = useState<string | null>(null);
  const [fileContent, setFileContent] = useState<string>("");
  const [isLoadingTree, setIsLoadingTree] = useState(true);
  const [isLoadingFile, setIsLoadingFile] = useState(false);

  const visibleFiles = useMemo(() => {
    const paths: { path: string }[] = [];
    const collectPaths = (nodes: FileNode[]) => {
      for (const node of nodes) {
        if (node.type === "file") paths.push({ path: node.path });
        if (node.children) collectPaths(node.children);
      }
    };
    collectPaths(files);
    for (const path of updatedFiles.keys()) paths.push({ path });
    return buildFileTree(paths);
  }, [files, updatedFiles]);

  const save = useCallback(async () => {
    if (!activeTab || readOnly || saving || !drafts.has(activeTab)) return;
    const path = activeTab; const content = drafts.get(path)!; setSaving(true);
    try {
      await api.saveFile(projectId, path, content);
      setDrafts(current => { const next = new Map(current); if (next.get(path) === content) next.delete(path); return next; });
      setFileContent(content); onSaved?.(path, content); toast({ title: "File saved" });
    } catch (error) { toast({ title: "Save failed", description: error instanceof Error ? error.message : "Please try again", variant: "destructive" }); }
    finally { setSaving(false); }
  }, [activeTab, drafts, onSaved, projectId, readOnly, saving, toast]);
  useEffect(() => {
    const key = (event: KeyboardEvent) => { if ((event.metaKey || event.ctrlKey) && event.key === "s") { event.preventDefault(); void save(); } };
    const unload = (event: BeforeUnloadEvent) => { if (drafts.size) { event.preventDefault(); event.returnValue = ""; } };
    window.addEventListener("keydown", key); window.addEventListener("beforeunload", unload);
    return () => { window.removeEventListener("keydown", key); window.removeEventListener("beforeunload", unload); };
  }, [save, drafts.size]);

  // Load tabs from localStorage
  useEffect(() => {
    const savedTabs = localStorage.getItem(getTabsKey(projectId));
    const savedActiveTab = localStorage.getItem(getActiveTabKey(projectId));

    if (savedTabs) {
      try {
        const tabs = JSON.parse(savedTabs);
        if (Array.isArray(tabs) && tabs.length > 0) {
          setOpenTabs(tabs);
          setActiveTab(savedActiveTab || tabs[0]);
          return;
        }
      } catch (e) {
        console.error("Failed to parse saved tabs:", e);
      }
    }
  }, [projectId]);

  // Save tabs to localStorage whenever they change
  useEffect(() => {
    if (openTabs.length > 0) {
      localStorage.setItem(getTabsKey(projectId), JSON.stringify(openTabs));
    } else {
      localStorage.removeItem(getTabsKey(projectId));
    }
  }, [openTabs, projectId]);

  // Save active tab to localStorage
  useEffect(() => {
    if (activeTab) {
      localStorage.setItem(getActiveTabKey(projectId), activeTab);
    } else {
      localStorage.removeItem(getActiveTabKey(projectId));
    }
  }, [activeTab, projectId]);

  // Load file tree
  useEffect(() => {
    const loadFiles = async () => {
      setIsLoadingTree(true);
      try {
        const fileTree = await api.getFiles(projectId);
        setFiles(fileTree);

        // If no tabs are open, default to pages/Index.tsx
        setOpenTabs((currentTabs) => {
          if (currentTabs.length > 0) return currentTabs;

          const defaultPaths = ["src/pages/Index.tsx", "pages/Index.tsx"];
          for (const defaultPath of defaultPaths) {
            if (findFileInTree(fileTree, defaultPath)) {
              setActiveTab(defaultPath);
              return [defaultPath];
            }
          }
          return currentTabs;
        });
      } catch (error) {
        console.error("Failed to load files:", error);
      } finally {
        setIsLoadingTree(false);
      }
    };

    loadFiles();
  }, [projectId]);

  // Discard responses for a previously selected file instead of replacing the
  // current editor buffer after a fast tab switch.
  useEffect(() => {
    let cancelled = false;
    if (!activeTab) { setFileContent(""); setIsLoadingFile(false); return; }
    if (updatedFiles.has(activeTab)) { setFileContent(updatedFiles.get(activeTab)!); setIsLoadingFile(false); return; }
    setIsLoadingFile(true);
    api.getFileContent(projectId, activeTab).then(content => { if (!cancelled) setFileContent(content); })
      .catch(() => { if (!cancelled) setFileContent("// Could not load this file. Reopen it to retry."); })
      .finally(() => { if (!cancelled) setIsLoadingFile(false); });
    return () => { cancelled = true; };
  }, [projectId, activeTab, updatedFiles]);

  const handleSelectFile = useCallback((path: string) => {
    // Add to tabs if not already open
    if (!openTabs.includes(path)) {
      setOpenTabs((prev) => [...prev, path]);
    }
    setActiveTab(path);
  }, [openTabs]);

  const handleCloseTab = useCallback((path: string) => {
    setOpenTabs((prev) => {
      const newTabs = prev.filter((t) => t !== path);

      // If closing active tab, switch to another tab
      if (activeTab === path) {
        const closingIndex = prev.indexOf(path);
        const newActiveIndex = Math.min(closingIndex, newTabs.length - 1);
        setActiveTab(newTabs[newActiveIndex] || null);
      }

      return newTabs;
    });
  }, [activeTab]);

  const handleSelectTab = useCallback((path: string) => {
    setActiveTab(path);
  }, []);

  return (
    <div className="flex h-full">
      {/* File Tree */}
      <div className="w-56 shrink-0 border-r border-border/50 overflow-y-auto bg-panel">
        <div className="panel-header">
          <span className="text-sm font-medium">Files</span>
        </div>
        <FileTree
          files={visibleFiles}
          selectedPath={activeTab}
          onSelectFile={handleSelectFile}
          isLoading={isLoadingTree}
        />
      </div>

      {/* Code Editor with Tabs */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* File Tabs */}
        <FileTabs
          openTabs={openTabs}
          activeTab={activeTab}
          onSelectTab={handleSelectTab}
          onCloseTab={handleCloseTab}
        />

        <div className="flex items-center justify-between px-3 py-1 border-b border-border/50 text-xs text-muted-foreground">
          <span>{readOnly ? "Read-only" : activeTab && drafts.has(activeTab) ? "Unsaved changes" : "Saved"}</span>
          {!readOnly && <Button size="sm" variant="outline" className="h-7 text-xs" onClick={save} disabled={saving || !activeTab || !drafts.has(activeTab)}>{saving ? "Saving…" : "Save"}</Button>}
        </div>
        {/* Editor */}
        <div className="flex-1 overflow-hidden">
          <CodeEditor
            content={activeTab && drafts.has(activeTab) ? drafts.get(activeTab)! : fileContent}
            readOnly={readOnly || saving}
            onCodeChange={content => { if (activeTab && !readOnly) setDrafts(current => new Map(current).set(activeTab, content)); }}
            filePath={activeTab}
            isLoading={isLoadingFile}
          />
        </div>
      </div>
    </div>
  );
}
