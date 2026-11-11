import '@testing-library/jest-dom';
import { afterEach, beforeEach } from 'vitest';
import { resetOAuthCallbackState } from './auth/callback';

// Polyfill localStorage if not available (for jsdom compatibility)
if (typeof globalThis.localStorage === 'undefined') {
  const store: Record<string, string> = {};

  Object.defineProperty(globalThis, 'localStorage', {
    value: {
      getItem: (key: string) => store[key] ?? null,
      setItem: (key: string, value: string) => {
        store[key] = value.toString();
      },
      removeItem: (key: string) => {
        delete store[key];
      },
      clear: () => {
        for (const key in store) {
          delete store[key];
        }
      },
      key: (index: number) => {
        const keys = Object.keys(store);
        return keys[index] ?? null;
      },
      get length() {
        return Object.keys(store).length;
      },
    },
    writable: true,
  });
}

// Polyfill scrollIntoView if not available
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = function() {
    // No-op for testing
  };
}

beforeEach(() => {
  resetOAuthCallbackState();
});

// Ensure localStorage is cleared after each test
afterEach(() => {
  if (typeof localStorage !== 'undefined') {
    localStorage.clear();
  }
});
