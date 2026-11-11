import { describe, expect, it } from 'vitest';
import { isHomePath } from '../homeRoute';

describe('isHomePath', () => {
  it('matches only the public landing route', () => {
    expect(isHomePath('/home')).toBe(true);
    expect(isHomePath('/home/')).toBe(true);
    expect(isHomePath('/')).toBe(false);
    expect(isHomePath('/login/callback')).toBe(false);
    expect(isHomePath('/homepage')).toBe(false);
  });
});
