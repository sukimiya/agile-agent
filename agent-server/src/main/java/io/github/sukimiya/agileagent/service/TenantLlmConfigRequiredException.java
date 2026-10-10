package io.github.sukimiya.agileagent.service;

public final class TenantLlmConfigRequiredException extends RuntimeException {

    public static final String MESSAGE = "LLM configuration is required";

    public TenantLlmConfigRequiredException() {
        super(MESSAGE);
    }
}
