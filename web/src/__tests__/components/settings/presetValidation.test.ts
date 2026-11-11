import { describe, it, expect } from 'vitest';
import { validatePreset, type PresetDraft } from '../../../components/settings/presetValidation';

const valid: PresetDraft = {
  name: 'x.ai',
  baseUrl: 'https://api.x.ai/v1',
  model: 'grok-4.7',
  balancePath: '/v1/billing/teams/{teamId}/prepaid/balance',
  balanceBaseUrl: 'https://management-api.x.ai',
  contextWindowTokens: '',
  maxOutputTokens: '',
  balanceAmountPointer: '/total/val',
  balanceCurrencyPointer: '',
  balanceCurrency: 'USD',
  balanceDivisor: '100',
};

describe('validatePreset', () => {
  it('accepts a relative balance path and a separate https balance host', () => {
    expect(validatePreset(valid)).toEqual({});
  });

  it('rejects an absolute balance path and an http balance host', () => {
    const errors = validatePreset({
      ...valid,
      balancePath: 'https://management-api.x.ai/v1/billing/teams/$TEAM_ID/prepaid/balance',
      balanceBaseUrl: 'http://management-api.x.ai',
    });
    expect(errors.balancePath).toMatch(/相对路径/);
    expect(errors.balanceBaseUrl).toMatch(/https/);
  });
});
