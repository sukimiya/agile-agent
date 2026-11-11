import { useEffect, useState } from "react";
import { applyTheme, persistTheme, readStoredTheme, systemPrefersDark, type ThemeMode } from "../../theme";

const OPTIONS: { mode: ThemeMode; label: string; detail: string }[] = [
  { mode: "light", label: "白天", detail: "始终使用浅色" },
  { mode: "dark", label: "夜晚", detail: "始终使用深色" },
  { mode: "auto", label: "自动", detail: "跟随系统设置" },
];

export default function AppearancePanel() {
  const [mode, setMode] = useState<ThemeMode>(() => readStoredTheme());

  useEffect(() => {
    applyTheme(mode, systemPrefersDark());
  }, [mode]);

  const choose = (next: ThemeMode) => {
    persistTheme(next);
    setMode(next);
  };

  return (
    <div>
      <h2 className="text-base font-semibold text-stone-800 mb-1">外观 / 主题</h2>
      <p className="text-sm text-stone-500 mb-4">自动会跟随系统的浅色或深色设置，并在系统切换时立即更新。</p>
      <div role="group" aria-label="主题" className="space-y-2 max-w-md">
        {OPTIONS.map((option) => {
          const selected = option.mode === mode;
          return (
            <button
              key={option.mode}
              type="button"
              aria-pressed={selected}
              onClick={() => choose(option.mode)}
              className={`w-full text-left px-4 py-3 rounded-xl border cursor-pointer transition-colors
                ${selected
                  ? "border-fresh-sky bg-frozen-water-mountain-lake"
                  : "border-stone-200 hover:bg-frozen-water"}`}
            >
              <div className={`text-sm font-medium ${selected ? "text-medium-violet-red" : "text-stone-800"}`}>
                {option.label}
              </div>
              <div className="text-xs text-stone-500 mt-0.5">{option.detail}</div>
            </button>
          );
        })}
      </div>
    </div>
  );
}
