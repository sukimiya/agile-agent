import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import SettingsModal from '../../components/SettingsModal';
import * as knowledgeApi from '../../api/knowledge';
import * as hintsApi from '../../api/hints';
import * as llmConfigApi from '../../api/llmConfig';
import * as llmProvidersApi from '../../api/llmProviders';

describe('SettingsModal', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(knowledgeApi, 'listKnowledge').mockResolvedValue([]);
    vi.spyOn(hintsApi, 'listHints').mockResolvedValue([]);
    vi.spyOn(llmConfigApi, 'fetchBalance').mockResolvedValue([]);
    vi.spyOn(llmConfigApi, 'fetchLlmConfig').mockResolvedValue(null);
    vi.spyOn(llmProvidersApi, 'fetchPresets').mockResolvedValue([]);
  });

  it('renders nothing when closed', () => {
    render(<SettingsModal isOpen={false} onClose={() => {}} platformAdmin={false} tenantAdmin={false} />);
    expect(screen.queryByText('知识库上传')).not.toBeInTheDocument();
  });

  it('shows 知识库上传 and 文件上传审核列表 menus but not API文档上传 for a non-admin', () => {
    render(<SettingsModal isOpen={true} onClose={() => {}} platformAdmin={false} tenantAdmin={false} />);

    expect(screen.getByRole('button', { name: '知识库上传' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '文件上传审核列表' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'API文档上传' })).not.toBeInTheDocument();
  });

  it('shows API文档上传 menu for an admin', () => {
    render(<SettingsModal isOpen={true} onClose={() => {}} platformAdmin={true} tenantAdmin={true} />);
    expect(screen.getByRole('button', { name: 'API文档上传' })).toBeInTheDocument();
  });

  it('defaults to the 知识库上传 panel', () => {
    render(<SettingsModal isOpen={true} onClose={() => {}} platformAdmin={false} tenantAdmin={false} />);
    // Panel heading (h2) vs menu button (button) - both have same text, so there are 2 matches.
    expect(screen.getAllByText('知识库上传')).toHaveLength(2);
  });

  it('switches to the review list panel when its menu item is clicked', async () => {
    render(<SettingsModal isOpen={true} onClose={() => {}} platformAdmin={false} tenantAdmin={false} />);
    fireEvent.click(screen.getByRole('button', { name: '文件上传审核列表' }));
    await waitFor(() => expect(screen.getByText('暂无文档')).toBeInTheDocument());
  });

  it('calls onClose when the close button is clicked', () => {
    const onClose = vi.fn();
    render(<SettingsModal isOpen={true} onClose={onClose} platformAdmin={false} tenantAdmin={false} />);
    fireEvent.click(screen.getByLabelText('关闭'));
    expect(onClose).toHaveBeenCalled();
  });

  it('calls onClose when Escape is pressed', () => {
    const onClose = vi.fn();
    render(<SettingsModal isOpen={true} onClose={onClose} platformAdmin={false} tenantAdmin={false} />);
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalled();
  });

  it('shows 提示语管理 menu only for an admin', () => {
    const { unmount } = render(<SettingsModal isOpen={true} onClose={() => {}} platformAdmin={false} tenantAdmin={false} />);
    expect(screen.queryByRole('button', { name: '提示语管理' })).not.toBeInTheDocument();
    unmount();

    render(<SettingsModal isOpen={true} onClose={() => {}} platformAdmin={true} tenantAdmin={true} />);
    expect(screen.getByRole('button', { name: '提示语管理' })).toBeInTheDocument();
  });

  it('shows LLM 设置 for a non-admin and hides tenant-admin menus', () => {
    render(<SettingsModal isOpen={true} onClose={() => {}} platformAdmin={false} tenantAdmin={false} />);
    expect(screen.getByRole('button', { name: 'LLM 设置' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '我的余额' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '模型预置管理' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '提示语管理' })).not.toBeInTheDocument();
  });

  it('shows tenant menus but not platform-only menus for a tenant admin', () => {
    render(<SettingsModal isOpen={true} onClose={() => {}} platformAdmin={false} tenantAdmin={true} />);
    expect(screen.getByRole('button', { name: '我的余额' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'API文档上传' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '模型预置管理' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '提示语管理' })).not.toBeInTheDocument();
  });

  it('shows platform-admin menus', () => {
    render(<SettingsModal isOpen={true} onClose={() => {}} platformAdmin={true} tenantAdmin={false} />);
    expect(screen.getByRole('button', { name: '模型预置管理' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '提示语管理' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '企业接口配置' })).not.toBeInTheDocument();
  });

  it('switches to the 我的余额 panel for a tenant admin', async () => {
    render(<SettingsModal isOpen={true} onClose={() => {}} platformAdmin={false} tenantAdmin={true} />);
    fireEvent.click(screen.getByRole('button', { name: '我的余额' }));
    await waitFor(() => expect(screen.getAllByText('我的余额')).toHaveLength(2));
  });

  it('opens on the panel named by initialMenu', async () => {
    render(<SettingsModal isOpen={true} onClose={() => {}} platformAdmin={false} tenantAdmin={false} initialMenu="llm-config" />);
    await waitFor(() => expect(screen.getAllByText('LLM 设置')).toHaveLength(2));
  });

  it('resets to the default panel on the next open after initialMenu was used', async () => {
    const { rerender } = render(
      <SettingsModal isOpen={true} onClose={() => {}} platformAdmin={false} tenantAdmin={false} initialMenu="llm-config" />,
    );
    await waitFor(() => expect(screen.getAllByText('LLM 设置')).toHaveLength(2));

    rerender(<SettingsModal isOpen={false} onClose={() => {}} platformAdmin={false} tenantAdmin={false} />);
    rerender(<SettingsModal isOpen={true} onClose={() => {}} platformAdmin={false} tenantAdmin={false} />);

    expect(screen.getAllByText('知识库上传')).toHaveLength(2);
  });

  it('switches to the LLM 设置 menu when the balance panel jump button is clicked', async () => {
    vi.spyOn(llmConfigApi, 'fetchBalance').mockRejectedValue(
      new llmConfigApi.LlmConfigError('NOT_CONFIGURED', '请先在 LLM 设置中配置模型和 API Key'),
    );

    render(<SettingsModal isOpen={true} onClose={() => {}} platformAdmin={false} tenantAdmin={true} initialMenu="balance" />);
    await waitFor(() => expect(screen.getByRole('button', { name: '去配置' })).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: '去配置' }));

    await waitFor(() => expect(screen.getAllByText('LLM 设置')).toHaveLength(2));
  });
});
