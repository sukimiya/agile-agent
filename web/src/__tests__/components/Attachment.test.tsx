import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Attachment } from '../../components/Attachment';

describe('Attachment', () => {
  it('renders name and size', () => {
    render(<Attachment name="report.pdf" size={1024 * 1024 * 2} onRemove={() => {}} />);
    expect(screen.getByText('report.pdf')).toBeInTheDocument();
    expect(screen.getByText(/2.0 MB/)).toBeInTheDocument();
  });

  it('shows PDF icon for PDF', () => {
    render(<Attachment name="x.pdf" size={100} onRemove={() => {}} />);
    expect(screen.getByText('📄')).toBeInTheDocument();
  });

  it('shows image icon for image', () => {
    render(<Attachment name="x.png" size={100} onRemove={() => {}} />);
    expect(screen.getByText('🖼️')).toBeInTheDocument();
  });

  it('error state has red ring', () => {
    const { container } = render(<Attachment name="x.png" size={100} onRemove={() => {}} error="too big" />);
    expect(container.querySelector('[class*="border-red"]')).toBeTruthy();
    expect(screen.getByTitle('too big')).toBeInTheDocument();
  });

  it('calls onRemove when ❌ clicked', () => {
    const onRemove = vi.fn();
    render(<Attachment name="x.png" size={100} onRemove={onRemove} />);
    fireEvent.click(screen.getByLabelText('移除附件'));
    expect(onRemove).toHaveBeenCalled();
  });
});
