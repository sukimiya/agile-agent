import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import Sidebar from '../Sidebar';

describe('Sidebar', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('lists conversations fetched from the backend', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => [{ id: 's1', title: '第一次对话', updatedAt: new Date().toISOString() }],
    })));

    render(
      <Sidebar
        activeId="s1"
        onSelect={() => {}}
        onNewChat={() => {}}
        refreshKey={0}
        collapsed={false}
        onToggle={() => {}}
        userName="测试用户"
        onLogout={() => {}}
        onOpenSettings={() => {}}
      />
    );

    await waitFor(() => expect(screen.getByText('第一次对话')).toBeInTheDocument());
  });

  it('calls onSelect when a conversation is clicked', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => [{ id: 's1', title: '第一次对话', updatedAt: new Date().toISOString() }],
    })));
    const onSelect = vi.fn();

    render(
      <Sidebar
        activeId=""
        onSelect={onSelect}
        onNewChat={() => {}}
        refreshKey={0}
        collapsed={false}
        onToggle={() => {}}
        userName="测试用户"
        onLogout={() => {}}
        onOpenSettings={() => {}}
      />
    );
    await waitFor(() => screen.getByText('第一次对话'));
    fireEvent.click(screen.getByText('第一次对话'));

    expect(onSelect).toHaveBeenCalledWith('s1');
  });

  it('calls onNewChat when the new-chat button is clicked', () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => [] })));
    const onNewChat = vi.fn();

    render(
      <Sidebar
        activeId=""
        onSelect={() => {}}
        onNewChat={onNewChat}
        refreshKey={0}
        collapsed={false}
        onToggle={() => {}}
        userName="测试用户"
        onLogout={() => {}}
        onOpenSettings={() => {}}
      />
    );
    fireEvent.click(screen.getByText('+ 新对话'));

    expect(onNewChat).toHaveBeenCalled();
  });

  it('calls onOpenSettings when the settings button is clicked', () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => [] })));
    const onOpenSettings = vi.fn();

    render(
      <Sidebar
        activeId=""
        onSelect={() => {}}
        onNewChat={() => {}}
        refreshKey={0}
        collapsed={false}
        onToggle={() => {}}
        userName="测试用户"
        onLogout={() => {}}
        onOpenSettings={onOpenSettings}
      />
    );
    fireEvent.click(screen.getByLabelText('设置'));

    expect(onOpenSettings).toHaveBeenCalled();
  });
});
