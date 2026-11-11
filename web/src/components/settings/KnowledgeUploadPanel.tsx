import { useRef, useState } from "react";
import { uploadKnowledge, KnowledgeError } from "../../api/knowledge";
import type { KnowledgeStatus } from "../../api/knowledge";

const ALLOWED_EXTENSIONS = [".md", ".markdown", ".txt"];

function hasAllowedExtension(name: string): boolean {
  const lower = name.toLowerCase();
  return ALLOWED_EXTENSIONS.some((ext) => lower.endsWith(ext));
}

function UploadCloudIcon() {
  return (
    <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="mx-auto text-stone-400">
      <path d="M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242" />
      <path d="M12 12v9" />
      <path d="m16 16-4-4-4 4" />
    </svg>
  );
}

interface Props {
  heading: string;
}

export default function KnowledgeUploadPanel({ heading }: Props) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<{ summary: string; status: KnowledgeStatus } | null>(null);

  const processFile = (f: File | undefined) => {
    setResult(null);
    if (!f) return;
    if (!hasAllowedExtension(f.name)) {
      setError("仅支持 .md / .markdown / .txt 文本文档");
      setFile(null);
      return;
    }
    setError("");
    setFile(f);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    e.target.value = "";
    processFile(f);
  };

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    if (isLoading) return;
    setIsDragging(true);
  };

  const handleDragLeave = () => setIsDragging(false);

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
    if (isLoading) return;
    processFile(e.dataTransfer.files?.[0]);
  };

  const handleUpload = async () => {
    if (!file) return;
    setIsLoading(true);
    setError("");
    setResult(null);
    try {
      const uploadResult = await uploadKnowledge(file, title.trim() || undefined);
      setResult(uploadResult);
      setFile(null);
      setTitle("");
    } catch (err) {
      setError(err instanceof KnowledgeError ? err.reason : "上传失败，请重试");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div>
      <h2 className="text-base font-semibold text-stone-800 mb-4">{heading}</h2>

      <label htmlFor="knowledge-title" className="block text-sm text-stone-600 mb-1">标题（可选）</label>
      <input
        id="knowledge-title"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        disabled={isLoading}
        className="w-full px-3 py-2 mb-4 border border-fresh-sky rounded-lg text-sm shadow-input
          focus:outline-none focus:border-fresh-sky focus:shadow-input-focus"
      />

      <label htmlFor="knowledge-file" className="block text-sm text-stone-600 mb-1">选择文件</label>
      <div
        role="button"
        tabIndex={0}
        aria-label="拖拽或点击上传文件"
        onClick={() => fileInputRef.current?.click()}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") fileInputRef.current?.click();
        }}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        className={`mb-4 border-2 border-dashed rounded-lg px-4 py-8 text-center cursor-pointer transition-colors
          ${isLoading ? "opacity-50 cursor-not-allowed" : ""}
          ${isDragging ? "border-fresh-sky bg-frozen-water-mountain-lake" : "border-fresh-sky hover:bg-frozen-water"}`}
      >
        <input
          id="knowledge-file"
          type="file"
          accept=".md,.markdown,.txt"
          ref={fileInputRef}
          onChange={handleFileChange}
          disabled={isLoading}
          className="hidden"
        />
        <UploadCloudIcon />
        <p className="text-sm text-stone-600 mt-2">
          {file ? `已选择：${file.name}` : "点击选择文件，或将文件拖拽到此处"}
        </p>
        <p className="text-xs text-stone-400 mt-1">支持 .md / .markdown / .txt</p>
      </div>

      {error && <p className="text-xs font-medium bg-slime-lime text-ink rounded-md px-2 py-1 mb-4">{error}</p>}
      {result !== null && (
        <div className="text-xs text-stone-600 bg-stone-50 border border-stone-200 rounded-lg p-3 mb-4">
          <p className="font-medium text-stone-700 mb-1">
            {result.status === "APPROVED" ? "上传成功，已生效。摘要：" : "已提交，等待租户管理员审批。摘要："}
          </p>
          <p>{result.summary || "（未生成摘要）"}</p>
        </div>
      )}

      <button
        type="button"
        onClick={handleUpload}
        disabled={!file || isLoading}
        className="px-4 py-2 bg-fresh-sky text-ink text-sm font-medium rounded-lg
          hover:bg-fresh-sky-dark disabled:opacity-40 disabled:cursor-not-allowed
          transition-colors cursor-pointer"
      >
        {isLoading ? "上传中..." : "上传"}
      </button>
    </div>
  );
}
