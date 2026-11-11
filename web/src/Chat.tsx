import { useRef, useEffect, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { oneDark } from "react-syntax-highlighter/dist/esm/styles/prism";
import { Attachment } from "./components/Attachment";
import { DownloadButton } from "./components/DownloadButton";
import { useStore } from "./store";
import { listHints, type Hint } from "./api/hints";

interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
  attachments?: { name: string }[];
  usage?: { promptTokens: number; completionTokens: number; totalTokens: number };
  action?: "open-llm-settings";
}

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'application/pdf'];
const MAX_SIZE = 10 * 1024 * 1024;
const MAX_FILES = 10;

// Fallback for messages with no real usage attached yet (still streaming) or loaded from
// history (past turns' real usage isn't persisted, only the message text is).
function estimateTokens(text: string): number {
  return Math.ceil(text.length / 1.5);
}

function tokensFor(m: Message): number {
  return m.usage?.totalTokens ?? estimateTokens(m.content);
}

function formatTokens(n: number): string {
  if (n >= 1_000_000_000) return (n / 1_000_000_000).toFixed(1) + "G";
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + "M";
  if (n >= 1_000) return (n / 1_000).toFixed(1) + "K";
  return String(n);
}

function ArrowIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <line x1="5" y1="12" x2="19" y2="12" />
      <polyline points="12 5 19 12 12 19" />
    </svg>
  );
}

function PaperclipIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
    </svg>
  );
}

function CopyIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="20 6 9 17 4 12" />
    </svg>
  );
}

function CopyButton({ content }: { content: string }) {
  const [copied, setCopied] = useState(false);

  const handleClick = async () => {
    try {
      await navigator.clipboard.writeText(content);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      /* clipboard unavailable */
    }
  };

  return (
    <button
      type="button"
      onClick={handleClick}
      aria-label={copied ? "已复制" : "复制"}
      title={copied ? "已复制" : "复制"}
      className="text-stone-400 hover:text-stone-700 cursor-pointer p-1 rounded hover:bg-stone-100 transition-colors"
    >
      {copied ? <CheckIcon /> : <CopyIcon />}
    </button>
  );
}

interface ChatProps {
  messages: Message[];
  isLoading: boolean;
  input: string;
  onInputChange: (v: string) => void;
  onSubmit: (text?: string) => void;
  onOpenLlmSettings?: () => void;
}

export default function Chat({
  messages,
  isLoading,
  input,
  onInputChange,
  onSubmit,
  onOpenLlmSettings,
}: ChatProps) {
  const bottomRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const pendingFiles = useStore((s) => s.pendingFiles);
  const addFiles = useStore((s) => s.addFiles);
  const removeFile = useStore((s) => s.removeFile);
  const [fileError, setFileError] = useState<string>("");

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    if (files.length === 0) return;

    if (pendingFiles.length + files.length > MAX_FILES) {
      setFileError(`一次最多 ${MAX_FILES} 个文件`);
      e.target.value = "";
      return;
    }
    for (const f of files) {
      if (!ALLOWED_TYPES.includes(f.type)) {
        setFileError(`${f.name}：仅支持图片和 PDF（Word/Excel 请先导出为 PDF）`);
        e.target.value = "";
        return;
      }
      if (f.size > MAX_SIZE) {
        setFileError(`${f.name}：超出 10 MB 上限`);
        e.target.value = "";
        return;
      }
    }
    addFiles(files);
    setFileError("");
    e.target.value = "";
  };

  const [hints, setHints] = useState<Hint[]>([]);

  useEffect(() => {
    listHints().then(setHints).catch(() => setHints([]));
  }, []);

  return (
    <div className="flex flex-col h-full">
      {/* Messages area */}
      <div className="flex-1 overflow-y-auto px-4 py-6">
      <div className="max-w-3xl mx-auto space-y-6">
        {messages.length === 0 && (
          <div className="flex flex-col items-center justify-center h-full text-stone-400">
            <p className="text-lg mb-6">有什么可以帮你的？</p>
            <div className="flex gap-2 flex-wrap justify-center">
              {hints.map((h) => (
                <button
                  key={h.id}
                  onClick={() => onSubmit(h.content)}
                  className="px-4 py-2 rounded-full border border-fresh-sky bg-surface text-sm text-fresh-sky
                    hover:bg-frozen-water transition-colors cursor-pointer"
                >
                  {h.content}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((m) => (
          <div
            key={m.id}
            className={`flex gap-3 ${m.role === "user" ? "justify-end" : ""}`}
          >
            {m.role === "assistant" && (
              <div className="w-8 h-8 rounded-full flex items-center justify-center text-xs font-medium shrink-0 bg-frozen-water-mountain-lake text-medium-violet-red">
                AI
              </div>
            )}
            <div
              className={`max-w-[80%] px-4 py-3 rounded-2xl text-sm leading-relaxed
                ${m.role === "user"
                  ? "bg-ink text-white"
                  : "bg-surface border border-stone-200 shadow-soft"
                }`}
            >
              {m.role === "assistant" ? (
                <>
                  <MarkdownContent content={m.content} />
                  {m.action === "open-llm-settings" && onOpenLlmSettings && (
                    <div className="mt-2">
                      <button
                        type="button"
                        onClick={onOpenLlmSettings}
                        className="px-4 py-2 bg-fresh-sky text-ink text-sm font-medium rounded-lg
                          hover:bg-fresh-sky-dark transition-colors cursor-pointer"
                      >
                        去配置
                      </button>
                    </div>
                  )}
                  <div className="flex items-center justify-end gap-2 mt-1">
                    <CopyButton content={m.content} />
                    <DownloadButton content={m.content} />
                    <span className="text-xs text-stone-400">
                      ~{formatTokens(tokensFor(m))} tokens
                    </span>
                  </div>
                </>
              ) : (
                <>
                  {m.attachments && m.attachments.length > 0 && (
                    <div className="flex flex-wrap gap-1.5 mb-2">
                      {m.attachments.map((a, i) => (
                        <span key={i} className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-white/20 text-xs">
                          📎 {a.name}
                        </span>
                      ))}
                    </div>
                  )}
                  <p className="whitespace-pre-wrap">{m.content}</p>
                  <div className="text-xs text-white/70 mt-1 text-right">
                    ~{formatTokens(tokensFor(m))} tokens
                  </div>
                </>
              )}
            </div>
          </div>
        ))}

        {isLoading && (
          <div className="flex gap-3">
            <div className="w-8 h-8 rounded-full flex items-center justify-center text-xs font-medium shrink-0 bg-frozen-water-mountain-lake text-medium-violet-red">
              AI
            </div>
            <div className="bg-surface border border-stone-200 px-4 py-3 rounded-2xl">
              <span className="text-stone-400 text-sm">思考中...</span>
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>
      </div>

      <div className="max-w-3xl mx-auto w-full">
        {pendingFiles.length > 0 && (
          <div className="px-4 pb-2 flex flex-wrap gap-2">
            {pendingFiles.map((f, i) => (
              <Attachment
                key={i}
                name={f.name}
                size={f.size}
                onRemove={() => removeFile(i)}
              />
            ))}
          </div>
        )}
        {fileError && (
          <div className="px-4 pb-1"><p className="inline-block text-xs font-medium bg-slime-lime text-ink rounded-md px-2 py-1">{fileError}</p></div>
        )}

        {/* Input */}
        <form
          onSubmit={(e) => {
            e.preventDefault();
            onSubmit();
          }}
          className="p-4"
        >
          <div className="flex gap-2 items-center px-2 py-1.5 rounded-full border border-fresh-sky bg-surface shadow-input
            focus-within:border-fresh-sky focus-within:shadow-input-focus transition-colors">
            <input
              type="file"
              multiple
              accept="image/jpeg,image/png,image/gif,image/webp,application/pdf"
              ref={fileInputRef}
              onChange={handleFileSelect}
              className="hidden"
            />
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={isLoading || pendingFiles.length >= MAX_FILES}
              className="p-2 text-fresh-sky disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer shrink-0"
              aria-label="上传文件"
            >
              <PaperclipIcon />
            </button>
            <input
              value={input || ""}
              onChange={(e) => onInputChange(e.target.value)}
              placeholder="输入您的问题..."
              className="flex-1 min-w-0 bg-transparent text-sm focus:outline-none disabled:opacity-50"
              disabled={isLoading}
            />
            <button
              type="submit"
              disabled={isLoading || (!(input || "").trim() && pendingFiles.length === 0)}
              aria-label="发送"
              className="w-9 h-9 shrink-0 rounded-full flex items-center justify-center
                bg-fresh-sky text-ink hover:bg-fresh-sky-dark disabled:bg-stone-200 disabled:text-stone-400
                disabled:cursor-not-allowed transition-colors cursor-pointer"
            >
              <ArrowIcon />
            </button>
          </div>
          {messages.length > 0 && (
            <div className="px-2 pt-2 text-xs text-stone-400">
              已用 ~{formatTokens(messages.reduce((sum, m) => sum + tokensFor(m), 0))} tokens
            </div>
          )}
        </form>
      </div>
    </div>
  );
}

// --- Markdown rendering with syntax highlighting ---
function fixTables(text: string): string {
  const lines = text.split("\n");
  for (let i = 1; i < lines.length - 1; i++) {
    // Fix separator rows: match column count to header
    if (/^\|[-:| ]+\|$/.test(lines[i]) && /^\|.+\|$/.test(lines[i - 1])) {
      const headerCols = (lines[i - 1].match(/\|/g) || []).length - 1;
      const sepCols = (lines[i].match(/\|/g) || []).length - 1;
      if (headerCols !== sepCols) {
        const sep = "|" + "---|".repeat(headerCols);
        lines[i] = sep;
      }
    }
  }
  return lines.join("\n");
}

function MarkdownContent({ content }: { content: string }) {
  const fixed = fixTables(content);
  return (
    <div className="markdown-body">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          code({ className, children, ...props }) {
            const match = /language-(\w+)/.exec(className || "");
            const codeStr = String(children).replace(/\n$/, "");
            const nodeProps = props as Record<string, unknown>;
            if (match) {
              return (
                <SyntaxHighlighter
                  style={oneDark}
                  language={match[1]}
                  PreTag="div"
                >
                  {codeStr}
                </SyntaxHighlighter>
              );
            }
            const inline = nodeProps.inline;
            if (inline) {
              return <code className="bg-stone-100 px-1.5 py-0.5 rounded text-xs">{children}</code>;
            }
            return (
              <pre className="bg-stone-800 text-stone-100 p-3 rounded-lg overflow-x-auto text-xs">
                <code>{children}</code>
              </pre>
            );
          },
        }}
      >
        {fixed}
      </ReactMarkdown>
    </div>
  );
}
