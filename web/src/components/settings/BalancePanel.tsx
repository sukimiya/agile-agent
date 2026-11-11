import { useEffect, useState } from "react";
import { fetchBalance, LlmConfigError, type Balance } from "../../api/llmConfig";

interface Props {
  onGoToLlmConfig?: () => void;
}

export default function BalancePanel({ onGoToLlmConfig }: Props) {
  const [balances, setBalances] = useState<Balance[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [notConfigured, setNotConfigured] = useState(false);

  useEffect(() => {
    setIsLoading(true);
    setError("");
    fetchBalance()
      .then(setBalances)
      .catch((err) => {
        if (err instanceof LlmConfigError && err.code === "NOT_CONFIGURED") {
          setNotConfigured(true);
        }
        setError(err instanceof Error ? err.message : "加载失败");
      })
      .finally(() => setIsLoading(false));
  }, []);

  return (
    <div>
      <h2 className="text-base font-semibold text-stone-800 mb-4">我的余额</h2>

      {error && <p className="text-xs font-medium bg-warning text-ink rounded-md px-2 py-1 mb-3">{error}</p>}

      {notConfigured && onGoToLlmConfig && (
        <button
          type="button"
          onClick={onGoToLlmConfig}
          className="mb-3 px-4 py-2 bg-primary text-white text-sm font-medium rounded-lg
            hover:bg-primary-light transition-colors cursor-pointer"
        >
          去配置
        </button>
      )}

      {isLoading ? (
        <p className="text-sm text-stone-400">加载中...</p>
      ) : !error && balances.length === 0 ? (
        <p className="text-sm text-stone-400">暂无余额信息</p>
      ) : (
        <div className="space-y-2">
          {balances.map((b, i) => (
            <div key={i} className="flex items-center justify-between border border-stone-200 rounded-lg p-3">
              <span className="text-sm text-stone-600">{b.currency}</span>
              <span className="text-sm font-medium text-stone-800">{b.balance}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
