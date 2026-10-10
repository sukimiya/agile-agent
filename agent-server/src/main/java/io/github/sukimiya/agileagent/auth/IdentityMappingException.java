package io.github.sukimiya.agileagent.auth;

/** Safe, stable failure for invalid OAuth2 identity claims. */
public final class IdentityMappingException extends RuntimeException {

    public static final String CODE = "IDENTITY_CLAIMS_INVALID";

    private final String code;

    IdentityMappingException(String field, String pointer, String reason) {
        super(CODE + ": configured claim '" + field + "' at JSON Pointer '" + pointer + "' " + reason);
        this.code = CODE;
    }

    public String code() {
        return code;
    }
}
