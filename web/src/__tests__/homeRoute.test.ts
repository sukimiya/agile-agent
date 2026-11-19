import { describe, expect, it } from 'vitest';
import { isHomePath } from '../homeRoute';

describe('isHomePath', () => {
  it('treats / and /home as the landing page, not login or chat', () => {
    expect(isHomePath('/')).toBe(true);
    expect(isHomePath('/home')).toBe(true);
    expect(isHomePath('/home/')).toBe(true);
    expect(isHomePath('/chat')).toBe(false);
    expect(isHomePath('/login/callback')).toBe(false);
    expect(isHomePath('/homepage')).toBe(false);
  });
});
