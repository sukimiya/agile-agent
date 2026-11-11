package io.github.sukimiya.agileagent.llmconfig;

/** A preset field failed validation. {@link #field()} is the request property name. */
public final class LlmProviderPresetValidationException extends RuntimeException {

    private final String field;

    public LlmProviderPresetValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** Kept for callers that only need the stable generic error. */
    public LlmProviderPresetValidationException() {
        this("preset", "Provider preset input is invalid");
    }

    public String field() {
        return field;
    }
}
