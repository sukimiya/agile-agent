import { create } from "zustand";
import { getToken, setToken, clearToken } from "./api/client";

export interface Conversation {
  id: string;
  title: string;
  createdAt: Date;
}

export interface AccessProfile {
  platformAdmin: boolean;
  tenantAdmin: boolean;
  roles: string[];
}

const emptyAccess: AccessProfile = { platformAdmin: false, tenantAdmin: false, roles: [] };

interface ChatState {
  token: string | null;
  userId: string | null;
  userName: string | null;
  setAuth: (auth: { token: string; userId: string; name: string }) => void;
  clearAuth: () => void;

  platformAdmin: boolean;
  tenantAdmin: boolean;
  roles: string[];
  setAccess: (access: AccessProfile) => void;

  conversations: Conversation[];
  activeId: string | null;
  setActiveId: (id: string) => void;
  addConversation: (conv: Conversation) => void;

  pendingFiles: File[];
  addFiles: (files: File[]) => void;
  removeFile: (index: number) => void;
  clearFiles: () => void;
}

export const useStore = create<ChatState>((set) => ({
  token: getToken(),
  userId: localStorage.getItem("auth-user-id"),
  userName: localStorage.getItem("auth-user-name"),
  setAuth: ({ token, userId, name }) => {
    setToken(token);
    localStorage.setItem("auth-user-id", userId);
    localStorage.setItem("auth-user-name", name);
    set({ token, userId, userName: name });
  },
  clearAuth: () => {
    clearToken();
    localStorage.removeItem("auth-user-id");
    localStorage.removeItem("auth-user-name");
    set({
      token: null,
      userId: null,
      userName: null,
      ...emptyAccess,
      conversations: [],
      activeId: null,
    });
  },

  ...emptyAccess,
  setAccess: (access) => set({
    platformAdmin: access.platformAdmin,
    tenantAdmin: access.tenantAdmin,
    roles: access.roles,
  }),

  conversations: [],
  activeId: null,
  setActiveId: (id) => set({ activeId: id }),
  addConversation: (conv) =>
    set((s) => ({ conversations: [conv, ...s.conversations] })),

  pendingFiles: [],
  addFiles: (files) => set((s) => ({ pendingFiles: [...s.pendingFiles, ...files] })),
  removeFile: (index) =>
    set((s) => ({ pendingFiles: s.pendingFiles.filter((_, i) => i !== index) })),
  clearFiles: () => set({ pendingFiles: [] }),
}));
