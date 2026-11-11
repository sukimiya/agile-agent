import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import HintManagementPanel from '../../../components/settings/HintManagementPanel';
import * as hintsApi from '../../../api/hints';

const hint = { id: 'h1', content: '有多少个客户？', createdBySubject: 'u1', createdAt: '2026-07-29T00:00:00Z' };

describe('HintManagementPanel', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('loads and shows existing hints', async () => {
    vi.spyOn(hintsApi, 'listHints').mockResolvedValue([hint]);

    render(<HintManagementPanel />);

    await waitFor(() => expect(screen.getByText('有多少个客户？')).toBeInTheDocument());
  });

  it('shows an empty state when there are no hints', async () => {
    vi.spyOn(hintsApi, 'listHints').mockResolvedValue([]);

    render(<HintManagementPanel />);

    await waitFor(() => expect(screen.getByText('暂无提示语')).toBeInTheDocument());
  });

  it('adds a new hint and refetches the list', async () => {
    const listSpy = vi.spyOn(hintsApi, 'listHints').mockResolvedValue([]);
    const createSpy = vi.spyOn(hintsApi, 'createHint').mockResolvedValue(hint);

    render(<HintManagementPanel />);
    await waitFor(() => expect(screen.getByText('暂无提示语')).toBeInTheDocument());

    fireEvent.change(screen.getByPlaceholderText('新提示语'), { target: { value: '有多少个客户？' } });
    fireEvent.click(screen.getByRole('button', { name: '添加' }));

    await waitFor(() => expect(createSpy).toHaveBeenCalledWith('有多少个客户？'));
    await waitFor(() => expect(listSpy).toHaveBeenCalledTimes(2));
  });

  it('disables the add button until the input has content', async () => {
    vi.spyOn(hintsApi, 'listHints').mockResolvedValue([]);

    render(<HintManagementPanel />);
    await waitFor(() => expect(screen.getByText('暂无提示语')).toBeInTheDocument());

    expect(screen.getByRole('button', { name: '添加' })).toBeDisabled();
  });

  it('edits a hint inline and saves it', async () => {
    const listSpy = vi.spyOn(hintsApi, 'listHints').mockResolvedValue([hint]);
    const updateSpy = vi.spyOn(hintsApi, 'updateHint').mockResolvedValue(undefined);

    render(<HintManagementPanel />);
    await waitFor(() => expect(screen.getByText('有多少个客户？')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    const input = screen.getByDisplayValue('有多少个客户？');
    fireEvent.change(input, { target: { value: '改过的问题' } });
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(updateSpy).toHaveBeenCalledWith('h1', '改过的问题'));
    await waitFor(() => expect(listSpy).toHaveBeenCalledTimes(2));
  });

  it('cancels an inline edit without saving', async () => {
    vi.spyOn(hintsApi, 'listHints').mockResolvedValue([hint]);
    const updateSpy = vi.spyOn(hintsApi, 'updateHint');

    render(<HintManagementPanel />);
    await waitFor(() => expect(screen.getByText('有多少个客户？')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    fireEvent.change(screen.getByDisplayValue('有多少个客户？'), { target: { value: '改过的问题' } });
    fireEvent.click(screen.getByRole('button', { name: '取消' }));

    expect(updateSpy).not.toHaveBeenCalled();
    expect(screen.getByText('有多少个客户？')).toBeInTheDocument();
  });

  it('deletes a hint and refetches the list', async () => {
    const listSpy = vi.spyOn(hintsApi, 'listHints').mockResolvedValue([hint]);
    const deleteSpy = vi.spyOn(hintsApi, 'deleteHint').mockResolvedValue(undefined);

    render(<HintManagementPanel />);
    await waitFor(() => expect(screen.getByText('有多少个客户？')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: '删除' }));

    await waitFor(() => expect(deleteSpy).toHaveBeenCalledWith('h1'));
    await waitFor(() => expect(listSpy).toHaveBeenCalledTimes(2));
  });

  it('shows the error reason when adding a hint fails', async () => {
    vi.spyOn(hintsApi, 'listHints').mockResolvedValue([]);
    vi.spyOn(hintsApi, 'createHint').mockRejectedValue(new hintsApi.HintError('FORBIDDEN', '仅管理员可操作'));

    render(<HintManagementPanel />);
    await waitFor(() => expect(screen.getByText('暂无提示语')).toBeInTheDocument());

    fireEvent.change(screen.getByPlaceholderText('新提示语'), { target: { value: '新问题' } });
    fireEvent.click(screen.getByRole('button', { name: '添加' }));

    await waitFor(() => expect(screen.getByText('仅管理员可操作')).toBeInTheDocument());
  });
});
