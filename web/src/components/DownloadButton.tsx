import { useState } from 'react';

const DOWNLOAD_EXT_RE = /\.(xlsx|xls|pdf|csv|zip|docx|doc|md|txt|png|jpg|jpeg|gif|webp)(?:[?#]|$)/i;

export interface DownloadLink {
  label: string;
  url: string;
}

function fileNameFromUrl(url: string): string {
  try {
    const u = new URL(url);
    const last = u.pathname.split('/').filter(Boolean).pop();
    return last ? decodeURIComponent(last) : u.hostname;
  } catch {
    return url;
  }
}

export function extractDownloadLinks(content: string): DownloadLink[] {
  const links: DownloadLink[] = [];
  const seen = new Set<string>();

  const mdRe = /\[([^\]]+)\]\(([^)]+)\)/g;
  let m: RegExpExecArray | null;
  while ((m = mdRe.exec(content)) !== null) {
    if (DOWNLOAD_EXT_RE.test(m[1]) || DOWNLOAD_EXT_RE.test(m[2])) {
      if (!seen.has(m[2])) {
        seen.add(m[2]);
        links.push({ label: m[1], url: m[2] });
      }
    }
  }

  const urlRe = /https?:\/\/\S+/g;
  while ((m = urlRe.exec(content)) !== null) {
    const url = m[0].replace(/[.,;。，；：、!?！？)）]+$/u, '');
    if (DOWNLOAD_EXT_RE.test(url) && !seen.has(url)) {
      seen.add(url);
      links.push({ label: fileNameFromUrl(url), url });
    }
  }

  return links;
}

function saveAsMarkdown(content: string) {
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `chat-${Date.now()}.md`;
  a.click();
  URL.revokeObjectURL(url);
}

function UploadIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
      <polyline points="17 8 12 3 7 8" />
      <line x1="12" y1="3" x2="12" y2="15" />
    </svg>
  );
}

interface Props { content: string }

export function DownloadButton({ content }: Props) {
  const [open, setOpen] = useState(false);
  const links = extractDownloadLinks(content);

  const handleClick = () => {
    if (links.length === 0) {
      saveAsMarkdown(content);
    } else if (links.length === 1) {
      window.open(links[0].url, '_blank');
    } else {
      setOpen((v) => !v);
    }
  };

  return (
    <div className="relative inline-block">
      <button
        type="button"
        onClick={handleClick}
        aria-label="下载"
        title={links.length > 1 ? `下载（${links.length}）` : '下载'}
        className="text-stone-400 hover:text-stone-700 cursor-pointer p-1 rounded hover:bg-stone-100 transition-colors"
      >
        <UploadIcon />
      </button>
      {open && links.length > 1 && (
        <div className="absolute right-0 mt-1 bg-white border border-stone-200 rounded shadow-md py-1 min-w-[160px] z-10">
          {links.map((l, i) => (
            <a
              key={i}
              href={l.url}
              target="_blank"
              rel="noopener noreferrer"
              onClick={() => setOpen(false)}
              className="block px-3 py-1 text-xs hover:bg-stone-50"
            >
              {l.label}
            </a>
          ))}
        </div>
      )}
    </div>
  );
}
