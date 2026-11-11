import { useEffect, useState, useCallback } from "react";
import { listHints, createHint, updateHint, deleteHint, HintError, type Hint } from "../../api/hints";

export default function HintManagementPanel() {
  const [hints, setHints] = useState<Hint[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");
  const [newContent, setNewContent] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingContent, setEditingContent] = useState("");

  const load = useCallback(async () => {
    setIsLoading(true);
    setError("");
    try {
      const rows = await listHints();
      setHints(rows);
    } catch {
      setError("加载失败，请重试");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleAdd = async () => {
    const content = newContent.trim();
    if (!content) return;
    setIsSaving(true);
    setError("");
    try {
      await createHint(content);
      setNewContent("");
      await load();
    } catch (err) {
      setError(err instanceof HintError ? err.reason : "添加失败，请重试");
    } finally {
      setIsSaving(false);
    }
  };

  const startEdit = (hint: Hint) => {
    setEditingId(hint.id);
    setEditingContent(hint.content);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditingContent("");
  };

  const handleSaveEdit = async (id: string) => {
    const content = editingContent.trim();
    if (!content) return;
    setIsSaving(true);
    setError("");
    try {
      await updateHint(id, content);
      setEditingId(null);
      await load();
    } catch (err) {
      setError(err instanceof HintError ? err.reason : "保存失败，请重试");
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async (id: string) => {
    setIsSaving(true);
    setError("");
    try {
      await deleteHint(id);
      await load();
    } catch (err) {
      setError(err instanceof HintError ? err.reason : "删除失败，请重试");
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div>
      <h2 className="text-base font-semibold text-stone-800 mb-4">提示语管理</h2>

      {error && <p className="text-xs font-medium bg-slime-lime text-ink rounded-md px-2 py-1 mb-3">{error}</p>}

      {isLoading ? (
        <p className="text-sm text-stone-400">加载中...</p>
      ) : hints.length === 0 ? (
        <p className="text-sm text-stone-400 mb-4">暂无提示语</p>
      ) : (
        <div className="space-y-2 mb-4">
          {hints.map((h) => (
            <div key={h.id} className="flex items-center gap-2 border border-stone-200 rounded-lg p-2">
              {editingId === h.id ? (
                <>
                  <input
                    value={editingContent}
                    onChange={(e) => setEditingContent(e.target.value)}
                    className="flex-1 px-2 py-1 border border-fresh-sky rounded text-sm shadow-input
                      focus:outline-none focus:border-fresh-sky focus:shadow-input-focus"
                  />
                  <button
                    type="button"
                    onClick={() => handleSaveEdit(h.id)}
                    disabled={isSaving || !editingContent.trim()}
                    className="px-3 py-1 text-xs rounded-full bg-fresh-sky text-ink
                      hover:bg-fresh-sky-dark disabled:opacity-40 disabled:cursor-not-allowed
                      transition-colors cursor-pointer"
                  >
                    保存
                  </button>
                  <button
                    type="button"
                    onClick={cancelEdit}
                    disabled={isSaving}
                    className="px-3 py-1 text-xs rounded-full border border-fresh-sky text-fresh-sky
                      hover:bg-frozen-water disabled:opacity-40 disabled:cursor-not-allowed
                      transition-colors cursor-pointer"
                  >
                    取消
                  </button>
                </>
              ) : (
                <>
                  <span className="flex-1 text-sm text-stone-700">{h.content}</span>
                  <button
                    type="button"
                    onClick={() => startEdit(h)}
                    disabled={isSaving}
                    className="px-3 py-1 text-xs rounded-full border border-fresh-sky text-fresh-sky
                      hover:bg-frozen-water disabled:opacity-40 disabled:cursor-not-allowed
                      transition-colors cursor-pointer"
                  >
                    编辑
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDelete(h.id)}
                    disabled={isSaving}
                    className="px-3 py-1 text-xs rounded-full border border-fresh-sky text-fresh-sky
                      hover:bg-frozen-water disabled:opacity-40 disabled:cursor-not-allowed
                      transition-colors cursor-pointer"
                  >
                    删除
                  </button>
                </>
              )}
            </div>
          ))}
        </div>
      )}

      <div className="flex items-center gap-2">
        <input
          value={newContent}
          onChange={(e) => setNewContent(e.target.value)}
          placeholder="新提示语"
          disabled={isSaving}
          className="flex-1 px-3 py-2 border border-fresh-sky rounded-lg text-sm shadow-input
            focus:outline-none focus:border-fresh-sky focus:shadow-input-focus"
        />
        <button
          type="button"
          onClick={handleAdd}
          disabled={isSaving || !newContent.trim()}
          className="px-4 py-2 bg-fresh-sky text-ink text-sm font-medium rounded-lg
            hover:bg-fresh-sky-dark disabled:opacity-40 disabled:cursor-not-allowed
            transition-colors cursor-pointer"
        >
          添加
        </button>
      </div>
    </div>
  );
}
