// src/components/settings/LlmConfigPanel.tsx
import { useEffect, useState } from "react";
import {
  fetchLlmConfig, saveLlmConfig, deleteLlmConfig, LlmConfigError, type LlmConfig,
} from "../../api/llmConfig";
import { fetchPresets, type LlmProviderPreset } from "../../api/llmProviders";

export default function LlmConfigPanel({ canEdit = true }: { canEdit?: boolean }) {
  const [config, setConfig] = useState<LlmConfig | null>(null);
  const [presets, setPresets] = useState<LlmProviderPreset[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState(false);
  const [presetId, setPresetId] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  useEffect(() => {
    setIsLoading(true);
    Promise.all([fetchLlmConfig(), fetchPresets()])
      .then(([cfg, rows]) => {
        setConfig(cfg);
        setPresets(rows);
        setPresetId(cfg?.presetId ?? "");
        setEditing(!cfg);
      })
      .catch(() => setError("加载失败，请重试"))
      .finally(() => setIsLoading(false));
  }, []);

  const startEdit = () => {
    setPresetId(config?.presetId ?? "");
    setApiKey("");
    setError("");
    setEditing(true);
  };

  const handleSave = async () => {
    if (!presetId || !apiKey.trim()) return;
    setIsSaving(true);
    setError("");
    try {
      await saveLlmConfig(presetId, apiKey.trim());
      const cfg = await fetchLlmConfig();
      setConfig(cfg);
      setApiKey("");
      setEditing(false);
    } catch (err) {
      setError(err instanceof LlmConfigError ? err.message : "保存失败，请重试");
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async () => {
    setIsSaving(true);
    setError("");
    try {
      await deleteLlmConfig();
      setConfig(null);
      setPresetId("");
      setApiKey("");
      setConfirmingDelete(false);
      setEditing(true);
    } catch {
      setError("删除失败，请重试");
    } finally {
      setIsSaving(false);
    }
  };

  const inputCls = `w-full px-3 py-2 border border-stone-200 rounded-lg text-sm
    focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary/20
    disabled:opacity-50 disabled:cursor-not-allowed`;

  const form = (
    <div className="space-y-3 max-w-md">
      <div>
        <label htmlFor="llm-preset" className="block text-xs text-stone-500 mb-1">模型</label>
        <select
          id="llm-preset"
          value={presetId}
          onChange={(e) => setPresetId(e.target.value)}
          disabled={isSaving || presets.length === 0}
          className={inputCls}
        >
          <option value="" disabled>请选择模型</option>
          {presets.map((p) => (
            <option key={p.id} value={p.id}>{p.name}（{p.model}）</option>
          ))}
        </select>
      </div>
      <div>
        <label htmlFor="llm-apikey" className="block text-xs text-stone-500 mb-1">API Key</label>
        <input
          id="llm-apikey"
          type="password"
          value={apiKey}
          onChange={(e) => setApiKey(e.target.value)}
          placeholder="输入你的 API Key"
          disabled={isSaving || presets.length === 0}
          className={inputCls}
        />
      </div>
      <div className="flex gap-2">
        <button
          type="button"
          onClick={handleSave}
          disabled={isSaving || !presetId || !apiKey.trim()}
          className="px-4 py-2 bg-primary text-white text-sm font-medium rounded-lg
            hover:bg-primary-light disabled:opacity-40 disabled:cursor-not-allowed
            transition-colors cursor-pointer"
        >
          {isSaving ? "验证中…" : "保存"}
        </button>
        {config && (
          <button
            type="button"
            onClick={() => setEditing(false)}
            disabled={isSaving}
            className="px-4 py-2 border border-stone-300 text-stone-600 text-sm rounded-lg
              hover:bg-stone-100 disabled:opacity-40 disabled:cursor-not-allowed
              transition-colors cursor-pointer"
          >
            取消
          </button>
        )}
      </div>
    </div>
  );

  return (
    <div>
      <h2 className="text-base font-semibold text-stone-800 mb-4">LLM 设置</h2>

      {error && <p className="text-xs text-red-500 mb-3">{error}</p>}

      {isLoading ? (
        <p className="text-sm text-stone-400">加载中...</p>
      ) : !canEdit && !config ? (
        <p className="text-sm text-stone-400">请联系租户管理员配置模型</p>
      ) : presets.length === 0 && !config ? (
        <>
          <p className="text-sm text-stone-400 mb-4">暂无可用模型，请联系管理员</p>
          {form}
        </>
      ) : !editing && config ? (
        <div className="max-w-md">
          <div className="border border-stone-200 rounded-lg p-4 space-y-2 mb-4">
            <div className="flex items-center justify-between text-sm">
              <span className="text-stone-500">模型</span>
              <span className="text-stone-800">{config.presetName}（{config.model}）</span>
            </div>
            <div className="flex items-center justify-between text-sm">
              <span className="text-stone-500">API Key</span>
              <span className="text-stone-800">{config.apiKeyMasked}</span>
            </div>
          </div>
          {canEdit && (
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={startEdit}
              disabled={isSaving}
              className="px-4 py-2 border border-stone-300 text-stone-600 text-sm rounded-lg
                hover:bg-stone-100 disabled:opacity-40 disabled:cursor-not-allowed
                transition-colors cursor-pointer"
            >
              更换配置
            </button>
            {confirmingDelete ? (
              <>
                <span className="text-xs text-stone-500">确认删除当前配置？</span>
                <button
                  type="button"
                  onClick={handleDelete}
                  disabled={isSaving}
                  className="px-4 py-2 border border-red-300 text-red-600 text-sm rounded-lg
                    hover:bg-red-50 disabled:opacity-40 disabled:cursor-not-allowed
                    transition-colors cursor-pointer"
                >
                  确认删除
                </button>
                <button
                  type="button"
                  onClick={() => setConfirmingDelete(false)}
                  disabled={isSaving}
                  className="px-4 py-2 border border-stone-300 text-stone-600 text-sm rounded-lg
                    hover:bg-stone-100 disabled:opacity-40 disabled:cursor-not-allowed
                    transition-colors cursor-pointer"
                >
                  取消
                </button>
              </>
            ) : (
              <button
                type="button"
                onClick={() => setConfirmingDelete(true)}
                disabled={isSaving}
                className="px-4 py-2 border border-stone-300 text-stone-600 text-sm rounded-lg
                  hover:bg-stone-100 disabled:opacity-40 disabled:cursor-not-allowed
                  transition-colors cursor-pointer"
              >
                删除配置
              </button>
            )}
          </div>
          )}
        </div>
      ) : (
        form
      )}
    </div>
  );
}
