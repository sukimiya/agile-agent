import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import ReviewListPanel from '../../../components/settings/ReviewListPanel';
import * as knowledgeApi from '../../../api/knowledge';

const pendingDoc = {
  id: 'doc-1',
  title: '业务流程说明',
  uploaderUserId: 'sukimiya',
  status: 'PENDING' as const,
  downloadUrl: '/api/knowledge/doc-1/download',
  scopeType: 'GLOBAL',
  tenantId: null,
  orgId: null,
  summary: '这是一份业务流程说明文档',
  uploadedAt: '2026-07-28T00:00:00Z',
  scopeName: '全局',
};

describe('ReviewListPanel', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('loads and shows PENDING documents by default', async () => {
    const listSpy = vi.spyOn(knowledgeApi, 'listKnowledge').mockResolvedValue([pendingDoc]);

    render(<ReviewListPanel />);

    await waitFor(() => expect(screen.getByText('业务流程说明')).toBeInTheDocument());
    expect(listSpy).toHaveBeenCalledWith('PENDING');
    expect(screen.getByText('这是一份业务流程说明文档')).toBeInTheDocument();
  });

  it('shows approve/reject buttons only for PENDING rows', async () => {
    vi.spyOn(knowledgeApi, 'listKnowledge').mockResolvedValue([pendingDoc]);

    render(<ReviewListPanel />);

    await waitFor(() => expect(screen.getByText('业务流程说明')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: '通过' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '拒绝' })).toBeInTheDocument();
  });

  it('does not show approve/reject buttons on the APPROVED tab', async () => {
    vi.spyOn(knowledgeApi, 'listKnowledge').mockResolvedValue([
      { ...pendingDoc, id: 'doc-2' },
    ]);

    render(<ReviewListPanel />);
    fireEvent.click(screen.getByRole('button', { name: '已通过' }));

    await waitFor(() => expect(screen.getByText('业务流程说明')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '通过' })).not.toBeInTheDocument();
  });

  it('clicking 通过 calls approveKnowledge and refetches the list', async () => {
    const listSpy = vi.spyOn(knowledgeApi, 'listKnowledge').mockResolvedValue([pendingDoc]);
    const approveSpy = vi.spyOn(knowledgeApi, 'approveKnowledge').mockResolvedValue(undefined);

    render(<ReviewListPanel />);

    await waitFor(() => expect(screen.getByText('业务流程说明')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: '通过' }));

    await waitFor(() => expect(approveSpy).toHaveBeenCalledWith('doc-1'));
    await waitFor(() => expect(listSpy).toHaveBeenCalledTimes(2));
  });

  it('clicking 拒绝 calls rejectKnowledge and refetches the list', async () => {
    const listSpy = vi.spyOn(knowledgeApi, 'listKnowledge').mockResolvedValue([pendingDoc]);
    const rejectSpy = vi.spyOn(knowledgeApi, 'rejectKnowledge').mockResolvedValue(undefined);

    render(<ReviewListPanel />);

    await waitFor(() => expect(screen.getByText('业务流程说明')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: '拒绝' }));

    await waitFor(() => expect(rejectSpy).toHaveBeenCalledWith('doc-1'));
    await waitFor(() => expect(listSpy).toHaveBeenCalledTimes(2));
  });

  it('shows the scope name badge on each card', async () => {
    vi.spyOn(knowledgeApi, 'listKnowledge').mockResolvedValue([
      pendingDoc,
      { ...pendingDoc, id: 'doc-2', scopeName: '悦安新材' },
    ]);

    render(<ReviewListPanel />);

    await waitFor(() => expect(screen.getByText('全局')).toBeInTheDocument());
    expect(screen.getByText('悦安新材')).toBeInTheDocument();
  });

  it('shows an empty state when there are no documents', async () => {
    vi.spyOn(knowledgeApi, 'listKnowledge').mockResolvedValue([]);

    render(<ReviewListPanel />);

    await waitFor(() => expect(screen.getByText('暂无文档')).toBeInTheDocument());
  });
});
