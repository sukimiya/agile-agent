import { describe, it, expect, vi, beforeEach } from 'vitest';
import { listConversations, getConversationMessages } from '../../api/conversations';

describe('conversations api', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('listConversations returns the parsed list', async () => {
    const fake = [{ id: 's1', title: '标题', updatedAt: '2026-07-08T00:00:00' }];
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => fake })));

    const result = await listConversations();

    expect(result).toEqual(fake);
  });

  it('getConversationMessages returns null on 404', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 404 })));

    const result = await getConversationMessages('missing');

    expect(result).toBeNull();
  });

  it('getConversationMessages returns the parsed messages on success', async () => {
    const fake = [{ id: 'msg-0', role: 'user', content: 'hi' }];
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => fake })));

    const result = await getConversationMessages('s1');

    expect(result).toEqual(fake);
  });

  it('listConversations throws on a non-ok response', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 500 })));

    await expect(listConversations()).rejects.toThrow();
  });

  it('getConversationMessages throws on a non-404 non-ok response', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 500 })));

    await expect(getConversationMessages('s1')).rejects.toThrow();
  });
});
