import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import AppearancePanel from '../../../components/settings/AppearancePanel';
import { THEME_STORAGE_KEY } from '../../../theme';

describe('AppearancePanel', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
    delete document.documentElement.dataset.theme;
  });

  afterEach(() => {
    document.documentElement.classList.remove('dark');
  });

  it('starts on 自动 and switches the saved theme', () => {
    render(<AppearancePanel />);

    expect(screen.getByRole('button', { name: /自动/ })).toHaveAttribute('aria-pressed', 'true');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: /夜晚/ }));
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(screen.getByRole('button', { name: /夜晚/ })).toHaveAttribute('aria-pressed', 'true');

    fireEvent.click(screen.getByRole('button', { name: /白天/ }));
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
    expect(document.documentElement.classList.contains('dark')).toBe(false);

    fireEvent.click(screen.getByRole('button', { name: /自动/ }));
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('auto');
    expect(document.documentElement.dataset.theme).toBe('light');
  });
});
