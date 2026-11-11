// src/components/settings/LlmPresetPanel.tsx
import { useEffect, useState, useCallback } from "react";
import {
  fetchPresets, createPreset, updatePreset, deletePreset,
  PresetError, type LlmProviderPreset,
} from "../../api/llmProviders";

interface FormState {
  name: string;
  baseUrl: string;
  model: string;
  balancePath: string;
}

const EMPTY_FORM: FormState = { name: "", baseUrl: "", model: "", balancePath: "" };

export default function LlmPresetPanel() {
  const [presets, setPresets] = useState<LlmProviderPreset[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const [editingId, setEditingId] = useState<string | "new" | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [confirmingDeleteId, setConfirmingDeleteId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError("");
    try {
      setPresets(await fetchPresets());
    } catch (err) {
      setError(err instanceof PresetError ? err.message : "加载失败，请重试");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const startCreate = () => {
    setEditingId("new");
    setForm(EMPTY_FORM);
    setError("");
  };

  const startEdit = (p: LlmProviderPreset) => {
    setEditingId(p.id);
    setForm({ name: p.name, baseUrl: p.baseUrl, model: p.model, balancePath: p.balancePath ?? "" });
    setError("");
  };

  const cancelEdit = () => {
    setEditingId(null);
    setForm(EMPTY_FORM);
  };

  const handleSave = async () => {
    if (!form.name.trim() || !form.baseUrl.trim() || !form.model.trim()) return;
    setIsSaving(true);
    setError("");
    try {
      const input = {
        name: form.name.trim(),
        baseUrl: form.baseUrl.trim(),
        model: form.model.trim(),
        balancePath: form.balancePath.trim(),
      };
      if (editingId === "new") await createPreset(input);
      else if (editingId) await updatePreset(editingId, input);
      cancelEdit();
      await load();
    } catch (err) {
      setError(err instanceof PresetError ? err.message : "保存失败，请重试");
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async (id: string) => {
    setIsSaving(true);
    setError("");
    try {
      await deletePreset(id);
      setConfirmingDeleteId(null);
      await load();
    } catch (err) {
      setError(err instanceof PresetError ? err.message : "删除失败，请重试");
    } finally {
      setIsSaving(false);
    }
  };

  const inputCls = `w-full px-3 py-2 border border-stone-200 rounded-lg text-sm
    focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary/20
    disabled:opacity-50 disabled:cursor-not-allowed`;
  const btnCls = `px-3 py-1 text-xs rounded-full border border-stone-300 text-stone-600
    hover:bg-stone-100 disabled:opacity-40 disabled:cursor-not-allowed
    transition-colors cursor-pointer`;

  return (
    <div>
      <h2 className="text-base font-semibold text-stone-800 mb-4">模型预置管理</h2>

      {error && <p className="text-xs text-red-500 mb-3">{error}</p>}

      {isLoading ? (
        <p className="text-sm text-stone-400">加载中...</p>
      ) : (
        <>
          {presets.length === 0 ? (
            <p className="text-sm text-stone-400 mb-4">暂无预置</p>
          ) : (
            <div className="space-y-2 mb-4">
              {presets.map((p) => (
                <div key={p.id} className="border border-stone-200 rounded-lg p-3">
                  <div className="flex items-center gap-2">
                    <div className="flex-1 min-w-0">
                      <div className="text-sm text-stone-800">{p.name}（{p.model}）</div>
                      <div className="text-xs text-stone-500 truncate">{p.baseUrl}</div>
                      <div className="text-xs text-stone-400">
                        余额接口：{p.balancePath ?? "不支持"}
                      </div>
                    </div>
                    <button type="button" onClick={() => startEdit(p)} disabled={isSaving} className={btnCls}>
                      编辑
                    </button>
                    {confirmingDeleteId === p.id ? (
                      <>
                        <span className="text-xs text-stone-500">删除后引用该预置的用户配置将失效</span>
                        <button
                          type="button"
                          onClick={() => handleDelete(p.id)}
                          disabled={isSaving}
                          className="px-3 py-1 text-xs rounded-full border border-red-300 text-red-600
                            hover:bg-red-50 disabled:opacity-40 disabled:cursor-not-allowed
                            transition-colors cursor-pointer"
                        >
                          确认删除
                        </button>
                        <button
                          type="button"
                          onClick={() => setConfirmingDeleteId(null)}
                          disabled={isSaving}
                          className={btnCls}
                        >
                          取消
                        </button>
                      </>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setConfirmingDeleteId(p.id)}
                        disabled={isSaving}
                        className={btnCls}
                      >
                        删除
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}

          {editingId === null ? (
            <button
              type="button"
              onClick={startCreate}
              className="px-4 py-2 bg-primary text-white text-sm font-medium rounded-lg
                hover:bg-primary-light transition-colors cursor-pointer"
            >
              新增预置
            </button>
          ) : (
            <div className="space-y-3 max-w-md border border-stone-200 rounded-lg p-4">
              <div>
                <label htmlFor="preset-name" className="block text-xs text-stone-500 mb-1">名称</label>
                <input
                  id="preset-name"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  disabled={isSaving}
                  className={inputCls}
                />
              </div>
              <div>
                <label htmlFor="preset-baseurl" className="block text-xs text-stone-500 mb-1">Base URL</label>
                <input
                  id="preset-baseurl"
                  value={form.baseUrl}
                  onChange={(e) => setForm({ ...form, baseUrl: e.target.value })}
                  placeholder="https://api.example.com/v1"
                  disabled={isSaving}
                  className={inputCls}
                />
              </div>
              <div>
                <label htmlFor="preset-model" className="block text-xs text-stone-500 mb-1">Model</label>
                <input
                  id="preset-model"
                  value={form.model}
                  onChange={(e) => setForm({ ...form, model: e.target.value })}
                  disabled={isSaving}
                  className={inputCls}
                />
              </div>
              <div>
                <label htmlFor="preset-balancepath" className="block text-xs text-stone-500 mb-1">余额接口路径（可选）</label>
                <input
                  id="preset-balancepath"
                  value={form.balancePath}
                  onChange={(e) => setForm({ ...form, balancePath: e.target.value })}
                  placeholder="/user/balance，留空表示不支持余额查询"
                  disabled={isSaving}
                  className={inputCls}
                />
              </div>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={handleSave}
                  disabled={isSaving || !form.name.trim() || !form.baseUrl.trim() || !form.model.trim()}
                  className="px-4 py-2 bg-primary text-white text-sm font-medium rounded-lg
                    hover:bg-primary-light disabled:opacity-40 disabled:cursor-not-allowed
                    transition-colors cursor-pointer"
                >
                  保存
                </button>
                <button
                  type="button"
                  onClick={cancelEdit}
                  disabled={isSaving}
                  className="px-4 py-2 border border-stone-300 text-stone-600 text-sm rounded-lg
                    hover:bg-stone-100 disabled:opacity-40 disabled:cursor-not-allowed
                    transition-colors cursor-pointer"
                >
                  取消
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
