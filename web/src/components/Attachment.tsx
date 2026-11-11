function formatSize(n: number): string {
  if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB';
  if (n >= 1024) return (n / 1024).toFixed(0) + ' KB';
  return n + ' B';
}

function iconFor(name: string): string {
  const ext = name.split('.').pop()?.toLowerCase() ?? '';
  if (['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(ext)) return '🖼️';
  if (ext === 'pdf') return '📄';
  return '📎';
}

interface Props {
  name: string;
  size: number;
  onRemove: () => void;
  error?: string;
}

export function Attachment({ name, size, onRemove, error }: Props) {
  const borderClass = error
    ? 'border-slime-lime bg-slime-lime text-ink'
    : 'border-stone-200 bg-frozen-water text-stone-800';
  return (
    <div
      className={`inline-flex items-center gap-2 px-2 py-1 rounded-md border ${borderClass} text-xs`}
      title={error}
    >
      <span>{iconFor(name)}</span>
      <span className="font-medium truncate max-w-[160px]">{name}</span>
      <span className="text-stone-500">{formatSize(size)}</span>
      <button
        type="button"
        aria-label="移除附件"
        onClick={onRemove}
        className="text-stone-500 hover:text-ink cursor-pointer"
      >
        ❌
      </button>
    </div>
  );
}
