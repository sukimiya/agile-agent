import { describe, it, expect, vi, beforeEach } from 'vitest';
import { uploadKnowledge, listKnowledge, approveKnowledge, rejectKnowledge } from '../../api/knowledge';

describe('knowledge api', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  describe('uploadKnowledge', () => {
    it('posts multipart with the file and optional title, returns id + summary + status', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => ({
        ok: true,
        status: 200,
        json: async () => ({ id: 'doc-1', summary: '摘要内容', status: 'PENDING' }),
      })));

      const file = new File(['# hi'], 'a.md', { type: 'text/markdown' });
      const result = await uploadKnowledge(file, '标题');

      expect(result).toEqual({ id: 'doc-1', summary: '摘要内容', status: 'PENDING' });
      const call = (globalThis.fetch as any).mock.calls[0];
      expect(call[0]).toBe('/api/knowledge/upload');
      expect(call[1].method).toBe('POST');
      const body = call[1].body as FormData;
      expect(body.get('file')).toBeInstanceOf(File);
      expect(body.get('title')).toBe('标题');
    });

    it('omits the title field when none is given', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => ({
        ok: true,
        status: 200,
        json: async () => ({ id: 'doc-1', summary: '' }),
      })));

      const file = new File(['# hi'], 'a.md', { type: 'text/markdown' });
      await uploadKnowledge(file);

      const call = (globalThis.fetch as any).mock.calls[0];
      const body = call[1].body as FormData;
      expect(body.has('title')).toBe(false);
    });

    it('throws KnowledgeError with code/reason on 4xx response', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => ({
        ok: false,
        status: 400,
        json: async () => ({ code: 'BAD_TYPE', reason: '仅支持文本文档' }),
      })));

      const file = new File(['x'], 'a.pdf', { type: 'application/pdf' });
      await expect(uploadKnowledge(file)).rejects.toMatchObject({
        code: 'BAD_TYPE',
        reason: '仅支持文本文档',
      });
    });
  });

  describe('listKnowledge', () => {
    it('fetches with the status query param', async () => {
      const rows = [{
        id: 'doc-1', title: 'a', uploaderUserId: 'u1', summary: 's', uploadedAt: '2026-07-28T00:00:00Z',
        status: 'PENDING', downloadUrl: '/api/knowledge/doc-1/download', scopeType: 'GLOBAL',
        scopeName: '全局', tenantId: null, orgId: null,
      }];
      vi.stubGlobal('fetch', vi.fn(async () => ({
        ok: true,
        status: 200,
        json: async () => rows,
      })));

      const result = await listKnowledge('PENDING');

      expect(result).toEqual(rows);
      const call = (globalThis.fetch as any).mock.calls[0];
      expect(call[0]).toBe('/api/knowledge?status=PENDING');
    });
  });

  describe('approveKnowledge / rejectKnowledge', () => {
    it('approveKnowledge posts to the approve endpoint', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => ({}) })));

      await approveKnowledge('doc-1');

      const call = (globalThis.fetch as any).mock.calls[0];
      expect(call[0]).toBe('/api/knowledge/doc-1/approve');
      expect(call[1].method).toBe('POST');
    });

    it('rejectKnowledge posts to the reject endpoint', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => ({}) })));

      await rejectKnowledge('doc-1');

      const call = (globalThis.fetch as any).mock.calls[0];
      expect(call[0]).toBe('/api/knowledge/doc-1/reject');
      expect(call[1].method).toBe('POST');
    });

    it('throws a friendly KnowledgeError on 409 (already processed)', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 409, json: async () => ({}) })));

      await expect(approveKnowledge('doc-1')).rejects.toMatchObject({
        reason: '该文档已被处理，请刷新列表',
      });
    });
  });
});
