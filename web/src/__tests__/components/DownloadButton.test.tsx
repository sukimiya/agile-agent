import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { DownloadButton, extractDownloadLinks } from '../../components/DownloadButton';

describe('extractDownloadLinks', () => {
  it('finds .xlsx and .pdf links', () => {
    const text = '看这个 [报表.xlsx](http://x.com/r.xlsx) 和 [合同.pdf](http://y.com/c.pdf)';
    const links = extractDownloadLinks(text);
    expect(links).toEqual([
      { label: '报表.xlsx', url: 'http://x.com/r.xlsx' },
      { label: '合同.pdf', url: 'http://y.com/c.pdf' },
    ]);
  });

  it('ignores links without download extensions', () => {
    const text = 'see [page](http://x.com/page) and [stuff](http://x.com/path)';
    expect(extractDownloadLinks(text)).toEqual([]);
  });

  it('matches .md .csv .zip .docx .doc .xls .txt', () => {
    const text = '[a.md](http://x/a.md) [b.csv](http://x/b.csv) [c.zip](http://x/c.zip) [d.docx](http://x/d.docx) [e.doc](http://x/e.doc) [f.xls](http://x/f.xls) [g.txt](http://x/g.txt)';
    expect(extractDownloadLinks(text)).toHaveLength(7);
  });

  it('matches image extensions .png .jpg .gif', () => {
    const text = '[img.png](http://x/img.png) [pic.jpg](http://x/pic.jpg) [anim.gif](http://x/anim.gif)';
    expect(extractDownloadLinks(text)).toHaveLength(3);
  });

  it('extracts bare URL ending in download extension', () => {
    const text = '附件下载：http://x.com/file.png 可以打开看。';
    const links = extractDownloadLinks(text);
    expect(links).toHaveLength(1);
    expect(links[0].url).toBe('http://x.com/file.png');
    expect(links[0].label).toBe('file.png');
  });

  it('handles MinIO presigned URL with query string', () => {
    const text = 'https://minio.x/bucket/r.pdf?X-Amz-Signature=abc';
    const links = extractDownloadLinks(text);
    expect(links).toHaveLength(1);
    expect(links[0].label).toBe('r.pdf');
  });

  it('dedupes between markdown link and bare URL', () => {
    const text = '[报表](http://x/r.xlsx) 也可以直接打开 http://x/r.xlsx';
    expect(extractDownloadLinks(text)).toHaveLength(1);
  });

  it('strips trailing CJK punctuation from bare URL', () => {
    const text = '链接：http://x/file.pdf。';
    const links = extractDownloadLinks(text);
    expect(links[0].url).toBe('http://x/file.pdf');
  });
});

describe('DownloadButton', () => {
  it('saves text as .md when no download links found', () => {
    const createUrl = vi.fn(() => 'blob:fake');
    const revokeUrl = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', { value: createUrl });
    Object.defineProperty(URL, 'revokeObjectURL', { value: revokeUrl });

    render(<DownloadButton content="纯文本内容" />);
    fireEvent.click(screen.getByLabelText('下载'));
    expect(createUrl).toHaveBeenCalled();
  });

  it('opens link directly for single download link', () => {
    const open = vi.spyOn(window, 'open').mockImplementation(() => null);
    render(<DownloadButton content="见 [r.xlsx](http://x.com/r.xlsx)" />);
    fireEvent.click(screen.getByLabelText('下载'));
    expect(open).toHaveBeenCalledWith('http://x.com/r.xlsx', '_blank');
  });

  it('shows dropdown menu for multiple links', () => {
    render(<DownloadButton content="[a.xlsx](http://x/a.xlsx) [b.pdf](http://x/b.pdf)" />);
    fireEvent.click(screen.getByLabelText('下载'));
    expect(screen.getByText('a.xlsx')).toBeInTheDocument();
    expect(screen.getByText('b.pdf')).toBeInTheDocument();
  });
});
