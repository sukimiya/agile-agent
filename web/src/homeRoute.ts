/** True only for the public landing page. `/` stays the existing login and chat app. */
export function isHomePath(pathname: string): boolean {
  const path = pathname.length > 1 && pathname.endsWith('/') ? pathname.slice(0, -1) : pathname;
  return path === '/home';
}
