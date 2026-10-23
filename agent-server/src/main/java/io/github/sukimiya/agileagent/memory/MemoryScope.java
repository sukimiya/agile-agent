package io.github.sukimiya.agileagent.memory;

import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

/**
 * Isolation boundary of all memory data: a tenant plus the authenticated subject inside it. Values are kept
 * exactly as resolved from the server-side {@link IdentityContext}; they are never trimmed or case-folded, so
 * {@code alice} and {@code Alice} (or the same subject in two tenants) are always different scopes. Storage
 * compares them with a binary collation.
 */
public record MemoryScope(String tenantId, String subject) {

    /** Matches {@code tenant_id VARCHAR(128)} in the memory schema. */
    public static final int MAX_TENANT_LENGTH = 128;
    /** Matches {@code subject}/{@code user_id VARCHAR(255)} in the memory schema. */
    public static final int MAX_SUBJECT_LENGTH = 255;

    public MemoryScope {
        requireValue("tenantId", tenantId, MAX_TENANT_LENGTH);
        requireValue("subject", subject, MAX_SUBJECT_LENGTH);
    }

    /**
     * Resolves the scope from the server-side identity. A missing tenant is never guessed: it is rejected with
     * {@link ErrorCode#TENANT_REQUIRED} (HTTP 403).
     */
    public static MemoryScope of(IdentityContext identity) {
        if (identity == null || identity.tenantId() == null || identity.tenantId().isBlank()) {
            throw new MemoryException(ErrorCode.TENANT_REQUIRED);
        }
        return new MemoryScope(identity.tenantId(), identity.subject());
    }

    private static void requireValue(String name, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must be at most " + maxLength + " characters");
        }
    }
}
