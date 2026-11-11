import { authorizeUrl } from "./api/auth";

interface Props {
  error?: string | null;
}

export default function Login({ error }: Props) {
  return (
    <div className="flex items-center justify-center h-screen bg-slate-50">
      <div className="w-full max-w-sm bg-white p-8 rounded-xl shadow-sm border border-slate-100">
        <h1 className="text-lg font-semibold mb-1">agile-agent</h1>
        <p className="text-sm text-slate-400 mb-6">使用 GitHub 账号登录</p>

        {error && <p className="text-xs text-red-500 mb-4">{error}</p>}

        <a
          href={authorizeUrl()}
          className="block w-full px-4 py-2.5 bg-primary text-white text-sm font-medium rounded-lg
            text-center hover:bg-primary-light transition-colors"
        >
          使用 GitHub 登录
        </a>
      </div>
    </div>
  );
}
