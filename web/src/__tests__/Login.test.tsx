import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import Login from '../Login';
import { useStore } from '../store';

describe('Login', () => {
  beforeEach(() => {
    localStorage.clear();
    useStore.setState({ token: null, userId: null, userName: null });
  });

  it('links to the GitHub OAuth authorize endpoint', () => {
    render(<Login />);

    const link = screen.getByRole('link', { name: '使用 GitHub 登录' });
    expect(link).toHaveAttribute('href', '/api/auth/oauth2/authorize');
    expect(screen.getByText('agile-agent')).toBeInTheDocument();
    expect(screen.queryByLabelText('用户名')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('密码')).not.toBeInTheDocument();
  });

  it('shows the callback error passed in by the app', () => {
    render(<Login error="登录服务暂时不可用" />);
    expect(screen.getByText('登录服务暂时不可用')).toBeInTheDocument();
  });
});
