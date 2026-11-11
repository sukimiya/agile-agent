// src/api/llmConfig.ts
import { apiFetch } from './client';

export interface LlmConfig {
  presetId: string;
  presetName: string;
  model: string;
  apiKeyMasked: string;
}

export interface Balance {
  currency: string;
  balance: string;
}

export class LlmConfigError extends Error {
  code: string;
  constructor(code: string, message: string) {
    super(message);
    this.code = code;
  }
}

async function bodyMessage(res: Response): Promise<string> {
  const err = await res.json().catch(() => null);
  return err?.message ?? res.statusText;
}

export async function fetchLlmConfig(): Promise<LlmConfig | null> {
  const res = await apiFetch('/api/llm-config');
  if (res.status === 404) return null;
  if (!res.ok) throw new LlmConfigError('UNKNOWN', '加载失败');
  return res.json();
}

export async function saveLlmConfig(presetId: string, apiKey: string): Promise<void> {
  const res = await apiFetch('/api/llm-config', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ presetId, apiKey }),
  });
  if (!res.ok) throw new LlmConfigError('SAVE_FAILED', await bodyMessage(res));
}

export async function deleteLlmConfig(): Promise<void> {
  const res = await apiFetch('/api/llm-config', { method: 'DELETE' });
  if (res.status === 404) return;
  if (!res.ok) throw new LlmConfigError('DELETE_FAILED', '删除失败');
}

export async function fetchBalance(): Promise<Balance[]> {
  const res = await apiFetch('/api/llm-config/balance');
  if (res.ok) return res.json();
  if (res.status === 428) {
    const err = await res.json().catch(() => null);
    throw new LlmConfigError('NOT_CONFIGURED', err?.message ?? '请先在 LLM 设置中配置模型和 API Key');
  }
  if (res.status === 404) throw new LlmConfigError('NOT_SUPPORTED', '当前厂商不支持余额查询');
  if (res.status === 502) throw new LlmConfigError('PROVIDER_ERROR', await bodyMessage(res));
  throw new LlmConfigError('UNKNOWN', '加载失败');
}
