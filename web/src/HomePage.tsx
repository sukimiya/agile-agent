import { authorizeUrl } from './api/auth';
import { useStore } from './store';

const REPO_URL = 'https://github.com/sukimiya/agile-agent';

const FEATURES = [
  {
    title: '相关往事会被找回',
    body: '对话按模型的上下文窗口整理，放不下的旧轮次不会生硬截断。和当前问题相关的内容，会用中文全文检索把原文找回来再交给模型，不必另建向量库。默认先查当前会话，需要时再打开跨会话召回。',
  },
  {
    title: '记得住，也忘得掉',
    body: '系统可以把对话里值得留下的事做成带来源的记忆，并带上相关关系。很久不用的记忆会逐渐淡出，你亲口确认过的不会被自动停用。在聊天里说「忘记…」就会停用对应记忆，说「记起来…」可以恢复。',
  },
  {
    title: '知识库与附件',
    body: '可以上传 Markdown 或文本资料，审核通过后，助手就能查阅后再回答。聊天里也可以附上图片或 PDF。',
  },
  {
    title: '自选模型，还能查余额',
    body: '使用兼容 OpenAI 接口的模型服务，可配置 x.ai、通义千问、DeepSeek 等。对话和余额查询可以分开填写地址和密钥，在设置里查看当前余额。',
  },
  {
    title: 'GitHub 登录，数据按人隔离',
    body: '用 GitHub 账号登录即可开始聊天。会话和配置按租户、按用户分开，模型密钥加密保存在自己的租户配置里。',
  },
] as const;

const REASONS = [
  '以 Apache License 2.0 开源，代码公开，也可用 Docker Compose 自行部署。',
  '长期记忆是产品本身的能力：该记住的留下，该忘掉的听你的，不是事后把聊天记录塞回模型。',
  '不绑定单一模型供应商。密钥由租户自己配置并加密保存，聊天费用走你自己的模型账户。',
  '对话以流式事件返回，回答一边生成一边出现。',
] as const;

const buttonClass = `inline-flex items-center justify-center px-5 py-2.5 rounded-lg bg-fresh-sky text-ink
  text-sm font-medium hover:bg-fresh-sky-dark transition-colors motion-reduce:transition-none`;

function Cta({ signedIn }: { signedIn: boolean }) {
  if (signedIn) {
    return <a href="/" className={buttonClass}>进入聊天</a>;
  }
  return <a href={authorizeUrl()} className={buttonClass}>GitHub 登录</a>;
}

export default function HomePage() {
  const token = useStore((s) => s.token);
  const userName = useStore((s) => s.userName);
  const signedIn = Boolean(token);

  return (
    <div className="min-h-screen bg-canvas text-fg">
      <header className="sticky top-0 z-20 border-b border-stone-200/80 bg-canvas/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <a href="/home" className="text-base font-semibold tracking-tight text-medium-violet-red">
            agile-agent
          </a>
          <Cta signedIn={signedIn} />
        </div>
      </header>

      <main>
        <section className="relative overflow-hidden">
          <div aria-hidden className="pointer-events-none absolute inset-0">
            <div className="absolute left-1/2 top-0 h-[28rem] w-[28rem] -translate-x-1/2 -translate-y-1/3 rounded-full bg-medium-violet-red/25 blur-3xl" />
            <div className="absolute right-[12%] top-24 h-72 w-72 rounded-full bg-fresh-sky/30 blur-3xl" />
            <div className="absolute bottom-0 left-[18%] h-56 w-56 rounded-full bg-frozen-water/80 blur-3xl dark:bg-frozen-water" />
          </div>
          <div className="relative mx-auto max-w-6xl px-6 pb-20 pt-24 sm:pt-32">
            <h1 className="max-w-3xl text-4xl font-semibold tracking-tight text-fg sm:text-6xl">
              记得住，也忘得掉
            </h1>
            <p className="mt-6 max-w-2xl text-base leading-7 text-stone-600 sm:text-lg">
              开源的对话助手。需要时会找回相关往事；不想留的，在聊天里说一声就可以忘掉。
            </p>
            {signedIn && userName && (
              <p className="mt-6 text-sm text-medium-violet-red">欢迎回来，{userName}</p>
            )}
            <div className="mt-8">
              <Cta signedIn={signedIn} />
            </div>
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-6 py-8">
          <ul className="grid grid-cols-1 gap-5 lg:grid-cols-3">
            {FEATURES.map((feature) => (
              <li
                key={feature.title}
                className="rounded-2xl border border-stone-200 bg-surface p-6 shadow-soft transition
                  duration-200 hover:-translate-y-0.5 hover:border-fresh-sky
                  hover:shadow-[0_0_28px_color-mix(in_srgb,var(--color-fresh-sky)_40%,transparent)]
                  motion-reduce:transition-none motion-reduce:hover:translate-y-0"
              >
                <h3 className="text-base font-semibold text-fg">{feature.title}</h3>
                <p className="mt-3 text-sm leading-6 text-stone-600">{feature.body}</p>
              </li>
            ))}
          </ul>
        </section>

        <section className="mt-12 bg-frozen-water-mountain-lake">
          <div className="mx-auto max-w-6xl px-6 py-20">
            <h2 className="text-2xl font-semibold tracking-tight text-fg sm:text-3xl">为什么选择 agile-agent</h2>
            <ul className="mt-8 max-w-3xl space-y-4">
              {REASONS.map((reason) => (
                <li key={reason} className="border-l-2 border-fresh-sky pl-4 text-sm leading-6 text-stone-700">
                  {reason}
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-6 py-20 text-center">
          <Cta signedIn={signedIn} />
        </section>
      </main>

      <footer className="border-t border-stone-200">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-8 text-sm text-stone-500">
          <span className="text-medium-violet-red">agile-agent</span>
          <a
            href={REPO_URL}
            className="underline decoration-fresh-sky decoration-2 underline-offset-4 hover:text-fg"
          >
            开源仓库
          </a>
        </div>
      </footer>
    </div>
  );
}
