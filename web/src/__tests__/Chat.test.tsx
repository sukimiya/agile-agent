import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import Chat from '../Chat';
import * as hintsApi from '../api/hints';

describe('Chat hints', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  const baseProps = {
    messages: [],
    isLoading: false,
    input: '',
    onInputChange: () => {},
    onSubmit: vi.fn(),
  };

  it('fetches hints on mount and renders them as buttons', async () => {
    vi.spyOn(hintsApi, 'listHints').mockResolvedValue([
      { id: 'h1', content: '有多少个客户？', createdBySubject: 'u1', createdAt: '2026-07-29T00:00:00Z' },
      { id: 'h2', content: '最近一周的销售情况如何？', createdBySubject: 'u1', createdAt: '2026-07-29T00:00:01Z' },
    ]);

    render(<Chat {...baseProps} />);

    await waitFor(() => expect(screen.getByText('有多少个客户？')).toBeInTheDocument());
    expect(screen.getByText('最近一周的销售情况如何？')).toBeInTheDocument();
  });

  it('calls onSubmit with the hint content when a hint button is clicked', async () => {
    vi.spyOn(hintsApi, 'listHints').mockResolvedValue([
      { id: 'h1', content: '有多少个客户？', createdBySubject: 'u1', createdAt: '2026-07-29T00:00:00Z' },
    ]);
    const onSubmit = vi.fn();

    render(<Chat {...baseProps} onSubmit={onSubmit} />);

    await waitFor(() => screen.getByText('有多少个客户？'));
    fireEvent.click(screen.getByText('有多少个客户？'));

    expect(onSubmit).toHaveBeenCalledWith('有多少个客户？');
  });

  it('renders no hint buttons when the fetch fails', async () => {
    vi.spyOn(hintsApi, 'listHints').mockRejectedValue(new Error('network error'));

    render(<Chat {...baseProps} />);

    await waitFor(() => expect(screen.getByText('有什么可以帮你的？')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: /？/ })).not.toBeInTheDocument();
  });
});
