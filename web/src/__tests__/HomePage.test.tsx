import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import HomePage from '../HomePage';
import { useStore } from '../store';

const AUTHORIZE = '/api/auth/oauth2/authorize';
const REPO = 'https://github.com/sukimiya/agile-agent';

describe('HomePage', () => {
  beforeEach(() => {
    localStorage.clear();
    useStore.setState({
      token: null,
      userId: null,
      userName: null,
      platformAdmin: false,
      tenantAdmin: false,
      roles: [],
    });
  });

  it('shows the public hero and login targets when logged out', () => {
    render(<HomePage />);

    expect(screen.getByRole('heading', { level: 1, name: '记得住，也忘得掉' })).toBeInTheDocument();
    expect(screen.getByText('开源的对话助手。需要时会找回相关往事；不想留的，在聊天里说一声就可以忘掉。')).toBeInTheDocument();
    expect(screen.queryByText(/欢迎回来/)).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '进入聊天' })).not.toBeInTheDocument();

    const logins = screen.getAllByRole('link', { name: 'GitHub 登录' });
    expect(logins.length).toBeGreaterThanOrEqual(2);
    for (const link of logins) {
      expect(link).toHaveAttribute('href', AUTHORIZE);
    }
    expect(screen.getByRole('link', { name: '开源仓库' })).toHaveAttribute('href', REPO);
    expect(screen.getByRole('heading', { level: 2, name: '为什么选择 agile-agent' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 3, name: '相关往事会被找回' })).toBeInTheDocument();
  });

  it('welcomes the signed-in person and links into the chat', () => {
    useStore.setState({ token: 'app-tok', userId: 'u1', userName: '周琳琳' });
    render(<HomePage />);

    expect(screen.getByText('欢迎回来，周琳琳')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'GitHub 登录' })).not.toBeInTheDocument();

    const enter = screen.getAllByRole('link', { name: '进入聊天' });
    expect(enter.length).toBeGreaterThanOrEqual(2);
    for (const link of enter) {
      expect(link).toHaveAttribute('href', '/');
    }
    expect(screen.getByRole('heading', { level: 1, name: '记得住，也忘得掉' })).toBeInTheDocument();
  });
});
