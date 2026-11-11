import { apiFetch } from './client';

export interface UploadedRef {
  sessionId: string;
  name: string;
  size: number;
}

export class UploadError extends Error {
  code: string;
  reason: string;
  constructor(code: string, reason: string) {
    super(`${code}: ${reason}`);
    this.code = code;
    this.reason = reason;
  }
}

export async function uploadFiles(
  sessionId: string,
  files: File[],
): Promise<UploadedRef[]> {
  const form = new FormData();
  form.set('sessionId', sessionId);
  for (const f of files) form.append('files', f);

  const res = await apiFetch('/api/files/upload', {
    method: 'POST',
    body: form,
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ code: 'UNKNOWN', reason: res.statusText }));
    throw new UploadError(err.code ?? 'UNKNOWN', err.reason ?? '');
  }
  return res.json();
}
