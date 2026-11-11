import { apiFetch } from './client';

export interface LlmProviderPreset {
  id: string;
  name: string;
  baseUrl: string;
  model: string;
  balancePath: string | null;
  createdBySubject?: string;
  createdAt?: string;
  updatedAt?: string;
  contextWindowTokens?: number | null;
  maxOutputTokens?: number | null;
  balanceBaseUrl?: string | null;
  balanceAmountPointer?: string | null;
  balanceCurrencyPointer?: string | null;
  balanceCurrency?: string | null;
  balanceDivisor?: number | null;
  balanceNegate?: boolean | null;
}

export interface PresetInput {
  name: string;
  baseUrl: string;
  model: string;
  balancePath?: string;
  contextWindowTokens?: number | null;
  maxOutputTokens?: number | null;
  balanceBaseUrl?: string;
  balanceAmountPointer?: string;
  balanceCurrencyPointer?: string;
  balanceCurrency?: string;
  balanceDivisor?: number | null;
  balanceNegate?: boolean;
}

export class PresetError extends Error {
  code: string;
  field?: string;
  constructor(code: string, message: string, field?: string) {
    super(message);
    this.code = code;
    this.field = field;
  }
}

async function toError(res: Response): Promise<PresetError> {
  if (res.status === 403) return new PresetError('FORBIDDEN', '仅管理员可操作');
  const err = await res.json().catch(() => null);
  return new PresetError('UNKNOWN', err?.message ?? res.statusText, err?.field);
}

function textOrNull(value: string | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

function body(input: PresetInput) {
  const payload: Record<string, unknown> = {
    name: input.name,
    baseUrl: input.baseUrl,
    model: input.model,
    balancePath: textOrNull(input.balancePath),
  };
  const extras: Record<string, unknown> = {
    contextWindowTokens: input.contextWindowTokens ?? undefined,
    maxOutputTokens: input.maxOutputTokens ?? undefined,
    balanceBaseUrl: textOrNull(input.balanceBaseUrl) ?? undefined,
    balanceAmountPointer: textOrNull(input.balanceAmountPointer) ?? undefined,
    balanceCurrencyPointer: textOrNull(input.balanceCurrencyPointer) ?? undefined,
    balanceCurrency: textOrNull(input.balanceCurrency) ?? undefined,
    balanceDivisor: input.balanceDivisor ?? undefined,
    balanceNegate: input.balanceNegate ? true : undefined,
  };
  for (const [key, value] of Object.entries(extras)) {
    if (value !== undefined) payload[key] = value;
  }
  return payload;
}

export async function fetchPresets(): Promise<LlmProviderPreset[]> {
  const res = await apiFetch('/api/llm-providers');
  if (!res.ok) throw await toError(res);
  return res.json();
}

export async function createPreset(input: PresetInput): Promise<void> {
  const res = await apiFetch('/api/llm-providers', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body(input)),
  });
  if (!res.ok) throw await toError(res);
}

export async function updatePreset(id: string, input: PresetInput): Promise<void> {
  const res = await apiFetch(`/api/llm-providers/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body(input)),
  });
  if (!res.ok) throw await toError(res);
}

export async function deletePreset(id: string): Promise<void> {
  const res = await apiFetch(`/api/llm-providers/${id}`, { method: 'DELETE' });
  if (!res.ok) throw await toError(res);
}
