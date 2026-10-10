package io.github.sukimiya.agileagent.web;

/** Stable, safe JSON error returned by the public API. */
public record ApiError(String code, String message) {}
