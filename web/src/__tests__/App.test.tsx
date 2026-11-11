import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import App from '../App';
import { useStore } from '../store';
import { resetOAuthCallbackState } from '../auth/callback';

const OLD_ID = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';

function mockFetchByUrl(handlers: Record<string, () => unknown>) {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    for (const [prefix, handler] of Object.entries(handlers)) {
      if (url.startsWith(prefix)) return handler();
    }
    return { ok: true, status: 200, json: async () => [] };
  }));
}

function me(extra: Record<string, unknown> = {}) {
  return {
    ok: true,
    status: 200,
    json: async () => ({
      userId: 'u1',
      name: '周琳琳',
      roles: [],
      platformAdmin: false,
      tenantAdmin: false,
      ...extra,
    }),
  };
}

describe('App', () => {
  beforeEach(() => {
    localStorage.clear();
    resetOAuthCallbackState();
    window.history.replaceState(null, '', '/');
    useStore.setState({
      token: null,
      userId: null,
      userName: null,
      platformAdmin: false,
      tenantAdmin: false,
      roles: [],
      pendingFiles: [],
      conversations: [],
      activeId: null,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    resetOAuthCallbackState();
  });

  it('shows the GitHub login screen when there is no stored auth token', () => {
    render(<App />);

    expect(screen.getByRole('link', { name: '使用 GitHub 登录' })).toHaveAttribute(
      'href',
      '/api/auth/oauth2/authorize',
    );
    expect(screen.queryByPlaceholderText('输入您的问题...')).not.toBeInTheDocument();
  });

  it('shows the chat UI when a valid token is present', async () => {
    useStore.getState().setAuth({ token: 'app-tok', userId: 'u1', name: '周琳琳' });
    mockFetchByUrl({
      '/api/auth/me': () => me(),
      '/api/conversations/': () => ({ ok: false, status: 404 }),
      '/api/conversations': () => ({ ok: true, status: 200, json: async () => [] }),
    });

    render(<App />);

    await waitFor(() => expect(screen.getByPlaceholderText('输入您的问题...')).toBeInTheDocument());
    expect(screen.getByText('周琳琳')).toBeInTheDocument();
  });

  it('does not re-fetch messages for the fresh id when starting a new conversation', async () => {
    useStore.getState().setAuth({ token: 'app-tok', userId: 'u1', name: '周琳琳' });
    const messagesFetches: true[] = [];
    mockFetchByUrl({
      '/api/auth/me': () => me(),
      '/api/conversations/': () => {
        messagesFetches.push(true);
        return { ok: false, status: 404 };
      },
      '/api/conversations': () => ({ ok: true, status: 200, json: async () => [] }),
    });

    render(<App />);

    await waitFor(() => expect(screen.getByPlaceholderText('输入您的问题...')).toBeInTheDocument());
    await waitFor(() => expect(messagesFetches.length).toBe(1));

    fireEvent.click(screen.getByText('+ 新对话'));

    await new Promise((r) => setTimeout(r, 0));
    expect(messagesFetches.length).toBe(1);
  });

  it('clears auth and shows login when the stored token is invalid', async () => {
    useStore.getState().setAuth({ token: 'stale-tok', userId: 'u1', name: '周琳琳' });
    mockFetchByUrl({
      '/api/auth/me': () => ({ ok: false, status: 401 }),
    });

    render(<App />);

    await waitFor(() => expect(screen.getByRole('link', { name: '使用 GitHub 登录' })).toBeInTheDocument());
  });

  it('hides admin menus when /api/auth/me is not an admin', async () => {
    useStore.getState().setAuth({ token: 'app-tok', userId: 'u1', name: '周琳琳' });
    mockFetchByUrl({
      '/api/auth/me': () => me(),
      '/api/conversations/': () => ({ ok: false, status: 404 }),
      '/api/conversations': () => ({ ok: true, status: 200, json: async () => [] }),
    });

    render(<App />);

    await waitFor(() => expect(screen.getByPlaceholderText('输入您的问题...')).toBeInTheDocument());
    fireEvent.click(screen.getByLabelText('设置'));

    expect(screen.queryByRole('button', { name: 'API文档上传' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '提示语管理' })).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByText('知识库上传').length).toBeGreaterThan(0));
  });

  it('shows platform-admin menus from /api/auth/me', async () => {
    useStore.getState().setAuth({ token: 'app-tok', userId: 'u1', name: '周琳琳' });
    mockFetchByUrl({
      '/api/auth/me': () => me({ platformAdmin: true, roles: ['platform_admin'] }),
      '/api/conversations/': () => ({ ok: false, status: 404 }),
      '/api/conversations': () => ({ ok: true, status: 200, json: async () => [] }),
    });

    render(<App />);

    await waitFor(() => expect(screen.getByPlaceholderText('输入您的问题...')).toBeInTheDocument());
    fireEvent.click(screen.getByLabelText('设置'));

    await waitFor(() => expect(screen.getByRole('button', { name: 'API文档上传' })).toBeInTheDocument());
    expect(screen.getByRole('button', { name: '提示语管理' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '模型预置管理' })).toBeInTheDocument();
  });

  it('shows a jump-to-settings message when chat returns 428', async () => {
    useStore.getState().setAuth({ token: 'app-tok', userId: 'u1', name: '周琳琳' });
    mockFetchByUrl({
      '/api/auth/me': () => me(),
      '/api/conversations/': () => ({ ok: false, status: 404 }),
      '/api/conversations': () => ({ ok: true, status: 200, json: async () => [] }),
      '/api/chat/completions': () => ({
        ok: false,
        status: 428,
        json: async () => ({ code: 'LLM_CONFIG_REQUIRED', message: 'Current tenant has no LLM configuration' }),
      }),
      '/api/llm-providers': () => ({ ok: true, status: 200, json: async () => [] }),
      '/api/llm-config': () => ({ ok: false, status: 404 }),
    });

    render(<App />);
    await waitFor(() => expect(screen.getByPlaceholderText('输入您的问题...')).toBeInTheDocument());

    fireEvent.change(screen.getByPlaceholderText('输入您的问题...'), { target: { value: '你好' } });
    fireEvent.click(screen.getByLabelText('发送'));

    await waitFor(() => expect(screen.getByText('请先在设置页配置模型和 API Key')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: '去配置' }));

    await waitFor(() => expect(screen.getAllByText('LLM 设置')).toHaveLength(2));
  });

  it('reads snake_case token usage from the completion stream', async () => {
    useStore.getState().setAuth({ token: 'app-tok', userId: 'u1', name: '周琳琳' });
    const encoder = new TextEncoder();
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode(
          'data: {"choices":[{"delta":{"content":"收到"}}]}\n\n'
          + 'data: {"choices":[],"usage":{"prompt_tokens":3,"completion_tokens":1,"total_tokens":4}}\n\n'
          + 'data: [DONE]\n\n',
        ));
        controller.close();
      },
    });
    const bodies: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
      if (url.startsWith('/api/chat/completions')) {
        bodies.push(String(init?.body ?? ''));
        return { ok: true, status: 200, body: stream };
      }
      if (url.startsWith('/api/auth/me')) return me();
      if (url.startsWith('/api/conversations/')) return { ok: false, status: 404 };
      return { ok: true, status: 200, json: async () => [] };
    }));

    render(<App />);
    await waitFor(() => expect(screen.getByPlaceholderText('输入您的问题...')).toBeInTheDocument());
    fireEvent.change(screen.getByPlaceholderText('输入您的问题...'), { target: { value: '你好' } });
    fireEvent.click(screen.getByLabelText('发送'));

    await waitFor(() => expect(screen.getByText('收到')).toBeInTheDocument());
    await waitFor(() => expect(screen.getByText('~4 tokens')).toBeInTheDocument());
    const sent = JSON.parse(bodies[0]);
    expect(sent.model).toBe('agile-agent');
    expect(sent.conversationId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
  });

  it('shows a generic failure message when chat returns a non-ok status', async () => {
    useStore.getState().setAuth({ token: 'app-tok', userId: 'u1', name: '周琳琳' });
    mockFetchByUrl({
      '/api/auth/me': () => me(),
      '/api/conversations/': () => ({ ok: false, status: 404 }),
      '/api/conversations': () => ({ ok: true, status: 200, json: async () => [] }),
      '/api/chat/completions': () => ({ ok: false, status: 500, json: async () => ({}) }),
    });

    render(<App />);
    await waitFor(() => expect(screen.getByPlaceholderText('输入您的问题...')).toBeInTheDocument());

    fireEvent.change(screen.getByPlaceholderText('输入您的问题...'), { target: { value: '你好' } });
    fireEvent.click(screen.getByLabelText('发送'));

    await waitFor(() => expect(screen.getByText(/请求失败/)).toBeInTheDocument());
  });

  it('clears the conversation when the session ends and another account logs in', async () => {
    window.history.replaceState(null, '', `/?id=${OLD_ID}`);
    useStore.getState().setAuth({ token: 'tok-a', userId: 'u1', name: '用户A' });
    mockFetchByUrl({
      '/api/auth/me': () => me({ userId: 'u1', name: '用户A' }),
      [`/api/conversations/${OLD_ID}`]: () => ({
        ok: true,
        status: 200,
        json: async () => ([{ id: 'm1', role: 'assistant', content: '旧对话内容' }]),
      }),
      '/api/conversations/': () => ({ ok: false, status: 404 }),
      '/api/conversations': () => ({ ok: true, status: 200, json: async () => [] }),
    });

    render(<App />);
    await waitFor(() => expect(screen.getByText('旧对话内容')).toBeInTheDocument());

    fireEvent.click(screen.getByText('退出登录'));
    await waitFor(() => expect(screen.getByRole('link', { name: '使用 GitHub 登录' })).toBeInTheDocument());

    useStore.getState().setAuth({ token: 'tok-b', userId: 'u2', name: '用户B' });
    await waitFor(() => expect(screen.getByPlaceholderText('输入您的问题...')).toBeInTheDocument());

    expect(screen.queryByText('旧对话内容')).not.toBeInTheDocument();
    expect(window.location.search).not.toContain(OLD_ID);
  });

  it('stores the hash token on /login/callback, clears the hash, and validates /api/auth/me', async () => {
    window.history.replaceState(null, '', '/login/callback#token=app-tok');
    mockFetchByUrl({
      '/api/auth/me': () => me({ userId: 'u9', name: '周琳琳' }),
      '/api/conversations/': () => ({ ok: false, status: 404 }),
      '/api/conversations': () => ({ ok: true, status: 200, json: async () => [] }),
    });

    render(<App />);

    await waitFor(() => expect(screen.getByPlaceholderText('输入您的问题...')).toBeInTheDocument());
    expect(localStorage.getItem('auth-token')).toBe('app-tok');
    expect(window.location.hash).toBe('');
    expect(window.location.pathname).toBe('/');
    expect(screen.getByText('周琳琳')).toBeInTheDocument();
    const meCall = vi.mocked(fetch).mock.calls.find(([url]) => url === '/api/auth/me');
    expect(new Headers(meCall?.[1]?.headers).get('Authorization')).toBe('Bearer app-tok');
  });

  it('shows the login page when the callback hash reports an error', () => {
    window.history.replaceState(null, '', '/login/callback#error=provider_unavailable');

    render(<App />);

    expect(screen.getByText('登录服务暂时不可用')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '使用 GitHub 登录' })).toBeInTheDocument();
    expect(localStorage.getItem('auth-token')).toBeNull();
    expect(window.location.hash).toBe('');
    expect(window.location.pathname).toBe('/');
  });

  it('returns to login when the callback token is rejected by /api/auth/me', async () => {
    window.history.replaceState(null, '', '/login/callback#token=bad-tok');
    mockFetchByUrl({
      '/api/auth/me': () => ({ ok: false, status: 401 }),
    });

    render(<App />);

    await waitFor(() => expect(screen.getByText('登录失败，请重试')).toBeInTheDocument());
    expect(localStorage.getItem('auth-token')).toBeNull();
    expect(window.location.hash).toBe('');
  });
});
