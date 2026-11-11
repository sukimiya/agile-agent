// src/__tests__/api/llmConfig.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  fetchLlmConfig, saveLlmConfig, deleteLlmConfig, fetchBalance, LlmConfigError,
} from '../../api/llmConfig';

describe('llmConfig api', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  describe('fetchLlmConfig', () => {
    it('returns the config on 200', async () => {
      const cfg = { presetId: 'p1', presetName: 'Example', model: 'example-chat', apiKeyMasked: 'sk-****abcd' };
      vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => cfg })));

      const result = await fetchLlmConfig();

      expect(result).toEqual(cfg);
      const call = (globalThis.fetch as any).mock.calls[0];
      expect(call[0]).toBe('/api/llm-config');
    });

    it('returns null on 404', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 404 })));

      await expect(fetchLlmConfig()).resolves.toBeNull();
    });
  });

  describe('saveLlmConfig', () => {
    it('puts presetId and apiKey', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => ({}) })));

      await saveLlmConfig('p1', 'sk-test');

      const call = (globalThis.fetch as any).mock.calls[0];
      expect(call[0]).toBe('/api/llm-config');
      expect(call[1].method).toBe('PUT');
      expect(JSON.parse(call[1].body)).toEqual({ presetId: 'p1', apiKey: 'sk-test' });
    });

    it('sends a balance key and team id only when they are filled in', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => ({}) })));

      await saveLlmConfig('p1', 'sk-test', { balanceApiKey: '  ', balanceTeamId: ' team-1 ' });

      const call = (globalThis.fetch as any).mock.calls[0];
      expect(JSON.parse(call[1].body)).toEqual({ presetId: 'p1', apiKey: 'sk-test', balanceTeamId: 'team-1' });
    });

    it('throws the backend message on 400', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => ({
        ok: false, status: 400, json: async () => ({ message: '连通性校验失败: 401' }),
      })));

      await expect(saveLlmConfig('p1', 'bad')).rejects.toMatchObject({
        code: 'SAVE_FAILED', message: '连通性校验失败: 401',
      });
    });
  });

  describe('deleteLlmConfig', () => {
    it('sends DELETE and treats 404 as success', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 404 })));

      await expect(deleteLlmConfig()).resolves.toBeUndefined();

      const call = (globalThis.fetch as any).mock.calls[0];
      expect(call[0]).toBe('/api/llm-config');
      expect(call[1].method).toBe('DELETE');
    });
  });

  describe('fetchBalance', () => {
    it('returns balances on 200', async () => {
      const rows = [{ currency: 'CNY', balance: '12.34' }];
      vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => rows })));

      const result = await fetchBalance();

      expect(result).toEqual(rows);
      const call = (globalThis.fetch as any).mock.calls[0];
      expect(call[0]).toBe('/api/llm-config/balance');
    });

    it('throws NOT_CONFIGURED on 428 LLM_CONFIG_REQUIRED', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 428, json: async () => ({ code: 'LLM_CONFIG_REQUIRED', message: 'Current tenant has no LLM configuration' }) })));

      await expect(fetchBalance()).rejects.toMatchObject({ code: 'NOT_CONFIGURED' });
    });

    it('throws NOT_SUPPORTED on 404', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 404, json: async () => ({ message: '该厂商不支持余额查询' }) })));

      await expect(fetchBalance()).rejects.toMatchObject({ code: 'NOT_SUPPORTED' });
    });

    it('throws PROVIDER_ERROR with the backend message on 502', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 502, json: async () => ({ message: '厂商接口报错' }) })));

      await expect(fetchBalance()).rejects.toMatchObject({ code: 'PROVIDER_ERROR', message: '厂商接口报错' });
    });

    it('is an instance of LlmConfigError', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 428, json: async () => ({}) })));

      await expect(fetchBalance()).rejects.toBeInstanceOf(LlmConfigError);
    });
  });
});
