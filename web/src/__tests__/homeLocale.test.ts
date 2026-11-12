import { beforeEach, describe, expect, it } from 'vitest';
import { readHomeLocale, writeHomeLocale } from '../homeCopy';

describe('home locale', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('defaults to English when nothing is saved', () => {
    expect(readHomeLocale()).toBe('en');
  });

  it('keeps a saved Chinese choice and ignores any other value', () => {
    writeHomeLocale('zh');
    expect(readHomeLocale()).toBe('zh');
    localStorage.setItem('home-locale', 'fr');
    expect(readHomeLocale()).toBe('en');
  });
});
