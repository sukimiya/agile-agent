import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import HomePage from '../HomePage';
import { useStore } from '../store';

const AUTHORIZE = '/api/auth/oauth2/authorize';
const REPO = 'https://github.com/sukimiya/agile-agent';
const EN_TITLE = 'She remembers, and she can forget';
const EN_SUBTITLE = "An open-source conversation assistant. It will recall what matters when you need it; if you'd rather not keep something, just say so in the chat, and it will forget.";
const ZH_SUBTITLE = '开源的对话助手。需要时会找回相关往事；不想留的，在聊天里说一声就可以忘掉。';

function paragraphText(text: string) {
  return screen.getByText((_, node) => node?.tagName === 'P' && node.textContent === text);
}

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

  it('defaults to English and points sign-in at GitHub', () => {
    render(<HomePage />);

    expect(screen.getByRole('heading', { level: 1, name: EN_TITLE })).toBeInTheDocument();
    expect(paragraphText(EN_SUBTITLE)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { level: 1, name: '记得住，也忘得掉' })).not.toBeInTheDocument();
    expect(screen.queryByText(/Welcome back/)).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Open chat' })).not.toBeInTheDocument();

    const logins = screen.getAllByRole('link', { name: 'Sign in with GitHub' });
    expect(logins.length).toBeGreaterThanOrEqual(2);
    for (const link of logins) expect(link).toHaveAttribute('href', AUTHORIZE);
    expect(screen.getByRole('link', { name: 'Source on GitHub' })).toHaveAttribute('href', REPO);
    expect(screen.getByRole('heading', { level: 2, name: 'Why agile-agent' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 3, name: 'Relevant history is brought back' })).toBeInTheDocument();
  });

  it('switches to the existing Chinese copy and remembers the choice', () => {
    render(<HomePage />);
    fireEvent.click(screen.getByRole('button', { name: '中文' }));

    expect(screen.getByRole('heading', { level: 1, name: '记得住，也忘得掉' })).toBeInTheDocument();
    expect(paragraphText(ZH_SUBTITLE)).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'GitHub 登录' }).length).toBeGreaterThanOrEqual(2);
    expect(screen.getByRole('link', { name: '开源仓库' })).toHaveAttribute('href', REPO);
    expect(localStorage.getItem('home-locale')).toBe('zh');

    fireEvent.click(screen.getByRole('button', { name: 'EN' }));
    expect(screen.getByRole('heading', { level: 1, name: EN_TITLE })).toBeInTheDocument();
    expect(localStorage.getItem('home-locale')).toBe('en');
  });

  it('opens in Chinese when that choice was saved', () => {
    localStorage.setItem('home-locale', 'zh');
    render(<HomePage />);
    expect(screen.getByRole('heading', { level: 1, name: '记得住，也忘得掉' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { level: 1, name: EN_TITLE })).not.toBeInTheDocument();
  });

  it('welcomes a signed-in person in the active language and links into chat', () => {
    useStore.setState({ token: 'app-tok', userId: 'u1', userName: '周琳琳' });
    render(<HomePage />);

    expect(screen.getByText('Welcome back, 周琳琳')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Sign in with GitHub' })).not.toBeInTheDocument();
    const enter = screen.getAllByRole('link', { name: 'Open chat' });
    expect(enter.length).toBeGreaterThanOrEqual(2);
    for (const link of enter) expect(link).toHaveAttribute('href', '/');

    fireEvent.click(screen.getByRole('button', { name: '中文' }));
    expect(screen.getByText('欢迎回来，周琳琳')).toBeInTheDocument();
    const zhEnter = screen.getAllByRole('link', { name: '进入聊天' });
    expect(zhEnter.length).toBeGreaterThanOrEqual(2);
    for (const link of zhEnter) expect(link).toHaveAttribute('href', '/');
  });
});