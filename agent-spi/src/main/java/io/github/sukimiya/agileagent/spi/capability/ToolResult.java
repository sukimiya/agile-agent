package io.github.sukimiya.agileagent.spi.capability;

import java.util.Objects;

/** A tool outcome; providers must supply display-safe text without credentials. */
public record ToolResult(boolean success, String displayResult) {

    public ToolResult {
        Objects.requireNonNull(displayResult, "displayResult must not be null");
    }
}
