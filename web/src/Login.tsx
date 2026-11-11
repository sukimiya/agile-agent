import { authorizeUrl } from "./api/auth";

interface Props {
  error?: string | null;
}

export default function Login({ error }: Props) {
  return (
    <div className="flex items-center justify-center h-screen bg-canvas">
      <div className="w-full max-w-sm bg-surface p-8 rounded-2xl shadow-soft border border-stone-200">
        <h1 className="text-lg font-semibold mb-1 text-medium-violet-red">agile-agent</h1>
        <p className="text-sm text-stone-500 mb-6">使用 GitHub 账号登录</p>

        {error && <p className="text-xs font-medium bg-slime-lime text-ink rounded-md px-2 py-1 mb-4">{error}</p>}

        <a
          href={authorizeUrl()}
          className="block w-full px-4 py-2.5 bg-fresh-sky text-ink text-sm font-medium rounded-lg
            text-center hover:bg-fresh-sky-dark transition-colors"
        >
          使用 GitHub 登录
        </a>
      </div>
    </div>
  );
}
