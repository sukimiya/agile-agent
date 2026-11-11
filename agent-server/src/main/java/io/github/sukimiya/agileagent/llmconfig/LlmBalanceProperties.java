package io.github.sukimiya.agileagent.llmconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Defaults used when a preset does not say how to read a balance response. */
@ConfigurationProperties(prefix = "llm.balance")
public class LlmBalanceProperties {

    private String defaultCurrency = "USD";
    private int defaultDivisor = 1;

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public void setDefaultCurrency(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }

    public int getDefaultDivisor() {
        return defaultDivisor;
    }

    public void setDefaultDivisor(int defaultDivisor) {
        this.defaultDivisor = defaultDivisor;
    }
}
