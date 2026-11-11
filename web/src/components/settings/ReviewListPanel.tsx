import { useEffect, useState, useCallback } from "react";
import {
  listKnowledge,
  approveKnowledge,
  rejectKnowledge,
  KnowledgeError,
  type KnowledgeDoc,
  type KnowledgeStatus,
} from "../../api/knowledge";

const TABS: { status: KnowledgeStatus; label: string }[] = [
  { status: "PENDING", label: "待审核" },
  { status: "APPROVED", label: "已通过" },
  { status: "REJECTED", label: "已拒绝" },
];

function formatTime(iso: string): string {
  return new Date(iso).toLocaleString("zh-CN");
}

export default function ReviewListPanel() {
  const [status, setStatus] = useState<KnowledgeStatus>("PENDING");
  const [docs, setDocs] = useState<KnowledgeDoc[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");
  const [actioningId, setActioningId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError("");
    try {
      const rows = await listKnowledge(status);
      setDocs(rows);
    } catch {
      setError("加载失败，请重试");
    } finally {
      setIsLoading(false);
    }
  }, [status]);

  useEffect(() => {
    load();
  }, [load]);

  const handleAction = async (id: string, action: "approve" | "reject") => {
    setActioningId(id);
    setError("");
    try {
      if (action === "approve") await approveKnowledge(id);
      else await rejectKnowledge(id);
      await load();
    } catch (err) {
      setError(err instanceof KnowledgeError ? err.reason : "操作失败，请重试");
    } finally {
      setActioningId(null);
    }
  };

  return (
    <div>
      <h2 className="text-base font-semibold text-stone-800 mb-4">文件上传审核列表</h2>

      <div className="flex gap-2 mb-4">
        {TABS.map((t) => (
          <button
            key={t.status}
            type="button"
            onClick={() => setStatus(t.status)}
            className={`px-3 py-1.5 text-sm rounded-full transition-colors cursor-pointer
              ${status === t.status ? "bg-frozen-water-mountain-lake border border-fresh-sky text-fresh-sky" : "border border-transparent text-fresh-sky hover:bg-frozen-water"}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {error && <p className="text-xs font-medium bg-slime-lime text-ink rounded-md px-2 py-1 mb-3">{error}</p>}

      {isLoading ? (
        <p className="text-sm text-stone-400">加载中...</p>
      ) : docs.length === 0 ? (
        <p className="text-sm text-stone-400">暂无文档</p>
      ) : (
        <div className="space-y-3">
          {docs.map((d) => (
            <div key={d.id} className="border border-stone-200 rounded-lg p-3">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-stone-800 truncate">{d.title}</p>
                  <p className="text-xs text-stone-400 flex items-center gap-1.5">
                    <span className="inline-block px-1.5 py-0.5 rounded bg-blush-pop text-ink text-[11px]">
                      {d.scopeName || d.scopeType}
                    </span>
                    {d.uploaderUserId} · {formatTime(d.uploadedAt)}
                  </p>
                </div>
                {status === "PENDING" && (
                  <div className="flex gap-2 shrink-0">
                    <button
                      type="button"
                      onClick={() => handleAction(d.id, "approve")}
                      disabled={actioningId === d.id}
                      className="px-3 py-1 text-xs rounded-full bg-fresh-sky text-ink
                        hover:bg-fresh-sky-dark disabled:opacity-40 disabled:cursor-not-allowed
                        transition-colors cursor-pointer"
                    >
                      通过
                    </button>
                    <button
                      type="button"
                      onClick={() => handleAction(d.id, "reject")}
                      disabled={actioningId === d.id}
                      className="px-3 py-1 text-xs rounded-full border border-fresh-sky text-fresh-sky
                        hover:bg-frozen-water disabled:opacity-40 disabled:cursor-not-allowed
                        transition-colors cursor-pointer"
                    >
                      拒绝
                    </button>
                  </div>
                )}
              </div>
              {d.summary && <p className="text-xs text-stone-600 mt-2">{d.summary}</p>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
