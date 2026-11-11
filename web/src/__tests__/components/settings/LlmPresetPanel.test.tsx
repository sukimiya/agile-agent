// src/__tests__/components/settings/LlmPresetPanel.test.tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import LlmPresetPanel from '../../../components/settings/LlmPresetPanel';
import * as llmProvidersApi from '../../../api/llmProviders';

const presets = [
  { id: 'p1', name: 'Example', baseUrl: 'https://models.example/v1', model: 'example-chat', balancePath: '/user/balance' },
  { id: 'p2', name: 'Other', baseUrl: 'https://other.example/v1', model: 'other-chat', balancePath: null },
];

describe('LlmPresetPanel', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(llmProvidersApi, 'fetchPresets').mockResolvedValue(presets);
  });

  it('renders the preset list', async () => {
    render(<LlmPresetPanel />);

    await waitFor(() => expect(screen.getByText(/Example/)).toBeInTheDocument());
    expect(screen.getByText(/Other/)).toBeInTheDocument();
    expect(screen.getByText('https://models.example/v1')).toBeInTheDocument();
    expect(screen.getByText(/\/user\/balance/)).toBeInTheDocument();
  });

  it('creates a preset via the new-preset form', async () => {
    const create = vi.spyOn(llmProvidersApi, 'createPreset').mockResolvedValue();

    render(<LlmPresetPanel />);
    await waitFor(() => expect(screen.getByText(/Example/)).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: '新增预置' }));
    fireEvent.change(screen.getByLabelText('名称'), { target: { value: '新模型' } });
    fireEvent.change(screen.getByLabelText('Base URL'), { target: { value: 'https://x.com/v1' } });
    fireEvent.change(screen.getByLabelText('Model'), { target: { value: 'x-1' } });
    fireEvent.change(screen.getByLabelText('余额接口路径（可选）'), { target: { value: '/balance' } });
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(create).toHaveBeenCalledWith({
      name: '新模型', baseUrl: 'https://x.com/v1', model: 'x-1', balancePath: '/balance',
    }));
  });

  it('edits a preset with a prefilled form', async () => {
    const update = vi.spyOn(llmProvidersApi, 'updatePreset').mockResolvedValue();

    render(<LlmPresetPanel />);
    await waitFor(() => expect(screen.getByText(/Example/)).toBeInTheDocument());

    const editButtons = screen.getAllByRole('button', { name: '编辑' });
    fireEvent.click(editButtons[0]);
    expect(screen.getByLabelText('名称')).toHaveValue('Example');
    expect(screen.getByLabelText('余额接口路径（可选）')).toHaveValue('/user/balance');

    fireEvent.change(screen.getByLabelText('Model'), { target: { value: 'example-v2' } });
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(update).toHaveBeenCalledWith('p1', {
      name: 'Example', baseUrl: 'https://models.example/v1', model: 'example-v2', balancePath: '/user/balance',
    }));
  });

  it('deletes a preset after confirmation', async () => {
    const del = vi.spyOn(llmProvidersApi, 'deletePreset').mockResolvedValue();

    render(<LlmPresetPanel />);
    await waitFor(() => expect(screen.getByText(/Example/)).toBeInTheDocument());

    fireEvent.click(screen.getAllByRole('button', { name: '删除' })[0]);
    expect(screen.getByText(/删除后引用该预置的用户配置将失效/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '确认删除' }));

    await waitFor(() => expect(del).toHaveBeenCalledWith('p1'));
  });

  it('shows 仅管理员可操作 on 403', async () => {
    vi.spyOn(llmProvidersApi, 'fetchPresets').mockRejectedValue(
      new llmProvidersApi.PresetError('FORBIDDEN', '仅管理员可操作'),
    );

    render(<LlmPresetPanel />);

    await waitFor(() => expect(screen.getByText('仅管理员可操作')).toBeInTheDocument());
  });
});
