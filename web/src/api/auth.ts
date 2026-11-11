import { apiFetch, apiUrl, clearToken, setToken } from './client';

export interface CurrentUser {
  userId: string;
  name: string;
  subject: string;
  displayName: string;
  tenantId: string | null;
  tenantName: string | null;
  primaryOrgId: string | null;
  primaryOrgName: string | null;
  roles: string[];
  platformAdmin: boolean;
  tenantAdmin: boolean;
}

export function authorizeUrl(): string {
  return apiUrl('/api/auth/oauth2/authorize');
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  const res = await apiFetch('/api/auth/me');
  if (!res.ok) throw new Error('failed to fetch current user');
  const data = await res.json();
  return {
    userId: data.userId ?? '',
    name: data.name ?? data.displayName ?? '',
    subject: data.subject ?? '',
    displayName: data.displayName ?? data.name ?? '',
    tenantId: data.tenantId ?? null,
    tenantName: data.tenantName ?? null,
    primaryOrgId: data.primaryOrgId ?? null,
    primaryOrgName: data.primaryOrgName ?? null,
    roles: Array.isArray(data.roles) ? data.roles : [],
    platformAdmin: data.platformAdmin === true,
    tenantAdmin: data.tenantAdmin === true,
  };
}

export async function logout(): Promise<void> {
  try {
    await apiFetch('/api/auth/logout', { method: 'POST' });
  } finally {
    clearToken();
  }
}

export function storeSessionToken(token: string): void {
  setToken(token);
}
