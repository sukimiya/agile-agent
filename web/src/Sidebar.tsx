import { useEffect, useState } from "react";
import { listConversations, type ConversationSummary } from "./api/conversations";

interface Props {
  activeId: string;
  onSelect: (id: string) => void;
  onNewChat: () => void;
  refreshKey: number;
  collapsed: boolean;
  onToggle: () => void;
  userName: string;
  onLogout: () => void;
  onOpenSettings: () => void;
}

function formatTime(iso: string): string {
  const d = new Date(iso);
  const now = new Date();
  const sameDay = d.toDateString() === now.toDateString();
  return sameDay
    ? d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    : d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' });
}

function groupByDay(conversations: ConversationSummary[]): { label: string; items: ConversationSummary[] }[] {
  const now = new Date();
  const today: ConversationSummary[] = [];
  const earlier: ConversationSummary[] = [];
  for (const c of conversations) {
    const sameDay = new Date(c.updatedAt).toDateString() === now.toDateString();
    (sameDay ? today : earlier).push(c);
  }
  const groups: { label: string; items: ConversationSummary[] }[] = [];
  if (today.length > 0) groups.push({ label: "今天", items: today });
  if (earlier.length > 0) groups.push({ label: "更早", items: earlier });
  return groups;
}

function ChevronIcon({ direction }: { direction: "left" | "right" }) {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      {direction === "left" ? <polyline points="15 18 9 12 15 6" /> : <polyline points="9 18 15 12 9 6" />}
    </svg>
  );
}

function MessageIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="shrink-0 mt-0.5">
      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
    </svg>
  );
}

function SettingsIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </svg>
  );
}

export default function Sidebar({
  activeId, onSelect, onNewChat, refreshKey, collapsed, onToggle, userName, onLogout, onOpenSettings,
}: Props) {
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);

  useEffect(() => {
    listConversations().then(setConversations).catch(() => setConversations([]));
  }, [refreshKey]);

  if (collapsed) {
    return (
      <div className="w-12 shrink-0 border-r border-stone-200 bg-stone-100 flex flex-col items-center h-full py-3 gap-3">
        <button
          onClick={onToggle}
          aria-label="展开侧边栏"
          className="w-8 h-8 rounded-full flex items-center justify-center text-stone-400 hover:bg-stone-200 transition-colors cursor-pointer"
        >
          <ChevronIcon direction="right" />
        </button>
        <button
          onClick={onNewChat}
          aria-label="新对话"
          className="w-8 h-8 rounded-full flex items-center justify-center text-fresh-sky border border-fresh-sky hover:bg-frozen-water transition-colors cursor-pointer text-lg"
        >
          +
        </button>
      </div>
    );
  }

  const groups = groupByDay(conversations);

  return (
    <div className="w-64 shrink-0 border-r border-stone-200 bg-stone-100 flex flex-col h-full">
      <div className="px-4 pt-4 pb-1 text-base font-semibold tracking-tight text-medium-violet-red">agile-agent</div>
      <div className="p-3 flex items-center gap-2">
        <button
          onClick={onNewChat}
          className="flex-1 px-3 py-2 text-sm rounded-full border border-fresh-sky text-fresh-sky font-medium
            hover:bg-frozen-water transition-colors cursor-pointer"
        >
          + 新对话
        </button>
        <button
          onClick={onToggle}
          aria-label="收起侧边栏"
          className="w-8 h-8 shrink-0 rounded-full flex items-center justify-center text-stone-400 hover:bg-stone-200 transition-colors cursor-pointer"
        >
          <ChevronIcon direction="left" />
        </button>
      </div>
      <div className="flex-1 overflow-y-auto px-2 space-y-3">
        {groups.map((g) => (
          <div key={g.label}>
            <div className="px-3 pb-1 text-xs font-medium text-stone-400 uppercase tracking-wide">{g.label}</div>
            <div className="space-y-1">
              {g.items.map((c) => (
                <button
                  key={c.id}
                  onClick={() => onSelect(c.id)}
                  className={`w-full flex items-start gap-2 text-left px-3 py-2 rounded-lg text-sm transition-colors cursor-pointer
                    ${c.id === activeId ? "bg-frozen-water-mountain-lake text-history" : "hover:bg-stone-200 text-history"}`}
                >
                  <MessageIcon />
                  <div className="min-w-0">
                    <div className="truncate">{c.title}</div>
                    <div className="text-xs text-stone-400">{formatTime(c.updatedAt)}</div>
                  </div>
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>
      <div className="border-t border-stone-200 p-3 flex items-center gap-2">
        <div className="w-8 h-8 rounded-full bg-medium-violet-red text-white flex items-center justify-center text-xs font-medium shrink-0">
          {userName.trim().slice(0, 2)}
        </div>
        <div className="flex-1 min-w-0">
          <div className="text-sm text-stone-700 truncate">{userName}</div>
          <button
            onClick={onLogout}
            className="text-xs text-stone-700 underline decoration-fresh-sky decoration-2 underline-offset-2 hover:text-stone-500 transition-colors cursor-pointer"
          >
            退出登录
          </button>
        </div>
        <button
          onClick={onOpenSettings}
          aria-label="设置"
          className="w-8 h-8 shrink-0 rounded-full flex items-center justify-center text-stone-400 hover:bg-stone-200 hover:text-stone-600 transition-colors cursor-pointer"
        >
          <SettingsIcon />
        </button>
      </div>
    </div>
  );
}
