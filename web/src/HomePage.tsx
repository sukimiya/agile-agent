import { useEffect, useRef, useState } from 'react';
import { authorizeUrl } from './api/auth';
import { HOME_COPY, readHomeLocale, subtitleLines, writeHomeLocale, type HomeLocale } from './homeCopy';
import { mountHomeHero } from './homeHeroScene';
import { useStore } from './store';

const REPO_URL = 'https://github.com/sukimiya/agile-agent';

function Cta({ signedIn, label, className }: { signedIn: boolean; label: string; className: string }) {
  if (signedIn) {
    return <a href="/" className={className}>{label}</a>;
  }
  return <a href={authorizeUrl()} className={className}>{label}</a>;
}

export default function HomePage() {
  const token = useStore((s) => s.token);
  const userName = useStore((s) => s.userName);
  const signedIn = Boolean(token);
  const [locale, setLocale] = useState<HomeLocale>(() => readHomeLocale());
  const copy = HOME_COPY[locale];
  const [lead, tail] = subtitleLines(copy.subtitle);
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    return mountHomeHero(canvas);
  }, []);

  const choose = (next: HomeLocale) => {
    writeHomeLocale(next);
    setLocale(next);
  };

  return (
    <div className="home-root">
      <div className="home-hero">
        <canvas ref={canvasRef} className="home-hero-canvas" aria-hidden="true" />
        <header className="home-header">
          <a href="/home" className="home-logo">agile-agent</a>
          <div className="home-header-actions">
            <div className="home-lang" role="group" aria-label={copy.languageLabel}>
              <button type="button" aria-pressed={locale === 'en'} onClick={() => choose('en')}>EN</button>
              <span aria-hidden="true">/</span>
              <button type="button" aria-pressed={locale === 'zh'} onClick={() => choose('zh')}>中文</button>
            </div>
            <Cta signedIn={signedIn} label={signedIn ? copy.openChat : copy.signIn} className="home-btn home-btn--login" />
          </div>
        </header>

        <div className="home-copy">
          <h1>{copy.title}</h1>
          <p>
            {lead}
            {tail ? <><br />{tail}</> : null}
          </p>
          {signedIn && userName ? <p className="home-welcome">{copy.welcome(userName)}</p> : null}
          <Cta signedIn={signedIn} label={signedIn ? copy.openChat : copy.signIn} className="home-btn home-btn--cta" />
        </div>
      </div>

      <main>
        <section className="mx-auto max-w-6xl px-6 py-8">
          <ul className="grid grid-cols-1 gap-5 lg:grid-cols-3">
            {copy.features.map((feature) => (
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
            <h2 className="text-2xl font-semibold tracking-tight text-fg sm:text-3xl">{copy.whyTitle}</h2>
            <ul className="mt-8 max-w-3xl space-y-4">
              {copy.reasons.map((reason) => (
                <li key={reason} className="border-l-2 border-fresh-sky pl-4 text-sm leading-6 text-stone-700">
                  {reason}
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-6 py-20 text-center">
          <Cta signedIn={signedIn} label={signedIn ? copy.openChat : copy.signIn} className="home-btn home-btn--repeat" />
        </section>
      </main>

      <footer className="border-t border-stone-200">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-8 text-sm text-stone-500">
          <span className="text-medium-violet-red">agile-agent</span>
          <a
            href={REPO_URL}
            className="underline decoration-fresh-sky decoration-2 underline-offset-4 hover:text-fg"
          >
            {copy.repo}
          </a>
        </div>
      </footer>
    </div>
  );
}
