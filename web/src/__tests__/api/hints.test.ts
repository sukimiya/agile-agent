import { describe, it, expect, vi, beforeEach } from 'vitest';
import { listHints, createHint, updateHint, deleteHint } from '../../api/hints';

describe('hints api', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  describe('listHints', () => {
    it('fetches the hints list', async () => {
      const rows = [{ id: 'h1', content: '有多少个客户？', createdBySubject: 'u1', createdAt: '2026-07-29T00:00:00Z' }];
      vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => rows })));

      const result = await listHints();

      expect(result).toEqual(rows);
      const call = (globalThis.fetch as any).mock.calls[0];
      expect(call[0]).toBe('/api/chat/hints');
    });
  });

  describe('createHint', () => {
    it('posts the content and returns the created hint', async () => {
      const created = { id: 'h1', content: '新问题', createdBySubject: 'u1', createdAt: '2026-07-29T00:00:00Z' };
      vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => created })));

      const result = await createHint('新问题');

      expect(result).toEqual(created);
      const call = (globalThis.fetch as any).mock.calls[0];
      expect(call[0]).toBe('/api/chat/hints');
      expect(call[1].method).toBe('POST');
      expect(JSON.parse(call[1].body)).toEqual({ content: '新问题' });
    });

    it('throws a friendly HintError on 403', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 403 })));

      await expect(createHint('新问题')).rejects.toMatchObject({ reason: '仅管理员可操作' });
    });
  });

  describe('updateHint', () => {
    it('puts the content to the hint id', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => ({}) })));

      await updateHint('h1', '改过的问题');

      const call = (globalThis.fetch as any).mock.calls[0];
      expect(call[0]).toBe('/api/chat/hints/h1');
      expect(call[1].method).toBe('PUT');
      expect(JSON.parse(call[1].body)).toEqual({ content: '改过的问题' });
    });
  });

  describe('deleteHint', () => {
    it('deletes the hint by id', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 204 })));

      await deleteHint('h1');

      const call = (globalThis.fetch as any).mock.calls[0];
      expect(call[0]).toBe('/api/chat/hints/h1');
      expect(call[1].method).toBe('DELETE');
    });
  });
});
