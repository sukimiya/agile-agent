import { useEffect, useState } from 'react';
import { cycleTheme, persistTheme, readStoredTheme, systemPrefersDark, applyTheme, type ThemeMode } from '../theme';

const OPTIONS: { mode: ThemeMode; label: string }[] = [
  { mode: 'light', label: '白天' },
  { mode: 'dark', label: '夜晚' },
  { mode: 'auto', label: '自动' },
];

export function ThemeSwitch({ compact = false }: { compact?: boolean }) {
  const [mode, setMode] = useState<ThemeMode>(() => readStoredTheme());

  useEffect(() => {
    applyTheme(mode, systemPrefersDark());
  }, [mode]);

  const choose = (next: ThemeMode) => {
    persistTheme(next);
    setMode(next);
  };

  if (compact) {
    const current = OPTIONS.find((option) => option.mode === mode) ?? OPTIONS[2];
    return (
      <button
        type="button"
        aria-label={`主题：${current.label}`}
        onClick={() => choose(cycleTheme(mode))}
        className="w-8 h-8 rounded-full border border-fresh-sky text-fresh-sky text-xs hover:bg-frozen-water transition-colors cursor-pointer"
      >
        {current.label.slice(0, 1)}
      </button>
    );
  }

  return (
    <div role="group" aria-label="主题" className="inline-flex rounded-full border border-stone-200 p-0.5 text-xs">
      {OPTIONS.map((option) => {
        const selected = option.mode === mode;
        return (
          <button
            key={option.mode}
            type="button"
            aria-pressed={selected}
            onClick={() => choose(option.mode)}
            className={`px-2.5 py-1 rounded-full cursor-pointer transition-colors
              ${selected ? 'bg-frozen-water-mountain-lake text-medium-violet-red' : 'text-stone-500 hover:bg-frozen-water'}`}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
