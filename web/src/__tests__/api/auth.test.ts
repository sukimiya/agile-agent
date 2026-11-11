import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fetchCurrentUser, logout } from '../../api/auth';
import { getToken, setToken } from '../../api/client';

describe('auth', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('fetchCurrentUser calls /api/auth/me with the stored token and reads admin flags', async () => {
    setToken('app-tok');
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({
        userId: 'u1',
        name: '周琳琳',
        subject: 'u1',
        displayName: '周琳琳',
        tenantId: 'tenant-a',
        roles: ['tenant_admin'],
        platformAdmin: false,
        tenantAdmin: true,
      }),
    })));

    const user = await fetchCurrentUser();

    expect(user.name).toBe('周琳琳');
    expect(user.tenantAdmin).toBe(true);
    expect(user.platformAdmin).toBe(false);
    expect(user.roles).toEqual(['tenant_admin']);
    const call = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(call[0]).toBe('/api/auth/me');
    expect(new Headers(call[1].headers).get('Authorization')).toBe('Bearer app-tok');
  });

  it('logout calls the backend then clears the local token', async () => {
    setToken('app-tok');
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 204 })));

    await logout();

    expect(getToken()).toBeNull();
    const call = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(call[0]).toBe('/api/auth/logout');
    expect(call[1].method).toBe('POST');
  });
});
