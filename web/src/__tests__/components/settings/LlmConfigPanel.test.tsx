// src/__tests__/components/settings/LlmConfigPanel.test.tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import LlmConfigPanel from '../../../components/settings/LlmConfigPanel';
import * as llmConfigApi from '../../../api/llmConfig';
import * as llmProvidersApi from '../../../api/llmProviders';

const presets = [
  { id: 'p1', name: 'Example', baseUrl: 'https://models.example/v1', model: 'example-chat', balancePath: '/user/balance' },
  { id: 'p2', name: 'Other', baseUrl: 'https://other.example/v1', model: 'other-chat', balancePath: null },
];

describe('LlmConfigPanel', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(llmProvidersApi, 'fetchPresets').mockResolvedValue(presets);
  });

  it('shows the empty form when not configured', async () => {
    vi.spyOn(llmConfigApi, 'fetchLlmConfig').mockResolvedValue(null);

    render(<LlmConfigPanel />);

    await waitFor(() => expect(screen.getByText('模型')).toBeInTheDocument());
    expect(screen.getByLabelText('API Key')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '保存' })).toBeInTheDocument();
  });

  it('shows the config card when configured', async () => {
    vi.spyOn(llmConfigApi, 'fetchLlmConfig').mockResolvedValue({
      presetId: 'p1', presetName: 'Example', model: 'example-chat', apiKeyMasked: 'sk-****abcd',
    });

    render(<LlmConfigPanel />);

    await waitFor(() => expect(screen.getByText('sk-****abcd')).toBeInTheDocument());
    expect(screen.getByText('Example（example-chat）')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '更换配置' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '删除配置' })).toBeInTheDocument();
  });

  it('saves presetId and apiKey, then shows the card', async () => {
    vi.spyOn(llmConfigApi, 'fetchLlmConfig')
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce({ presetId: 'p2', presetName: 'Other', model: 'other-chat', apiKeyMasked: 'sk-****wxyz' });
    const save = vi.spyOn(llmConfigApi, 'saveLlmConfig').mockResolvedValue();

    render(<LlmConfigPanel />);
    await waitFor(() => expect(screen.getByLabelText('API Key')).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText('模型'), { target: { value: 'p2' } });
    fireEvent.change(screen.getByLabelText('API Key'), { target: { value: 'sk-new' } });
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(save).toHaveBeenCalledWith('p2', 'sk-new'));
    await waitFor(() => expect(screen.getByText('sk-****wxyz')).toBeInTheDocument());
  });

  it('shows the backend message when save fails', async () => {
    vi.spyOn(llmConfigApi, 'fetchLlmConfig').mockResolvedValue(null);
    vi.spyOn(llmConfigApi, 'saveLlmConfig').mockRejectedValue(
      new llmConfigApi.LlmConfigError('SAVE_FAILED', '连通性校验失败: 401'),
    );

    render(<LlmConfigPanel />);
    await waitFor(() => expect(screen.getByLabelText('API Key')).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText('模型'), { target: { value: 'p1' } });
    fireEvent.change(screen.getByLabelText('API Key'), { target: { value: 'bad' } });
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(screen.getByText('连通性校验失败: 401')).toBeInTheDocument());
  });

  it('deletes the config after confirmation', async () => {
    vi.spyOn(llmConfigApi, 'fetchLlmConfig').mockResolvedValue({
      presetId: 'p1', presetName: 'Example', model: 'example-chat', apiKeyMasked: 'sk-****abcd',
    });
    const del = vi.spyOn(llmConfigApi, 'deleteLlmConfig').mockResolvedValue();

    render(<LlmConfigPanel />);
    await waitFor(() => expect(screen.getByRole('button', { name: '删除配置' })).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: '删除配置' }));
    fireEvent.click(screen.getByRole('button', { name: '确认删除' }));

    await waitFor(() => expect(del).toHaveBeenCalled());
    await waitFor(() => expect(screen.getByLabelText('API Key')).toBeInTheDocument());
  });

  it('disables the form and hints to contact admin when no presets exist', async () => {
    vi.spyOn(llmConfigApi, 'fetchLlmConfig').mockResolvedValue(null);
    vi.spyOn(llmProvidersApi, 'fetchPresets').mockResolvedValue([]);

    render(<LlmConfigPanel />);

    await waitFor(() => expect(screen.getByText('暂无可用模型，请联系管理员')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: '保存' })).toBeDisabled();
  });
});
