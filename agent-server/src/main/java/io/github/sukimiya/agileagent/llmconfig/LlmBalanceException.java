package io.github.sukimiya.agileagent.llmconfig;

public final class LlmBalanceException extends RuntimeException {
    private final int status;
    private final String code;

    public LlmBalanceException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }

    public static LlmBalanceException unsupported() {
        return new LlmBalanceException(404, "LLM_BALANCE_UNSUPPORTED", "Provider does not support balance lookup");
    }

    public static LlmBalanceException providerError() {
        return new LlmBalanceException(502, "LLM_BALANCE_PROVIDER_ERROR", "Provider balance lookup failed");
    }
}
