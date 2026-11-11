import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import KnowledgeUploadPanel from '../../../components/settings/KnowledgeUploadPanel';
import * as knowledgeApi from '../../../api/knowledge';

describe('KnowledgeUploadPanel', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders the given heading', () => {
    render(<KnowledgeUploadPanel heading="知识库上传" />);
    expect(screen.getByText('知识库上传')).toBeInTheDocument();
  });

  it('uploads the selected file and shows the returned summary on success', async () => {
    const uploadSpy = vi.spyOn(knowledgeApi, 'uploadKnowledge').mockResolvedValue({
      id: 'doc-1',
      summary: '这是自动生成的摘要',
      status: 'APPROVED',
    });

    render(<KnowledgeUploadPanel heading="知识库上传" />);

    const file = new File(['# hello'], 'note.md', { type: 'text/markdown' });
    const input = screen.getByLabelText('选择文件');
    fireEvent.change(input, { target: { files: [file] } });

    fireEvent.click(screen.getByRole('button', { name: '上传' }));

    await waitFor(() => expect(screen.getByText('这是自动生成的摘要')).toBeInTheDocument());
    expect(screen.getByText('上传成功，已生效。摘要：')).toBeInTheDocument();
    expect(uploadSpy).toHaveBeenCalledWith(file, undefined);
  });

  it('shows an approval-pending message when status is PENDING', async () => {
    vi.spyOn(knowledgeApi, 'uploadKnowledge').mockResolvedValue({
      id: 'doc-1',
      summary: '这是自动生成的摘要',
      status: 'PENDING',
    });

    render(<KnowledgeUploadPanel heading="知识库上传" />);

    const file = new File(['# hello'], 'note.md', { type: 'text/markdown' });
    fireEvent.change(screen.getByLabelText('选择文件'), { target: { files: [file] } });
    fireEvent.click(screen.getByRole('button', { name: '上传' }));

    await waitFor(() =>
      expect(screen.getByText('已提交，等待租户管理员审批。摘要：')).toBeInTheDocument(),
    );
    expect(screen.getByText('这是自动生成的摘要')).toBeInTheDocument();
  });

  it('passes the title field when filled in', async () => {
    const uploadSpy = vi.spyOn(knowledgeApi, 'uploadKnowledge').mockResolvedValue({
      id: 'doc-1',
      summary: '摘要',
      status: 'APPROVED',
    });

    render(<KnowledgeUploadPanel heading="知识库上传" />);

    const file = new File(['# hello'], 'note.md', { type: 'text/markdown' });
    fireEvent.change(screen.getByLabelText('选择文件'), { target: { files: [file] } });
    fireEvent.change(screen.getByLabelText('标题（可选）'), { target: { value: '我的文档' } });
    fireEvent.click(screen.getByRole('button', { name: '上传' }));

    await waitFor(() => expect(uploadSpy).toHaveBeenCalledWith(file, '我的文档'));
  });

  it('shows the error reason when upload fails', async () => {
    vi.spyOn(knowledgeApi, 'uploadKnowledge').mockRejectedValue(
      new knowledgeApi.KnowledgeError('BAD_TYPE', '仅支持文本文档'),
    );

    render(<KnowledgeUploadPanel heading="知识库上传" />);

    const file = new File(['x'], 'note.md', { type: 'text/markdown' });
    fireEvent.change(screen.getByLabelText('选择文件'), { target: { files: [file] } });
    fireEvent.click(screen.getByRole('button', { name: '上传' }));

    await waitFor(() => expect(screen.getByText('仅支持文本文档')).toBeInTheDocument());
  });

  it('rejects unsupported file extensions before calling the API', () => {
    const uploadSpy = vi.spyOn(knowledgeApi, 'uploadKnowledge');

    render(<KnowledgeUploadPanel heading="知识库上传" />);

    const file = new File(['x'], 'note.pdf', { type: 'application/pdf' });
    fireEvent.change(screen.getByLabelText('选择文件'), { target: { files: [file] } });

    expect(screen.getByText(/仅支持/)).toBeInTheDocument();
    expect(uploadSpy).not.toHaveBeenCalled();
  });

  it('disables the upload button until a file is selected', () => {
    render(<KnowledgeUploadPanel heading="知识库上传" />);
    expect(screen.getByRole('button', { name: '上传' })).toBeDisabled();
  });

  it('accepts a file dropped onto the dropzone', async () => {
    const uploadSpy = vi.spyOn(knowledgeApi, 'uploadKnowledge').mockResolvedValue({
      id: 'doc-1',
      summary: '摘要',
      status: 'APPROVED',
    });

    render(<KnowledgeUploadPanel heading="知识库上传" />);

    const file = new File(['# hello'], 'note.md', { type: 'text/markdown' });
    const dropzone = screen.getByRole('button', { name: '拖拽或点击上传文件' });
    fireEvent.drop(dropzone, { dataTransfer: { files: [file] } });

    fireEvent.click(screen.getByRole('button', { name: '上传' }));

    await waitFor(() => expect(uploadSpy).toHaveBeenCalledWith(file, undefined));
  });

  it('shows an error when a dropped file has an unsupported extension', () => {
    const uploadSpy = vi.spyOn(knowledgeApi, 'uploadKnowledge');

    render(<KnowledgeUploadPanel heading="知识库上传" />);

    const file = new File(['x'], 'note.pdf', { type: 'application/pdf' });
    const dropzone = screen.getByRole('button', { name: '拖拽或点击上传文件' });
    fireEvent.drop(dropzone, { dataTransfer: { files: [file] } });

    expect(screen.getByText(/仅支持/)).toBeInTheDocument();
    expect(uploadSpy).not.toHaveBeenCalled();
  });

  it('highlights the dropzone while dragging over it, and un-highlights on drag leave', () => {
    render(<KnowledgeUploadPanel heading="知识库上传" />);

    const dropzone = screen.getByRole('button', { name: '拖拽或点击上传文件' });
    expect(dropzone.className).not.toMatch(/bg-frozen-water-mountain-lake/);

    fireEvent.dragOver(dropzone);
    expect(dropzone.className).toMatch(/bg-frozen-water-mountain-lake/);

    fireEvent.dragLeave(dropzone);
    expect(dropzone.className).not.toMatch(/bg-frozen-water-mountain-lake/);
  });
});
