export type HomeLocale = 'en' | 'zh';

export const HOME_LOCALE_KEY = 'home-locale';

export function readHomeLocale(storage: Pick<Storage, 'getItem'> = localStorage): HomeLocale {
  return storage.getItem(HOME_LOCALE_KEY) === 'zh' ? 'zh' : 'en';
}

export function writeHomeLocale(locale: HomeLocale, storage: Pick<Storage, 'setItem'> = localStorage): void {
  storage.setItem(HOME_LOCALE_KEY, locale);
}

export interface HomeCopy {
  languageLabel: string;
  title: string;
  subtitle: string;
  signIn: string;
  openChat: string;
  welcome: (name: string) => string;
  repo: string;
  whyTitle: string;
  features: readonly { title: string; body: string }[];
  reasons: readonly string[];
}

const en: HomeCopy = {
  languageLabel: 'Language',
  title: 'She remembers, and she can forget',
  subtitle: 'An open-source conversation assistant. It recalls relevant history when you need it. Tell it in the chat what to forget.',
  signIn: 'Sign in with GitHub',
  openChat: 'Open chat',
  welcome: (name) => `Welcome back, ${name}`,
  repo: 'Source on GitHub',
  whyTitle: 'Why agile-agent',
  features: [
    {
      title: 'Search brings back relevant history',
      body: "The app fits each conversation to the model's context window. Older turns that do not fit stay intact. Chinese full-text search returns the original text for the current question. You do not need a vector database. Search starts in the current conversation. You can turn on recall across conversations.",
    },
    {
      title: 'She remembers, and she can forget',
      body: 'The app saves facts worth keeping, each with a source, and stores links between them. Memories you do not use fade. A memory you confirmed stays active. In chat, say "forget ..." to retire a memory. Say "remember ..." to restore it.',
    },
    {
      title: 'Knowledge base and attachments',
      body: 'Upload Markdown or text. After review, the assistant can read the file before it answers. You can attach an image or a PDF in chat.',
    },
    {
      title: 'Choose a model and check the balance',
      body: 'Use a service with an OpenAI-compatible API, such as x.ai, Tongyi Qianwen, or DeepSeek. Chat and balance lookup can use different addresses and keys. Settings shows the current balance.',
    },
    {
      title: 'GitHub sign-in keeps data per person',
      body: 'Sign in with GitHub to start a chat. Conversations and settings are separate for each tenant and each user. Model keys are encrypted in that tenant config.',
    },
  ],
  reasons: [
    'The code is Apache License 2.0. You can deploy it with Docker Compose.',
    'Long-term memory is built in. It keeps what you ask it to keep. It forgets what you ask it to forget. It does not paste the old chat log into the model.',
    'You do not have to use one model vendor. You set the keys. The tenant config stores them encrypted. Chat charges go to your model account.',
    'Replies arrive as a stream of events. The answer appears while the model writes it.',
  ],
};

const zh: HomeCopy = {
  languageLabel: '语言',
  title: '记得住，也忘得掉',
  subtitle: '开源的对话助手。需要时会找回相关往事。不想留的，在聊天里说一声就可以忘掉。',
  signIn: 'GitHub 登录',
  openChat: '进入聊天',
  welcome: (name) => `欢迎回来，${name}`,
  repo: '开源仓库',
  whyTitle: '为什么选择 agile-agent',
  features: [
    {
      title: '相关往事会被找回',
      body: '对话按模型的上下文窗口整理，放不下的旧轮次不会生硬截断。和当前问题相关的内容，会用中文全文检索把原文找回来再交给模型，不必另建向量库。默认先查当前会话，需要时再打开跨会话召回。',
    },
    {
      title: '记得住，也忘得掉',
      body: '系统可以把对话里值得留下的事做成带来源的记忆，并带上相关关系。很久不用的记忆会逐渐淡出。你亲口确认过的不会被自动停用。在聊天里说"忘记…"就会停用对应记忆。说"记起来…"可以恢复。',
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
  ],
  reasons: [
    '以 Apache License 2.0 开源，代码公开，也可用 Docker Compose 自行部署。',
    '长期记忆是产品本身的能力：该记住的留下，该忘掉的听你的，不是事后把聊天记录塞回模型。',
    '不绑定单一模型供应商。密钥由租户自己配置并加密保存，聊天费用走你自己的模型账户。',
    '对话以流式事件返回，回答一边生成一边出现。',
  ],
};

export const HOME_COPY: Record<HomeLocale, HomeCopy> = { en, zh };

/** Split the hero subtitle on the semicolon so the second clause can sit on its own line. */
export function subtitleLines(subtitle: string): [string, string] {
  const latin = subtitle.indexOf(';');
  const at = latin >= 0 ? latin : subtitle.indexOf('；');
  if (at < 0) return [subtitle, ''];
  return [subtitle.slice(0, at + 1), subtitle.slice(at + 1)];
}
