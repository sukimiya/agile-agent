import { describe, it, expect, beforeEach } from 'vitest';
import { useStore } from '../store';

describe('store.pendingFiles', () => {
  beforeEach(() => {
    useStore.setState({ pendingFiles: [] });
  });

  it('starts empty', () => {
    expect(useStore.getState().pendingFiles).toEqual([]);
  });

  it('addFiles appends', () => {
    const f = new File(['x'], 'a.png', { type: 'image/png' });
    useStore.getState().addFiles([f]);
    expect(useStore.getState().pendingFiles).toHaveLength(1);
    expect(useStore.getState().pendingFiles[0].name).toBe('a.png');
  });

  it('removeFile removes by index', () => {
    const f1 = new File(['x'], 'a.png', { type: 'image/png' });
    const f2 = new File(['y'], 'b.png', { type: 'image/png' });
    useStore.getState().addFiles([f1, f2]);
    useStore.getState().removeFile(0);
    expect(useStore.getState().pendingFiles).toHaveLength(1);
    expect(useStore.getState().pendingFiles[0].name).toBe('b.png');
  });

  it('clearFiles empties', () => {
    const f = new File(['x'], 'a.png', { type: 'image/png' });
    useStore.getState().addFiles([f]);
    useStore.getState().clearFiles();
    expect(useStore.getState().pendingFiles).toEqual([]);
  });
});

describe('store.auth', () => {
  beforeEach(() => {
    localStorage.clear();
    useStore.setState({ token: null, userId: null, userName: null });
  });

  it('starts with no auth when localStorage is empty', () => {
    expect(useStore.getState().token).toBeNull();
    expect(useStore.getState().userId).toBeNull();
  });

  it('setAuth stores the token/user in state and localStorage', () => {
    useStore.getState().setAuth({ token: 'app-tok', userId: 'u1', name: '周琳琳' });

    expect(useStore.getState().token).toBe('app-tok');
    expect(useStore.getState().userId).toBe('u1');
    expect(useStore.getState().userName).toBe('周琳琳');
    expect(localStorage.getItem('auth-token')).toBe('app-tok');
  });

  it('clearAuth removes the token/user from state and localStorage', () => {
    useStore.getState().setAuth({ token: 'app-tok', userId: 'u1', name: '周琳琳' });

    useStore.getState().clearAuth();

    expect(useStore.getState().token).toBeNull();
    expect(localStorage.getItem('auth-token')).toBeNull();
  });
});

describe('store.access', () => {
  beforeEach(() => {
    useStore.setState({ platformAdmin: false, tenantAdmin: false, roles: [] });
  });

  it('starts with no admin flags', () => {
    expect(useStore.getState().platformAdmin).toBe(false);
    expect(useStore.getState().tenantAdmin).toBe(false);
    expect(useStore.getState().roles).toEqual([]);
  });

  it('setAccess updates admin flags from /api/auth/me', () => {
    useStore.getState().setAccess({ platformAdmin: true, tenantAdmin: false, roles: ['platform_admin'] });
    expect(useStore.getState().platformAdmin).toBe(true);
    expect(useStore.getState().roles).toEqual(['platform_admin']);
  });
});


it('clears account-scoped permissions and conversation metadata on logout', () => {
  useStore.getState().setAuth({ token: 'old', userId: 'old-user', name: '旧用户' });
  useStore.setState({
    platformAdmin: true,
    tenantAdmin: false,
    roles: ['platform_admin'],
    activeId: 'old-conv',
    conversations: [{ id: 'old-conv', title: '旧会话', createdAt: new Date() }],
  });
  useStore.getState().clearAuth();
  expect(useStore.getState()).toMatchObject({
    token: null,
    userId: null,
    userName: null,
    platformAdmin: false,
    tenantAdmin: false,
    roles: [],
    activeId: null,
    conversations: [],
  });
  expect(localStorage.getItem('auth-user-id')).toBeNull();
  expect(localStorage.getItem('auth-user-name')).toBeNull();
});
