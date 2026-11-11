import { apiFetch } from './client';

export type KnowledgeStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** Matches `KnowledgeController.KnowledgeDocumentSummary`. */
export interface KnowledgeDoc {
  id: string;
  title: string;
  uploaderUserId: string;
  summary: string;
  uploadedAt: string;
  status: KnowledgeStatus;
  downloadUrl: string;
  scopeType: string;
  scopeName: string | null;
  tenantId: string | null;
  orgId: string | null;
}

export class KnowledgeError extends Error {
  code: string;
  reason: string;
  constructor(code: string, reason: string) {
    super(`${code}: ${reason}`);
    this.code = code;
    this.reason = reason;
  }
}

async function toError(res: Response): Promise<KnowledgeError> {
  if (res.status === 409) {
    return new KnowledgeError('CONFLICT', '该文档已被处理，请刷新列表');
  }
  const err = await res.json().catch(() => ({ code: 'UNKNOWN', reason: res.statusText }));
  return new KnowledgeError(err.code ?? 'UNKNOWN', err.reason ?? err.message ?? '');
}

export async function uploadKnowledge(
  file: File,
  title?: string,
): Promise<{ id: string; summary: string; status: KnowledgeStatus }> {
  const form = new FormData();
  form.set('file', file);
  if (title) form.set('title', title);

  const res = await apiFetch('/api/knowledge/upload', {
    method: 'POST',
    body: form,
  });
  if (!res.ok) throw await toError(res);
  return res.json();
}

export async function listKnowledge(status: KnowledgeStatus): Promise<KnowledgeDoc[]> {
  const res = await apiFetch(`/api/knowledge?status=${status}`);
  if (!res.ok) throw await toError(res);
  return res.json();
}

export async function approveKnowledge(id: string): Promise<void> {
  const res = await apiFetch(`/api/knowledge/${id}/approve`, { method: 'POST' });
  if (!res.ok) throw await toError(res);
}

export async function rejectKnowledge(id: string): Promise<void> {
  const res = await apiFetch(`/api/knowledge/${id}/reject`, { method: 'POST' });
  if (!res.ok) throw await toError(res);
}
