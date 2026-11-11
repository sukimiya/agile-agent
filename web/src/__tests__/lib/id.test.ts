import { describe, it, expect } from 'vitest';
import { conversationId, isCanonicalUuid } from '../../lib/id';

describe('conversation ids', () => {
  it('generates a lowercase canonical UUID', () => {
    const id = conversationId();
    expect(isCanonicalUuid(id)).toBe(true);
    expect(id).toBe(id.toLowerCase());
  });

  it('rejects uppercase and non-uuid ids', () => {
    expect(isCanonicalUuid('AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA')).toBe(false);
    expect(isCanonicalUuid('conv-old')).toBe(false);
    expect(isCanonicalUuid(null)).toBe(false);
  });
});
