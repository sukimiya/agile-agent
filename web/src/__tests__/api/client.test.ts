import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiFetch, getToken, setToken, clearToken, UnauthorizedError } from '../../api/client';

describe('client', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('setToken/getToken/clearToken round-trip through localStorage', () => {
    expect(getToken()).toBeNull();
    setToken('tok-1');
    expect(getToken()).toBe('tok-1');
    clearToken();
    expect(getToken()).toBeNull();
  });

  it('apiFetch attaches Authorization header when a token is stored', async () => {
    setToken('tok-1');
    vi.stubGlobal('fetch', vi.fn(async () => ({ status: 200, ok: true })));

    await apiFetch('/api/conversations');

    const call = (globalThis.fetch as any).mock.calls[0];
    expect(call[1].headers.get('Authorization')).toBe('Bearer tok-1');
  });

  it('apiFetch omits Authorization header when no token is stored', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ status: 200, ok: true })));

    await apiFetch('/api/conversations');

    const call = (globalThis.fetch as any).mock.calls[0];
    expect(call[1].headers.get('Authorization')).toBeNull();
  });

  it('apiFetch clears the token and throws UnauthorizedError on 401', async () => {
    setToken('tok-1');
    vi.stubGlobal('fetch', vi.fn(async () => ({ status: 401, ok: false })));

    await expect(apiFetch('/api/conversations')).rejects.toBeInstanceOf(UnauthorizedError);
    expect(getToken()).toBeNull();
  });
});
