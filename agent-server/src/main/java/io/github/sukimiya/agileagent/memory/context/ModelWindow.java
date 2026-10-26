package io.github.sukimiya.agileagent.memory.context;

/**
 * The model's context window and the part of it reserved for the answer (D2); the rest is the input budget.
 * Comes from the tenant's provider preset, with {@code app.memory.context.default-*} for unset values.
 */
public record ModelWindow(int contextWindowTokens, int maxOutputTokens) {

    public ModelWindow {
        if (contextWindowTokens <= 0 || maxOutputTokens <= 0 || maxOutputTokens >= contextWindowTokens) {
            throw new IllegalArgumentException("model window needs 0 < maxOutputTokens < contextWindowTokens");
        }
    }
}
