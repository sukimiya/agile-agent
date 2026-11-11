export const THEME_STORAGE_KEY = 'theme-mode';

export type ThemeMode = 'light' | 'dark' | 'auto';
export type ResolvedTheme = 'light' | 'dark';

const MODES: ThemeMode[] = ['light', 'dark', 'auto'];

export function isThemeMode(value: string | null): value is ThemeMode {
  return value === 'light' || value === 'dark' || value === 'auto';
}

/** Saved choice, or auto when nothing valid is stored. */
export function readStoredTheme(storage: Pick<Storage, 'getItem'> = localStorage): ThemeMode {
  const stored = storage.getItem(THEME_STORAGE_KEY);
  return isThemeMode(stored) ? stored : 'auto';
}

export function systemPrefersDark(match: (query: string) => { matches: boolean } = defaultMatchMedia): boolean {
  return match('(prefers-color-scheme: dark)').matches;
}

function defaultMatchMedia(query: string): { matches: boolean } {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return { matches: false };
  return window.matchMedia(query);
}

export function resolveTheme(mode: ThemeMode, prefersDark: boolean): ResolvedTheme {
  if (mode === 'auto') return prefersDark ? 'dark' : 'light';
  return mode;
}

/** light → dark → auto → light. */
export function cycleTheme(mode: ThemeMode): ThemeMode {
  const index = MODES.indexOf(mode);
  return MODES[(index + 1) % MODES.length];
}

export function persistTheme(mode: ThemeMode, storage: Pick<Storage, 'setItem'> = localStorage): void {
  storage.setItem(THEME_STORAGE_KEY, mode);
}

export function applyTheme(mode: ThemeMode, prefersDark: boolean): ResolvedTheme {
  const resolved = resolveTheme(mode, prefersDark);
  const root = document.documentElement;
  root.classList.toggle('dark', resolved === 'dark');
  root.dataset.theme = resolved;
  root.style.colorScheme = resolved;
  return resolved;
}

/** Follow OS changes only while the saved choice is auto. */
export function subscribeSystemTheme(
  match: (query: string) => MediaQueryList = (query) => defaultMatchMedia(query) as MediaQueryList,
): () => void {
  const query = match('(prefers-color-scheme: dark)');
  if (typeof query.addEventListener !== 'function') return () => {};
  const sync = () => {
    const mode = readStoredTheme();
    if (mode === 'auto') applyTheme('auto', query.matches);
  };
  sync();
  query.addEventListener('change', sync);
  return () => query.removeEventListener('change', sync);
}
