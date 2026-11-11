import { describe, it, expect, vi, beforeEach } from 'vitest';
import { uploadFiles } from '../../api/upload';

describe('uploadFiles', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('posts multipart with sessionId and returns refs', async () => {
    const fakeRefs = [{ sessionId: 'conv-1', name: 'a.png', size: 100 }];
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => fakeRefs,
    })));

    const file = new File(['x'], 'a.png', { type: 'image/png' });
    const refs = await uploadFiles('conv-1', [file]);
    expect(refs).toEqual(fakeRefs);

    const call = (globalThis.fetch as any).mock.calls[0];
    expect(call[0]).toBe('/api/files/upload');
    expect(call[1].method).toBe('POST');
    const body = call[1].body as FormData;
    expect(body.get('sessionId')).toBe('conv-1');
    expect(body.getAll('files')).toHaveLength(1);
  });

  it('throws UploadError with code on 4xx response', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: false,
      status: 400,
      json: async () => ({ code: 'FILE_REJECTED', reason: 'too big' }),
    })));
    const file = new File(['x'], 'a.png', { type: 'image/png' });
    await expect(uploadFiles('conv-1', [file])).rejects.toMatchObject({
      code: 'FILE_REJECTED',
    });
  });
});
