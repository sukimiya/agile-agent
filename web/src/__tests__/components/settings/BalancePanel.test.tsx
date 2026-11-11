import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import BalancePanel from '../../../components/settings/BalancePanel';
import * as llmConfigApi from '../../../api/llmConfig';

describe('BalancePanel', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders the balance list under the 我的余额 heading', async () => {
    vi.spyOn(llmConfigApi, 'fetchBalance').mockResolvedValue([{ currency: 'CNY', balance: '12.34' }]);

    render(<BalancePanel />);

    expect(screen.getByText('我的余额')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('12.34')).toBeInTheDocument());
    expect(screen.getByText('CNY')).toBeInTheDocument();
  });

  it('shows the not-configured hint with a jump button on 555', async () => {
    vi.spyOn(llmConfigApi, 'fetchBalance').mockRejectedValue(
      new llmConfigApi.LlmConfigError('NOT_CONFIGURED', '请先在 LLM 设置中配置模型和 API Key'),
    );
    const onGoToLlmConfig = vi.fn();

    render(<BalancePanel onGoToLlmConfig={onGoToLlmConfig} />);

    await waitFor(() => expect(screen.getByText('请先在 LLM 设置中配置模型和 API Key')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: '去配置' }));
    expect(onGoToLlmConfig).toHaveBeenCalled();
  });

  it('shows 当前厂商不支持余额查询 on 404', async () => {
    vi.spyOn(llmConfigApi, 'fetchBalance').mockRejectedValue(
      new llmConfigApi.LlmConfigError('NOT_SUPPORTED', '当前厂商不支持余额查询'),
    );

    render(<BalancePanel />);

    await waitFor(() => expect(screen.getByText('当前厂商不支持余额查询')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '去配置' })).not.toBeInTheDocument();
  });

  it('shows the provider error message on 502', async () => {
    vi.spyOn(llmConfigApi, 'fetchBalance').mockRejectedValue(
      new llmConfigApi.LlmConfigError('PROVIDER_ERROR', '厂商接口报错'),
    );

    render(<BalancePanel />);

    await waitFor(() => expect(screen.getByText('厂商接口报错')).toBeInTheDocument());
  });
});
