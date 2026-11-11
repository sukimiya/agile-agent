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
}

export interface PresetInput {
  name: string;
  baseUrl: string;
  model: string;
  balancePath?: string;
}

export class PresetError extends Error {
  code: string;
  constructor(code: string, message: string) {
    super(message);
    this.code = code;
  }
}

async function toError(res: Response): Promise<PresetError> {
  if (res.status === 403) return new PresetError('FORBIDDEN', '仅管理员可操作');
  const err = await res.json().catch(() => null);
  return new PresetError('UNKNOWN', err?.message ?? res.statusText);
}

function body(input: PresetInput) {
  return {
    name: input.name,
    baseUrl: input.baseUrl,
    model: input.model,
    balancePath: input.balancePath?.trim() ? input.balancePath.trim() : null,
  };
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
