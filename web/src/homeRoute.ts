function stripTrailingSlash(pathname: string): string {
  if (pathname.length > 1 && pathname.endsWith('/')) return pathname.slice(0, -1);
  return pathname || '/';
}

/** `/` and `/home` are the public landing page. Login and chat live at `/chat`. */
export function isHomePath(pathname: string): boolean {
  const path = stripTrailingSlash(pathname);
  return path === '/' || path === '/home';
}

export const CHAT_PATH = '/chat';
