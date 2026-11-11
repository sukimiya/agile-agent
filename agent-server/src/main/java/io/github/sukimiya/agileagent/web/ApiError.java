package io.github.sukimiya.agileagent.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Stable, safe JSON error returned by the public API. {@code field} is set when one input failed. */
public record ApiError(String code, String message, @JsonInclude(JsonInclude.Include.NON_NULL) String field) {

    public ApiError(String code, String message) {
        this(code, message, null);
    }
}
