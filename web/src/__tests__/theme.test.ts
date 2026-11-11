import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  THEME_STORAGE_KEY,
  applyTheme,
  cycleTheme,
  persistTheme,
  readStoredTheme,
  resolveTheme,
  subscribeSystemTheme,
} from '../theme';

function installMatchMedia(matches: boolean) {
  const listeners = new Set<() => void>();
  const query = {
    matches,
    media: '(prefers-color-scheme: dark)',
    addEventListener: (_type: string, listener: () => void) => listeners.add(listener),
    removeEventListener: (_type: string, listener: () => void) => listeners.delete(listener),
    setMatches(next: boolean) {
      query.matches = next;
      listeners.forEach((listener) => listener());
    },
  };
  vi.stubGlobal('matchMedia', () => query);
  return query;
}

describe('theme', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
    delete document.documentElement.dataset.theme;
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.documentElement.classList.remove('dark');
  });

  it('defaults to auto when nothing is saved', () => {
    expect(readStoredTheme()).toBe('auto');
  });

  it('ignores an unknown stored value', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'sepia');
    expect(readStoredTheme()).toBe('auto');
  });

  it('auto follows the system preference', () => {
    expect(resolveTheme('auto', true)).toBe('dark');
    expect(resolveTheme('auto', false)).toBe('light');
    expect(resolveTheme('light', true)).toBe('light');
    expect(resolveTheme('dark', false)).toBe('dark');
  });

  it('persists a manual override and applies it over the system preference', () => {
    installMatchMedia(false);
    persistTheme('dark');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
    expect(readStoredTheme()).toBe('dark');
    expect(applyTheme('dark', false)).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(document.documentElement.dataset.theme).toBe('dark');
  });

  it('updates with the OS while auto is selected and ignores it after a manual choice', () => {
    const query = installMatchMedia(false);
    const stop = subscribeSystemTheme((() => query) as unknown as typeof window.matchMedia);
    expect(document.documentElement.classList.contains('dark')).toBe(false);

    query.setMatches(true);
    expect(document.documentElement.classList.contains('dark')).toBe(true);

    persistTheme('light');
    applyTheme('light', true);
    query.setMatches(true);
    expect(document.documentElement.classList.contains('dark')).toBe(false);

    stop();
    query.setMatches(false);
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('cycles light, dark, and auto', () => {
    expect(cycleTheme('light')).toBe('dark');
    expect(cycleTheme('dark')).toBe('auto');
    expect(cycleTheme('auto')).toBe('light');
  });
});
