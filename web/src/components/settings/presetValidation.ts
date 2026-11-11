export interface PresetDraft {
  name: string;
  baseUrl: string;
  model: string;
  balancePath: string;
  balanceBaseUrl: string;
  contextWindowTokens: string;
  maxOutputTokens: string;
  balanceAmountPointer: string;
  balanceCurrencyPointer: string;
  balanceCurrency: string;
  balanceDivisor: string;
}

export type FieldErrors = Partial<Record<keyof PresetDraft, string>>;

function absoluteHttps(value: string): string | null {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return '必须是完整的 https 地址';
  }
  if (url.protocol !== 'https:') return '必须使用 https';
  if (url.username || url.password) return '不能包含用户名或密码';
  if (url.search || url.hash) return '不能包含查询参数或片段';
  return null;
}

/** Client-side checks that match the preset rules the server enforces. */
export function validatePreset(draft: PresetDraft): FieldErrors {
  const errors: FieldErrors = {};
  if (!draft.name.trim()) errors.name = '名称不能为空';
  if (!draft.baseUrl.trim()) errors.baseUrl = 'Base URL 不能为空';
  else {
    const base = absoluteHttps(draft.baseUrl.trim());
    if (base) errors.baseUrl = base;
  }
  if (!draft.model.trim()) errors.model = 'Model 不能为空';

  const path = draft.balancePath.trim();
  if (path && (path.includes('://') || path.startsWith('//'))) {
    errors.balancePath = '余额路径必须是相对路径，不能写成完整网址';
  }

  const balanceBase = draft.balanceBaseUrl.trim();
  if (balanceBase) {
    const message = absoluteHttps(balanceBase);
    if (message) errors.balanceBaseUrl = message;
  }

  const windowText = draft.contextWindowTokens.trim();
  const outputText = draft.maxOutputTokens.trim();
  const windowTokens = windowText ? Number(windowText) : null;
  const outputTokens = outputText ? Number(outputText) : null;
  if (windowText && (!Number.isInteger(windowTokens) || (windowTokens ?? 0) < 1024)) {
    errors.contextWindowTokens = '上下文窗口至少为 1024';
  }
  if (outputText && (!Number.isInteger(outputTokens) || (outputTokens ?? 0) < 1)) {
    errors.maxOutputTokens = '最大输出至少为 1';
  } else if (windowTokens != null && outputTokens != null && outputTokens >= windowTokens) {
    errors.maxOutputTokens = '最大输出必须小于上下文窗口';
  }

  if (draft.balanceAmountPointer.trim() && !draft.balanceAmountPointer.trim().startsWith('/')) {
    errors.balanceAmountPointer = 'JSON Pointer 必须以 / 开头';
  }
  if (draft.balanceCurrencyPointer.trim() && !draft.balanceCurrencyPointer.trim().startsWith('/')) {
    errors.balanceCurrencyPointer = 'JSON Pointer 必须以 / 开头';
  }
  const divisor = draft.balanceDivisor.trim();
  if (divisor && (!Number.isInteger(Number(divisor)) || Number(divisor) < 1)) {
    errors.balanceDivisor = '除数至少为 1';
  }
  return errors;
}
