// src/components/settings/LlmPresetPanel.tsx
import { useEffect, useState, useCallback } from "react";
import {
  fetchPresets, createPreset, updatePreset, deletePreset,
  PresetError, type LlmProviderPreset, type PresetInput,
} from "../../api/llmProviders";
import { validatePreset, type FieldErrors, type PresetDraft } from "./presetValidation";

const NAME_HINT = "支持 x.ai, qwen, deepseek";

function NameHint() {
  const [open, setOpen] = useState(false);
  return (
    <span
      className="relative inline-flex"
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
    >
      <button
        type="button"
        aria-label={NAME_HINT}
        className="inline-flex h-3.5 w-3.5 items-center justify-center rounded-full text-fresh-sky
          focus:outline-none focus:shadow-input-focus"
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
      >
        <svg width="14" height="14" viewBox="0 0 16 16" aria-hidden="true">
          <circle cx="8" cy="8" r="6.25" fill="none" stroke="currentColor" strokeWidth="1.25" />
          <path d="M8 7.15v4" stroke="currentColor" strokeWidth="1.25" strokeLinecap="round" />
          <circle cx="8" cy="4.7" r="0.7" fill="currentColor" />
        </svg>
      </button>
      {open && (
        <span
          role="tooltip"
          className="absolute left-5 top-1/2 z-20 -translate-y-1/2 whitespace-nowrap rounded-md
            border border-stone-200 bg-surface px-2 py-1 text-xs text-stone-700 shadow-soft"
        >
          {NAME_HINT}
        </span>
      )}
    </span>
  );
}

const EMPTY_FORM: PresetDraft = {
  name: "",
  baseUrl: "",
  model: "",
  balancePath: "",
  balanceBaseUrl: "",
  contextWindowTokens: "",
  maxOutputTokens: "",
  balanceAmountPointer: "",
  balanceCurrencyPointer: "",
  balanceCurrency: "",
  balanceDivisor: "",
};

function draftFrom(p: LlmProviderPreset): PresetDraft {
  return {
    name: p.name,
    baseUrl: p.baseUrl,
    model: p.model,
    balancePath: p.balancePath ?? "",
    balanceBaseUrl: p.balanceBaseUrl ?? "",
    contextWindowTokens: p.contextWindowTokens == null ? "" : String(p.contextWindowTokens),
    maxOutputTokens: p.maxOutputTokens == null ? "" : String(p.maxOutputTokens),
    balanceAmountPointer: p.balanceAmountPointer ?? "",
    balanceCurrencyPointer: p.balanceCurrencyPointer ?? "",
    balanceCurrency: p.balanceCurrency ?? "",
    balanceDivisor: p.balanceDivisor == null ? "" : String(p.balanceDivisor),
  };
}

function toInput(form: PresetDraft, negate: boolean): PresetInput {
  const input: PresetInput = {
    name: form.name.trim(),
    baseUrl: form.baseUrl.trim(),
    model: form.model.trim(),
    balancePath: form.balancePath.trim(),
  };
  const asNumber = (value: string) => Number(value.trim());
  if (form.contextWindowTokens.trim()) input.contextWindowTokens = asNumber(form.contextWindowTokens);
  if (form.maxOutputTokens.trim()) input.maxOutputTokens = asNumber(form.maxOutputTokens);
  if (form.balanceBaseUrl.trim()) input.balanceBaseUrl = form.balanceBaseUrl.trim();
  if (form.balanceAmountPointer.trim()) input.balanceAmountPointer = form.balanceAmountPointer.trim();
  if (form.balanceCurrencyPointer.trim()) input.balanceCurrencyPointer = form.balanceCurrencyPointer.trim();
  if (form.balanceCurrency.trim()) input.balanceCurrency = form.balanceCurrency.trim();
  if (form.balanceDivisor.trim()) input.balanceDivisor = asNumber(form.balanceDivisor);
  if (negate) input.balanceNegate = true;
  return input;
}

export default function LlmPresetPanel() {
  const [presets, setPresets] = useState<LlmProviderPreset[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [isSaving, setIsSaving] = useState(false);
  const [editingId, setEditingId] = useState<string | "new" | null>(null);
  const [form, setForm] = useState<PresetDraft>(EMPTY_FORM);
  const [negate, setNegate] = useState(false);
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
    setNegate(false);
    setError("");
    setFieldErrors({});
  };

  const startEdit = (p: LlmProviderPreset) => {
    setEditingId(p.id);
    setForm(draftFrom(p));
    setNegate(Boolean(p.balanceNegate));
    setError("");
    setFieldErrors({});
  };

  const cancelEdit = () => {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setNegate(false);
    setFieldErrors({});
  };

  const handleSave = async () => {
    const local = validatePreset(form);
    if (Object.keys(local).length > 0) {
      setFieldErrors(local);
      setError("");
      return;
    }
    setIsSaving(true);
    setError("");
    setFieldErrors({});
    try {
      const input = toInput(form, negate);
      if (editingId === "new") await createPreset(input);
      else if (editingId) await updatePreset(editingId, input);
      cancelEdit();
      await load();
    } catch (err) {
      if (err instanceof PresetError) {
        setError(err.message);
        if (err.field && err.field in EMPTY_FORM) {
          setFieldErrors({ [err.field]: err.message });
        }
      } else {
        setError("保存失败，请重试");
      }
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

  const inputCls = `w-full px-3 py-2 border border-fresh-sky rounded-lg text-sm shadow-input
    focus:outline-none focus:border-fresh-sky focus:shadow-input-focus
    disabled:opacity-50 disabled:cursor-not-allowed`;
  const btnCls = `px-3 py-1 text-xs rounded-full border border-fresh-sky text-fresh-sky
    hover:bg-frozen-water disabled:opacity-40 disabled:cursor-not-allowed
    transition-colors cursor-pointer`;

  const set = (key: keyof PresetDraft, value: string) => setForm({ ...form, [key]: value });

  const field = (
    id: string,
    key: keyof PresetDraft,
    label: string,
    placeholder?: string,
  ) => (
    <div>
      <div className="mb-1 flex items-center gap-1">
        <label htmlFor={id} className="text-xs text-stone-500">{label}</label>
        {key === "name" && <NameHint />}
      </div>
      <input
        id={id}
        value={form[key]}
        onChange={(e) => set(key, e.target.value)}
        placeholder={placeholder}
        disabled={isSaving}
        aria-invalid={fieldErrors[key] ? true : undefined}
        className={inputCls}
      />
      {fieldErrors[key] && (
        <p className="text-xs text-medium-violet-red mt-1" role="alert">{fieldErrors[key]}</p>
      )}
    </div>
  );

  return (
    <div>
      <h2 className="text-base font-semibold text-stone-800 mb-4">模型预置管理</h2>

      {error && <p className="text-xs font-medium bg-slime-lime text-ink rounded-md px-2 py-1 mb-3">{error}</p>}

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
                        余额接口：{p.balanceBaseUrl ? `${p.balanceBaseUrl} ` : ""}{p.balancePath ?? "不支持"}
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
                          className="px-3 py-1 text-xs rounded-full border border-fresh-sky text-fresh-sky
                            hover:bg-frozen-water disabled:opacity-40 disabled:cursor-not-allowed
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
              className="px-4 py-2 bg-fresh-sky text-ink text-sm font-medium rounded-lg
                hover:bg-fresh-sky-dark transition-colors cursor-pointer"
            >
              新增预置
            </button>
          ) : (
            <div className="space-y-3 max-w-md border border-stone-200 rounded-lg p-4">
              {field("preset-name", "name", "名称")}
              {field("preset-baseurl", "baseUrl", "Base URL", "https://api.example.com/v1")}
              {field("preset-model", "model", "Model")}
              {field("preset-balance-base", "balanceBaseUrl", "余额 Base URL（可选）", "https://billing.example")}
              {field("preset-balancepath", "balancePath", "余额接口路径（可选）", "/user/balance 或 /teams/{teamId}/balance，留空表示不支持")}
              {field("preset-context", "contextWindowTokens", "上下文窗口（可选）", "留空使用默认值")}
              {field("preset-output", "maxOutputTokens", "最大输出（可选）", "留空使用默认值")}
              <details className="text-sm">
                <summary className="cursor-pointer text-fresh-sky">高级</summary>
                <div className="space-y-3 mt-3">
                  {field("preset-amount-pointer", "balanceAmountPointer", "金额 JSON Pointer（可选）", "/total/val")}
                  {field("preset-currency-pointer", "balanceCurrencyPointer", "货币 JSON Pointer（可选）", "/currency")}
                  {field("preset-currency", "balanceCurrency", "货币（可选）", "没有货币字段时使用，例如 USD")}
                  {field("preset-divisor", "balanceDivisor", "除数（可选）", "金额单位换算，例如分填 100")}
                  <label className="flex items-center gap-2 text-xs text-stone-600">
                    <input
                      type="checkbox"
                      checked={negate}
                      onChange={(e) => setNegate(e.target.checked)}
                      disabled={isSaving}
                    />
                    金额取反（负值表示可用余额时勾选）
                  </label>
                </div>
              </details>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={handleSave}
                  disabled={isSaving || !form.name.trim() || !form.baseUrl.trim() || !form.model.trim()}
                  className="px-4 py-2 bg-fresh-sky text-ink text-sm font-medium rounded-lg
                    hover:bg-fresh-sky-dark disabled:opacity-40 disabled:cursor-not-allowed
                    transition-colors cursor-pointer"
                >
                  保存
                </button>
                <button
                  type="button"
                  onClick={cancelEdit}
                  disabled={isSaving}
                  className="px-4 py-2 border border-fresh-sky text-fresh-sky text-sm rounded-lg
                    hover:bg-frozen-water disabled:opacity-40 disabled:cursor-not-allowed
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
