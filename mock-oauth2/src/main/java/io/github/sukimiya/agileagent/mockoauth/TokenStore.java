package io.github.sukimiya.agileagent.mockoauth;

import com.fasterxml.jackson.databind.JsonNode;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe in-memory access and rotating refresh token store. */
public final class TokenStore {
    private static final Duration ACCESS_TTL = Duration.ofHours(1);
    private final MockIdentityCatalog identities;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final Map<String, AccessGrant> accessTokens = new ConcurrentHashMap<>();
    private final Map<String, TokenFamily> refreshTokens = new ConcurrentHashMap<>();
    private final Map<String, TokenFamily> spentRefreshTokens = new ConcurrentHashMap<>();

    public TokenStore(MockIdentityCatalog identities, Clock clock, SecureRandom secureRandom) {
        this.identities = Objects.requireNonNull(identities);
        this.clock = Objects.requireNonNull(clock);
        this.secureRandom = Objects.requireNonNull(secureRandom);
    }

    public TokenStore(MockIdentityCatalog identities) {
        this(identities, Clock.systemUTC(), new SecureRandom());
    }

    public synchronized TokenPair issue(String subject) {
        if (identities.findBySubject(subject).isEmpty()) {
            throw new InvalidTokenException();
        }
        TokenFamily family = new TokenFamily(subject);
        return rotate(family);
    }

    public synchronized TokenPair refresh(String refreshToken) {
        if (refreshToken == null) {
            throw new InvalidTokenException();
        }
        TokenFamily family = refreshTokens.remove(refreshToken);
        if (family == null) {
            TokenFamily replayedFamily = spentRefreshTokens.get(refreshToken);
            if (replayedFamily != null) {
                revokeFamily(replayedFamily);
            }
            throw new InvalidTokenException();
        }
        if (family.revoked) {
            throw new InvalidTokenException();
        }
        family.activeRefreshTokens.remove(refreshToken);
        family.spentRefreshTokens.add(refreshToken);
        spentRefreshTokens.put(refreshToken, family);
        return rotate(family);
    }

    /** Revoking either active token revokes the entire token family. */
    public synchronized void revoke(String token) {
        if (token == null) {
            throw new InvalidTokenException();
        }
        TokenFamily family = refreshTokens.get(token);
        if (family == null) {
            family = spentRefreshTokens.get(token);
        }
        if (family == null) {
            AccessGrant access = accessTokens.get(token);
            family = access == null ? null : access.family();
        }
        if (family == null) {
            throw new InvalidTokenException();
        }
        revokeFamily(family);
    }

    public synchronized Optional<JsonNode> findIdentityByAccessToken(String accessToken) {
        if (accessToken == null) {
            return Optional.empty();
        }
        AccessGrant grant = accessTokens.get(accessToken);
        if (grant == null) {
            return Optional.empty();
        }
        if (grant.family().revoked || !clock.instant().isBefore(grant.expiresAt())) {
            accessTokens.remove(accessToken, grant);
            grant.family().activeAccessTokens.remove(accessToken);
            return Optional.empty();
        }
        return identities.userInfo(grant.family().subject);
    }

    private TokenPair rotate(TokenFamily family) {
        String access;
        do {
            access = randomValue();
        } while (accessTokens.containsKey(access) || refreshTokens.containsKey(access)
                || spentRefreshTokens.containsKey(access));
        String refresh;
        do {
            refresh = randomValue();
        } while (access.equals(refresh) || accessTokens.containsKey(refresh) || refreshTokens.containsKey(refresh)
                || spentRefreshTokens.containsKey(refresh));
        Instant expiresAt = clock.instant().plus(ACCESS_TTL);
        family.activeAccessTokens.add(access);
        family.activeRefreshTokens.add(refresh);
        accessTokens.put(access, new AccessGrant(family, expiresAt));
        refreshTokens.put(refresh, family);
        return new TokenPair(access, refresh, expiresAt);
    }

    private void revokeFamily(TokenFamily family) {
        family.revoked = true;
        family.activeAccessTokens.forEach(accessTokens::remove);
        family.activeRefreshTokens.forEach(refreshTokens::remove);
        family.spentRefreshTokens.forEach(spentRefreshTokens::remove);
        family.activeAccessTokens.clear();
        family.activeRefreshTokens.clear();
        family.spentRefreshTokens.clear();
    }

    private String randomValue() {
        byte[] bytes = new byte[32];
        synchronized (secureRandom) {
            secureRandom.nextBytes(bytes);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class TokenFamily {
        private final String subject;
        private final Set<String> activeAccessTokens = new HashSet<>();
        private final Set<String> activeRefreshTokens = new HashSet<>();
        private final Set<String> spentRefreshTokens = new HashSet<>();
        private volatile boolean revoked;

        private TokenFamily(String subject) {
            this.subject = subject;
        }
    }

    private record AccessGrant(TokenFamily family, Instant expiresAt) {
    }

    public record TokenPair(String accessToken, String refreshToken, Instant accessExpiresAt) {
        @Override
        public String toString() {
            return "TokenPair[accessToken=<redacted>, refreshToken=<redacted>, accessExpiresAt="
                    + accessExpiresAt + "]";
        }
    }

    public static final class InvalidTokenException extends RuntimeException {
        public InvalidTokenException() {
            super("Invalid or expired token");
        }
    }
}
