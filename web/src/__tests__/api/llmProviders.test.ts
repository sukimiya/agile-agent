import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fetchPresets, createPreset, updatePreset, deletePreset } from '../../api/llmProviders';

describe('llmProviders api', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('fetchPresets gets the list', async () => {
    const rows = [{ id: 'p1', name: 'Example', baseUrl: 'https://models.example/v1', model: 'example-chat', balancePath: '/user/balance' }];
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => rows })));

    const result = await fetchPresets();

    expect(result).toEqual(rows);
    const call = (globalThis.fetch as any).mock.calls[0];
    expect(call[0]).toBe('/api/llm-providers');
  });

  it('createPreset posts the input, blank balancePath becomes null', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => ({}) })));

    await createPreset({ name: 'Other', baseUrl: 'https://other.example/v1', model: 'other-chat', balancePath: '  ' });

    const call = (globalThis.fetch as any).mock.calls[0];
    expect(call[0]).toBe('/api/llm-providers');
    expect(call[1].method).toBe('POST');
    expect(JSON.parse(call[1].body)).toEqual({
      name: 'Other',
      baseUrl: 'https://other.example/v1',
      model: 'other-chat',
      balancePath: null,
    });
  });

  it('createPreset throws 仅管理员可操作 on 403', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 403 })));

    await expect(createPreset({ name: 'n', baseUrl: 'b', model: 'm' })).rejects.toMatchObject({
      code: 'FORBIDDEN', message: '仅管理员可操作',
    });
  });

  it('updatePreset puts to the preset id', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => ({}) })));

    await updatePreset('p1', { name: 'n', baseUrl: 'b', model: 'm', balancePath: '/user/balance' });

    const call = (globalThis.fetch as any).mock.calls[0];
    expect(call[0]).toBe('/api/llm-providers/p1');
    expect(call[1].method).toBe('PUT');
    expect(JSON.parse(call[1].body)).toEqual({ name: 'n', baseUrl: 'b', model: 'm', balancePath: '/user/balance' });
  });

  it('updatePreset surfaces the backend message on 400', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 400, json: async () => ({ message: 'name 不能为空' }) })));

    await expect(updatePreset('p1', { name: '', baseUrl: 'b', model: 'm' })).rejects.toMatchObject({
      message: 'name 不能为空',
    });
  });

  it('deletePreset deletes by id', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 204 })));

    await deletePreset('p1');

    const call = (globalThis.fetch as any).mock.calls[0];
    expect(call[0]).toBe('/api/llm-providers/p1');
    expect(call[1].method).toBe('DELETE');
  });
});
