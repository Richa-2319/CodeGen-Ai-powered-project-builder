import { ChatMessage, DeployResponse, FileNode, LoginCredentials, LoginResponse, ProjectSummaryResponse, ProjectResponse, ProjectMember, ProjectRole, SignupRequest, AuthResponse } from "./types";
import { extractCompletedFileEdits, parseSseDataLine } from "./stream-parser";

const RAW_BASE = import.meta.env.VITE_API_URL || "";

const BASE_URL = RAW_BASE.replace(/\/+$/, "");
export const IS_PREVIEW_ENABLED = import.meta.env.VITE_PREVIEW_ENABLED === "true";
export const getAuthToken = () => localStorage.getItem("auth_token");

export const setAuthToken = (token: string) => localStorage.setItem("auth_token", token);

export const removeAuthToken = () => localStorage.removeItem("auth_token");

export const isAuthenticated = () => !!getAuthToken();

const getAuthHeaders = (): HeadersInit => {
  const token = getAuthToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
};

// User info storage
export const setUserInfo = (user: { id: number; username: string; name: string }) => {
  localStorage.setItem("user_info", JSON.stringify(user));
};

export const getUserInfo = (): { id: number; username: string; name: string } | null => {
  const userInfo = localStorage.getItem("user_info");
  if (!userInfo) return null;

  try {
    const parsed: unknown = JSON.parse(userInfo);
    if (
      typeof parsed === "object" &&
      parsed !== null &&
      "id" in parsed &&
      typeof parsed.id === "number" &&
      "username" in parsed &&
      typeof parsed.username === "string" &&
      "name" in parsed &&
      typeof parsed.name === "string"
    ) {
      return parsed as { id: number; username: string; name: string };
    }
  } catch {
    // Corrupt browser state should behave like a logged-out session.
  }

  removeUserInfo();
  return null;
};

export const removeUserInfo = () => localStorage.removeItem("user_info");

// LocalStorage keys
export const PREVIEW_URL_KEY = "preview_url";
export const OPEN_TABS_KEY = "open_tabs";
export const ACTIVE_TAB_KEY = "active_tab";

// API response format for files endpoint
interface FilesApiResponse {
  files: { path: string }[];
}

interface ProjectMemberApiResponse extends Omit<ProjectMember, "role"> {
  projectRole: ProjectRole;
}

// Convert flat file paths to nested tree structure
export function buildFileTree(paths: { path: string }[]): FileNode[] {
  const root: FileNode[] = [];
  const nodeMap = new Map<string, FileNode>();

  // Sort paths to ensure directories come before their children
  const sortedPaths = [...paths].sort((a, b) => a.path.localeCompare(b.path));

  for (const { path } of sortedPaths) {
    const parts = path.split("/");
    let currentPath = "";

    for (let i = 0; i < parts.length; i++) {
      const part = parts[i];
      const parentPath = currentPath;
      currentPath = currentPath ? `${currentPath}/${part}` : part;

      // Skip if node already exists
      if (nodeMap.has(currentPath)) continue;

      const isFile = i === parts.length - 1;
      const node: FileNode = {
        name: part,
        path: currentPath,
        type: isFile ? "file" : "directory",
        children: isFile ? undefined : [],
      };

      nodeMap.set(currentPath, node);

      if (parentPath) {
        const parent = nodeMap.get(parentPath);
        if (parent && parent.children) {
          parent.children.push(node);
        }
      } else {
        root.push(node);
      }
    }
  }

  // Sort each level: directories first, then alphabetically
  const sortNodes = (nodes: FileNode[]) => {
    nodes.sort((a, b) => {
      if (a.type === "directory" && b.type === "file") return -1;
      if (a.type === "file" && b.type === "directory") return 1;
      return a.name.localeCompare(b.name);
    });
    nodes.forEach((node) => {
      if (node.children) sortNodes(node.children);
    });
  };

  sortNodes(root);
  return root;
}

export interface SubscriptionInfo {
  plan: { id: number | null; name: string; maxProjects: number; maxTokensPerDay: number; unlimitedAi: boolean; price: string };
  status: string; currentPeriodEnd: string | null; tokensUsedThisCycle: number;
}
export interface PreviewBundle { name: string; files: Record<string, string>; }
export interface Publication { slug: string | null; active: boolean; publishedAt: string | null; }

async function checkedJson<T>(response: Response, fallback: string): Promise<T> {
  if (!response.ok) {
    let message = fallback;
    try { const body = await response.json(); message = body.message || body.error?.message || fallback; } catch { /* Non-JSON gateway error. */ }
    throw new Error(message);
  }
  return response.json();
}

export const api = {
  async getUsageToday(): Promise<{ date: string; timeZone: string; tokensUsed: number }> {
    return checkedJson(await fetch(`${BASE_URL}/intelligence/usage/today`, { headers: getAuthHeaders() }), "Could not load today's AI usage");
  },
  async getSubscription(): Promise<SubscriptionInfo> {
    return checkedJson(await fetch(`${BASE_URL}/account/api/me/subscription`, { headers: getAuthHeaders() }), "Could not load current plan and usage");
  },
  async saveFile(projectId: string, path: string, content: string): Promise<void> {
    await checkedJson(await fetch(`${BASE_URL}/workspace/projects/${projectId}/files/content`, {
      method: "PUT", headers: { "Content-Type": "application/json", ...getAuthHeaders() }, body: JSON.stringify({ path, content }),
    }), "Could not save file");
  },
  async getPreview(projectId: string, signal?: AbortSignal): Promise<PreviewBundle> {
    return checkedJson(await fetch(`${BASE_URL}/workspace/projects/${projectId}/preview`, { headers: getAuthHeaders(), signal }), "Could not load preview files");
  },
  async getPublication(projectId: string): Promise<Publication> {
    return checkedJson(await fetch(`${BASE_URL}/workspace/projects/${projectId}/publication`, { headers: getAuthHeaders() }), "Could not load publication");
  },
  async publishProject(projectId: string): Promise<Publication> {
    return checkedJson(await fetch(`${BASE_URL}/workspace/projects/${projectId}/publication`, { method: "POST", headers: getAuthHeaders() }), "Could not publish project");
  },
  async unpublishProject(projectId: string): Promise<void> {
    const response = await fetch(`${BASE_URL}/workspace/projects/${projectId}/publication`, { method: "DELETE", headers: getAuthHeaders() });
    if (!response.ok) throw new Error("Could not unpublish project");
  },
  async getPublicApp(slug: string, signal?: AbortSignal): Promise<PreviewBundle> {
    return checkedJson(await fetch(`${BASE_URL}/workspace/public/apps/${encodeURIComponent(slug)}`, { signal, credentials: "omit" }), "This app is unavailable or has been unpublished");
  },
  async login(credentials: LoginCredentials): Promise<LoginResponse> {
    const response = await fetch(`${BASE_URL}/account/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(credentials),
    });

    if (!response.ok) {
      const error = await response.text();
      throw new Error(error || "Login failed");
    }

    return response.json();
  },

  async signup(data: SignupRequest): Promise<AuthResponse> {
    const response = await fetch(`${BASE_URL}/account/auth/signup`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });

    if (!response.ok) {
      const error = await response.text();
      throw new Error(error || "Signup failed");
    }

    return response.json();
  },

  async getFiles(projectId: string): Promise<FileNode[]> {
    const response = await fetch(`${BASE_URL}/workspace/projects/${projectId}/files`, {
      headers: { ...getAuthHeaders() },
    });

    if (!response.ok) {
      throw new Error("Failed to fetch files");
    }

    const data: FilesApiResponse = await response.json();
    return buildFileTree(data.files);
  },

  async getFileContent(projectId: string, path: string): Promise<string> {
    const response = await fetch(
      `${BASE_URL}/workspace/projects/${projectId}/files/content?${new URLSearchParams({ path })}`,
      {
        headers: { ...getAuthHeaders() },
      }
    );

    if (!response.ok) {
      console.error(`Error fetching file: ${response.status} ${response.statusText}`);
      throw new Error("Failed to fetch file content");
    }

    const data = await response.json();
    return data.content;
  },

  async deploy(projectId: string): Promise<DeployResponse> {
    const response = await fetch(`${BASE_URL}/workspace/projects/${projectId}/deploy`, {
      method: "POST",
      headers: { ...getAuthHeaders() },
    });

    if (!response.ok) {
      throw new Error("Deployment failed");
    }

    return response.json();
  },

  async getProjects(): Promise<ProjectSummaryResponse[]> {
    const response = await fetch(`${BASE_URL}/workspace/projects`, {
      headers: { ...getAuthHeaders() },
    });

    if (!response.ok) {
      throw new Error("Failed to fetch projects");
    }

    return response.json();
  },

  async createProject(name: string): Promise<ProjectResponse> {
    const response = await fetch(`${BASE_URL}/workspace/projects`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...getAuthHeaders() },
      body: JSON.stringify({ name }),
    });

    if (!response.ok) {
      throw new Error("Failed to create project");
    }

    return response.json();
  },

  async getProject(id: string): Promise<ProjectResponse> {
    const response = await fetch(`${BASE_URL}/workspace/projects/${id}`, {
      headers: { ...getAuthHeaders() },
    });

    if (!response.ok) {
      throw new Error("Failed to fetch project");
    }

    return response.json();
  },

  async updateProject(id: string, name: string): Promise<ProjectResponse> {
    const response = await fetch(`${BASE_URL}/workspace/projects/${id}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json", ...getAuthHeaders() },
      body: JSON.stringify({ name }),
    });

    if (!response.ok) {
      throw new Error("Failed to update project");
    }

    return response.json();
  },

  async deleteProject(id: string): Promise<void> {
    const response = await fetch(`${BASE_URL}/workspace/projects/${id}`, {
      method: "DELETE",
      headers: { ...getAuthHeaders() },
    });

    if (!response.ok) {
      throw new Error("Failed to delete project");
    }
  },

  async downloadProjectZip(id: string): Promise<Blob> {
    const response = await fetch(`${BASE_URL}/workspace/projects/${id}/files/download-zip`, {
      headers: { ...getAuthHeaders() },
    });

    if (!response.ok) {
      throw new Error("Failed to download project");
    }

    return response.blob();
  },

  async getProjectMembers(projectId: string): Promise<ProjectMember[]> {
    const response = await fetch(`${BASE_URL}/workspace/projects/${projectId}/members`, {
      headers: { ...getAuthHeaders() },
    });

    if (!response.ok) {
      throw new Error("Failed to fetch project members");
    }

    const members: ProjectMemberApiResponse[] = await response.json();
    return members.map(({ projectRole, ...member }) => ({ ...member, role: projectRole }));
  },

  async inviteMember(projectId: string, username: string, role: ProjectRole): Promise<void> {
    const response = await fetch(`${BASE_URL}/workspace/projects/${projectId}/members`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...getAuthHeaders() },
      body: JSON.stringify({ username, role }),
    });

    if (!response.ok) {
      await checkedJson(response, "This user needs a CodeGen account first, or already has access");
    }
  },

  async updateMemberRole(projectId: string, userId: number, role: ProjectRole): Promise<void> {
    const response = await fetch(`${BASE_URL}/workspace/projects/${projectId}/members/${userId}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json", ...getAuthHeaders() },
      body: JSON.stringify({ role }),
    });

    if (!response.ok) {
      throw new Error("Failed to update member role");
    }
  },

  async removeMember(projectId: string, userId: number): Promise<void> {
    const response = await fetch(`${BASE_URL}/workspace/projects/${projectId}/members/${userId}`, {
      method: "DELETE",
      headers: { ...getAuthHeaders() },
    });

    if (!response.ok) {
      throw new Error("Failed to remove member");
    }
  },

  async getChatHistory(projectId: string): Promise<ChatMessage[]> {
    const response = await fetch(`${BASE_URL}/intelligence/chat/projects/${projectId}`, {
      headers: { ...getAuthHeaders() },
    });

    if (!response.ok) {
      throw new Error("Failed to fetch chat history");
    }

    return response.json();
  },

  streamChat(
    projectId: string,
    message: string,
    onChunk: (chunk: string) => void,
    onFile: (path: string, content: string) => void,
    onComplete: () => void,
    onError: (error: Error) => void
  ) {
    const controller = new AbortController();

    fetch(`${BASE_URL}/intelligence/chat/stream`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "text/event-stream", ...getAuthHeaders() },
      body: JSON.stringify({ message, projectId }),
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) throw new Error("Chat stream failed");
        if (!response.headers.get("Content-Type")?.includes("text/event-stream")) {
          throw new Error("The server did not return a chat stream. Check the API gateway connection.");
        }

        const reader = response.body?.getReader();
        if (!reader) throw new Error("No reader available");

        const decoder = new TextDecoder();

        let sseBuffer = "";
        let fullContentBuffer = "";
        let reportedFileCount = 0;

        const processLine = (line: string) => {
          const content = parseSseDataLine(line);
          if (content === null) return;

          onChunk(content);
          fullContentBuffer += content;

          const completedEdits = extractCompletedFileEdits(fullContentBuffer);
          for (const edit of completedEdits.slice(reportedFileCount)) {
            onFile(edit.path, edit.content);
          }
          reportedFileCount = completedEdits.length;
        };

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          const chunk = decoder.decode(value, { stream: true });
          sseBuffer += chunk;

          // Process line by line to handle SSE format (data: ...)
          const lines = sseBuffer.split("\n");
          sseBuffer = lines.pop() || "";

          for (const line of lines) {
            try {
              processLine(line);
            } catch {
              throw new Error("Received an invalid chat stream event");
            }
          }
        }

        sseBuffer += decoder.decode();
        if (sseBuffer.trim()) {
          processLine(sseBuffer);
        }

        onComplete();
      })
      .catch((error) => {
        if (error.name !== "AbortError") {
          console.error("Stream error:", error);
          onError(error);
        }
      });

    return () => controller.abort();
  }

};
