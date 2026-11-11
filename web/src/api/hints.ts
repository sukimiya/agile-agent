import { apiFetch } from './client';

export interface Hint {
  id: string;
  content: string;
  createdBySubject: string;
  createdAt: string;
}

export class HintError extends Error {
  code: string;
  reason: string;
  constructor(code: string, reason: string) {
    super(`${code}: ${reason}`);
    this.code = code;
    this.reason = reason;
  }
}

async function toError(res: Response): Promise<HintError> {
  if (res.status === 403) {
    return new HintError('FORBIDDEN', '仅管理员可操作');
  }
  const err = await res.json().catch(() => ({ code: 'UNKNOWN', reason: res.statusText }));
  return new HintError(err.code ?? 'UNKNOWN', err.reason ?? '');
}

export async function listHints(): Promise<Hint[]> {
  const res = await apiFetch('/api/chat/hints');
  if (!res.ok) throw await toError(res);
  return res.json();
}

export async function createHint(content: string): Promise<Hint> {
  const res = await apiFetch('/api/chat/hints', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content }),
  });
  if (!res.ok) throw await toError(res);
  return res.json();
}

export async function updateHint(id: string, content: string): Promise<void> {
  const res = await apiFetch(`/api/chat/hints/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content }),
  });
  if (!res.ok) throw await toError(res);
}

export async function deleteHint(id: string): Promise<void> {
  const res = await apiFetch(`/api/chat/hints/${id}`, { method: 'DELETE' });
  if (!res.ok) throw await toError(res);
}
