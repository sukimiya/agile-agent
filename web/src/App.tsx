import { useState, useCallback, useEffect, useRef } from "react";
import Chat from "./Chat";
import Login from "./Login";
import Sidebar from "./Sidebar";
import SettingsModal, { type MenuKey } from "./components/SettingsModal";
import { uploadFiles, UploadError } from "./api/upload";
import { apiFetch, UnauthorizedError } from "./api/client";
import { fetchCurrentUser, logout as apiLogout, storeSessionToken } from "./api/auth";
import { getConversationMessages } from "./api/conversations";
import { consumeOAuthCallback, oauthErrorMessage } from "./auth/callback";
import { useStore } from "./store";
import { conversationId, genId, isCanonicalUuid } from "./lib/id";

interface UIMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  attachments?: { name: string }[];
  usage?: { promptTokens: number; completionTokens: number; totalTokens: number };
  action?: "open-llm-settings";
}

const LLM_REQUIRED_MESSAGE = "请先在设置页配置模型和 API Key";

function getInitialConvId(): string {
  const params = new URLSearchParams(window.location.search);
  const id = params.get("id");
  return isCanonicalUuid(id) ? id : conversationId();
}

export default function App() {
  const token = useStore((s) => s.token);
  const userName = useStore((s) => s.userName);
  const platformAdmin = useStore((s) => s.platformAdmin);
  const tenantAdmin = useStore((s) => s.tenantAdmin);
  const clearAuth = useStore((s) => s.clearAuth);
  const pendingFiles = useStore((s) => s.pendingFiles);
  const clearFiles = useStore((s) => s.clearFiles);
  const setAuth = useStore((s) => s.setAuth);
  const setAccess = useStore((s) => s.setAccess);

  const [callback] = useState(() => consumeOAuthCallback());
  const [convId, setConvId] = useState<string>(getInitialConvId);
  const [messages, setMessages] = useState<UIMessage[]>([]);
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [sidebarRefreshKey, setSidebarRefreshKey] = useState(0);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [settingsInitialMenu, setSettingsInitialMenu] = useState<MenuKey | undefined>(undefined);
  const [loginError, setLoginError] = useState<string | null>(() =>
    callback.error ? oauthErrorMessage(callback.error) : null,
  );
  const [bootstrapping, setBootstrapping] = useState(() => !!callback.token);

  const openLlmSettings = () => {
    setSettingsInitialMenu("llm-config");
    setSettingsOpen(true);
  };
  const messagesRef = useRef(messages);
  const convIdRef = useRef(convId);
  const skipNextFetchRef = useRef(false);

  const resetSession = useCallback(() => {
    clearAuth();
    clearFiles();
    setConvId(conversationId());
    setMessages([]);
    setInput("");
  }, [clearAuth, clearFiles]);

  useEffect(() => { messagesRef.current = messages; }, [messages]);
  useEffect(() => { convIdRef.current = convId; }, [convId]);

  useEffect(() => {
    if (!callback.token) return;
    let cancelled = false;
    const appToken = callback.token;
    storeSessionToken(appToken);
    fetchCurrentUser()
      .then((user) => {
        if (cancelled) return;
        clearFiles();
        setMessages([]);
        setInput("");
        setAuth({
          token: appToken,
          userId: user.userId,
          name: user.name || user.displayName || user.userId,
        });
        setAccess(user);
      })
      .catch(() => {
        if (cancelled) return;
        resetSession();
        setLoginError(oauthErrorMessage("auth_failed"));
      })
      .finally(() => {
        if (!cancelled) setBootstrapping(false);
      });
    return () => { cancelled = true; };
  }, [callback.token, clearFiles, resetSession, setAccess, setAuth]);

  useEffect(() => {
    if (bootstrapping || !token) return;
    let cancelled = false;
    fetchCurrentUser()
      .then((user) => { if (!cancelled) setAccess(user); })
      .catch(() => { if (!cancelled) resetSession(); });
    return () => { cancelled = true; };
  }, [bootstrapping, token, resetSession, setAccess]);

  useEffect(() => {
    const url = new URL(window.location.href);
    url.searchParams.set("id", convId);
    window.history.replaceState(null, "", url.toString());
  }, [convId]);

  useEffect(() => {
    if (bootstrapping || !token) return;
    if (skipNextFetchRef.current) {
      skipNextFetchRef.current = false;
      return;
    }
    let cancelled = false;
    getConversationMessages(convId)
      .then((msgs) => {
        if (cancelled) return;
        setMessages((msgs ?? []).map((m) => ({ id: m.id, role: m.role, content: m.content })));
      })
      .catch((err) => {
        if (cancelled) return;
        if (err instanceof UnauthorizedError) {
          resetSession();
        } else {
          setMessages([]);
        }
      });
    return () => { cancelled = true; };
  }, [bootstrapping, convId, token, resetSession]);

  const send = useCallback(async (text?: string) => {
    const msg = (text ?? input).trim();
    if ((!msg && pendingFiles.length === 0) || isLoading) return;

    const streamConvId = convId;

    setInput("");
    setIsLoading(true);

    let uploadedRefs: { name: string }[] = [];
    if (pendingFiles.length > 0) {
      try {
        const saved = await uploadFiles(convId, pendingFiles);
        uploadedRefs = saved.map((ref) => ({ name: ref.name }));
      } catch (e) {
        const reason = e instanceof UploadError ? e.message : "上传失败";
        setMessages((prev) => [...prev, {
          id: genId(),
          role: "assistant",
          content: `文件上传失败：${reason}`,
        }]);
        setIsLoading(false);
        return;
      }
      clearFiles();
    }

    const displayContent = msg || "（已上传文件）";
    const apiContent = uploadedRefs.length > 0
      ? `[已上传文件]\n${uploadedRefs.map(r => `- ${r.name}`).join("\n")}\n\n${msg}`
      : msg;

    const uid = genId();
    const aid = genId();
    const newMsgs: UIMessage[] = [
      ...messagesRef.current,
      { id: uid, role: "user", content: displayContent, attachments: uploadedRefs },
      { id: aid, role: "assistant", content: "" },
    ];
    setMessages(newMsgs);

    try {
      const res = await apiFetch("/api/chat/completions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          model: "agile-agent",
          messages: [{ role: "user", content: apiContent }],
          conversationId: convId,
          stream: true,
        }),
      });

      if (res.status === 428) {
        setMessages((prev) => {
          if (convIdRef.current !== streamConvId) return prev;
          const updated = [...prev];
          updated[updated.length - 1] = {
            ...updated[updated.length - 1],
            content: LLM_REQUIRED_MESSAGE,
            action: "open-llm-settings",
          };
          return updated;
        });
        return;
      }
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      if (!res.body) throw new Error("No stream body");
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() || "";

        for (const line of lines) {
          const trimmed = line.trim();
          if (!trimmed.startsWith("data:")) continue;
          const jsonStr = trimmed.slice(5).trim();
          if (!jsonStr || jsonStr === "[DONE]") continue;
          try {
            const c = JSON.parse(jsonStr);
            const content = c?.choices?.[0]?.delta?.content || "";
            if (content) {
              setMessages((prev) => {
                if (convIdRef.current !== streamConvId) return prev;
                const updated = [...prev];
                const last = updated[updated.length - 1];
                updated[updated.length - 1] = { ...last, content: last.content + content };
                return updated;
              });
            }
            if (c?.usage) {
              setMessages((prev) => {
                if (convIdRef.current !== streamConvId) return prev;
                const updated = [...prev];
                const last = updated[updated.length - 1];
                updated[updated.length - 1] = {
                  ...last,
                  usage: {
                    promptTokens: c.usage.prompt_tokens ?? 0,
                    completionTokens: c.usage.completion_tokens ?? 0,
                    totalTokens: c.usage.total_tokens ?? 0,
                  },
                };
                return updated;
              });
            }
          } catch { /* skip */ }
        }
      }
      setSidebarRefreshKey((k) => k + 1);
    } catch (err) {
      if (err instanceof UnauthorizedError) {
        resetSession();
      } else {
        const message = "请求失败: " + (err instanceof Error ? err.message : "未知错误");
        setMessages((prev) => {
          if (convIdRef.current !== streamConvId) return prev;
          const updated = [...prev];
          updated[updated.length - 1] = { ...updated[updated.length - 1], content: message };
          return updated;
        });
      }
    } finally {
      setIsLoading(false);
    }
  }, [input, isLoading, pendingFiles, clearFiles, resetSession, convId]);

  const newChat = () => {
    skipNextFetchRef.current = true;
    setConvId(conversationId());
    setMessages([]);
  };

  const handleLogout = async () => {
    try { await apiLogout(); } catch { /* best-effort */ }
    resetSession();
  };

  if (bootstrapping) {
    return (
      <div className="flex h-screen items-center justify-center bg-[#FBFAF7] text-sm text-stone-400">
        加载中...
      </div>
    );
  }

  if (!token) {
    return <Login error={loginError} />;
  }

  return (
    <div className="flex h-screen bg-[#FBFAF7]">
      <Sidebar
        activeId={convId}
        onSelect={(id) => setConvId(id)}
        onNewChat={newChat}
        refreshKey={sidebarRefreshKey}
        collapsed={sidebarCollapsed}
        onToggle={() => setSidebarCollapsed((v) => !v)}
        userName={userName ?? ""}
        onLogout={handleLogout}
        onOpenSettings={() => setSettingsOpen(true)}
      />
      <div className="flex-1 overflow-hidden">
        <Chat
          messages={messages}
          isLoading={isLoading}
          input={input}
          onInputChange={setInput}
          onSubmit={send}
          onOpenLlmSettings={openLlmSettings}
        />
      </div>
      <SettingsModal
        isOpen={settingsOpen}
        onClose={() => {
          setSettingsOpen(false);
          setSettingsInitialMenu(undefined);
        }}
        platformAdmin={platformAdmin}
        tenantAdmin={tenantAdmin}
        initialMenu={settingsInitialMenu}
      />
    </div>
  );
}
