export interface OAuthCallback {
  token: string | null;
  error: string | null;
}

let pending: OAuthCallback | null = null;

/**
 * Reads `#token` or `#error` on `/login/callback` once per page load, then
 * replaces the history entry so the token is not left in the address bar.
 * Later calls return the same result so React StrictMode remounts still see it.
 */
export function consumeOAuthCallback(): OAuthCallback {
  if (pending) return pending;
  if (window.location.pathname !== '/login/callback') {
    return { token: null, error: null };
  }
  const params = new URLSearchParams(window.location.hash.replace(/^#/, ''));
  const token = params.get('token');
  const error = params.get('error') ?? (token ? null : 'missing_token');
  const url = new URL(window.location.href);
  url.hash = '';
  url.pathname = '/';
  window.history.replaceState(null, '', `${url.pathname}${url.search}`);
  pending = { token, error };
  return pending;
}

export function resetOAuthCallbackState(): void {
  pending = null;
}

export function oauthErrorMessage(code: string | null): string {
  if (code === 'provider_unavailable') return '登录服务暂时不可用';
  if (code === 'missing_token') return '登录回调无效';
  return '登录失败，请重试';
}
