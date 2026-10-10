package io.github.sukimiya.agileagent.llmconfig;

import org.springframework.http.HttpStatus;

/** A sanitized, stable error suitable for returning from the LLM configuration API. */
public final class LlmConfigValidationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public LlmConfigValidationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static LlmConfigValidationException invalidInput() {
        return new LlmConfigValidationException(HttpStatus.BAD_REQUEST,
                "INVALID_LLM_CONFIG", "LLM configuration input is invalid");
    }

    public static LlmConfigValidationException notFound() {
        return new LlmConfigValidationException(HttpStatus.NOT_FOUND,
                "LLM_CONFIG_NOT_FOUND", "LLM configuration not found");
    }

    public static LlmConfigValidationException presetNotFound() {
        return new LlmConfigValidationException(HttpStatus.NOT_FOUND,
                "LLM_PROVIDER_PRESET_NOT_FOUND", "Provider preset not found");
    }

    public static LlmConfigValidationException providerRejected(int status) {
        return new LlmConfigValidationException(HttpStatus.BAD_GATEWAY,
                "LLM_PROVIDER_REJECTED", "Provider rejected the validation request (HTTP " + status + ")");
    }

    public static LlmConfigValidationException providerUnavailable() {
        return new LlmConfigValidationException(HttpStatus.BAD_GATEWAY,
                "LLM_PROVIDER_UNAVAILABLE", "Provider could not be reached");
    }

    public static LlmConfigValidationException saveFailed() {
        return new LlmConfigValidationException(HttpStatus.INTERNAL_SERVER_ERROR,
                "LLM_CONFIG_SAVE_FAILED", "LLM configuration could not be saved");
    }
}
