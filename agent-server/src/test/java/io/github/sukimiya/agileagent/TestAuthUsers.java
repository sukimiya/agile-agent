package io.github.sukimiya.agileagent;

import java.time.Instant;
import java.util.Set;

import io.github.sukimiya.agileagent.service.AuthSessionStore.AuthUser;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

/** Shared authenticated-user fixtures with an explicitly stored identity. */
public final class TestAuthUsers {

    private TestAuthUsers() {}

    public static IdentityContext identity(String subject, String displayName, Set<String> roles) {
        return new IdentityContext(subject, displayName, null, null, null, null,
                roles, Set.of(), Set.of(), roles.contains("platform_admin"), roles.contains("tenant_admin"));
    }

    public static AuthUser user(String subject, String displayName, String accessToken, String refreshToken,
                                Instant expiresAt, IdentityContext identity, Instant identityFetchedAt) {
        return new AuthUser(subject, displayName, accessToken, refreshToken, expiresAt, identity, identityFetchedAt);
    }

    public static AuthUser user(String subject, String displayName, String accessToken, String refreshToken,
                                Instant expiresAt, Set<String> roles, Instant identityFetchedAt) {
        return user(subject, displayName, accessToken, refreshToken, expiresAt,
                identity(subject, displayName, roles), identityFetchedAt);
    }
}
