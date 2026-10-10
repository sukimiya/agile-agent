package io.github.sukimiya.agileagent.mockoauth;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe in-memory authorization code store with PKCE S256 enforcement. */
public final class AuthorizationCodeStore {
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final ConcurrentMap<String, StoredCode> codes = new ConcurrentHashMap<>();

    public AuthorizationCodeStore() {
        this(Clock.systemUTC(), new SecureRandom());
    }

    public AuthorizationCodeStore(Clock clock, SecureRandom secureRandom) {
        this.clock = Objects.requireNonNull(clock);
        this.secureRandom = Objects.requireNonNull(secureRandom);
    }

    public String issue(String clientId, URI redirectUri, String subject,
                        String codeChallenge, String codeChallengeMethod) {
        if (isBlank(clientId) || redirectUri == null || isBlank(subject)
                || !PkceVerifier.isValidS256Challenge(codeChallenge) || !"S256".equals(codeChallengeMethod)) {
            throw new IllegalArgumentException("Invalid authorization request");
        }
        StoredCode value = new StoredCode(clientId, redirectUri.toString(), subject, codeChallenge,
                codeChallengeMethod, clock.instant().plus(CODE_TTL));
        String code;
        do {
            code = randomValue();
        } while (codes.putIfAbsent(code, value) != null);
        return code;
    }

    public String consume(String code, String clientId, URI redirectUri, String codeVerifier) {
        if (code == null) {
            throw new InvalidAuthorizationCodeException();
        }
        AtomicReference<String> subject = new AtomicReference<>();
        codes.compute(code, (key, stored) -> {
            if (stored == null || !clock.instant().isBefore(stored.expiresAt())) {
                return null;
            }
            if (!stored.clientId().equals(clientId) || redirectUri == null
                    || !stored.redirectUri().equals(redirectUri.toString())
                    || !PkceVerifier.matches(codeVerifier, stored.challenge(), stored.challengeMethod())) {
                return stored;
            }
            subject.set(stored.subject());
            return null;
        });
        if (subject.get() == null) {
            throw new InvalidAuthorizationCodeException();
        }
        return subject.get();
    }

    private String randomValue() {
        byte[] bytes = new byte[32];
        synchronized (secureRandom) {
            secureRandom.nextBytes(bytes);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record StoredCode(String clientId, String redirectUri, String subject, String challenge,
                              String challengeMethod, Instant expiresAt) {
    }

    public static final class InvalidAuthorizationCodeException extends RuntimeException {
        public InvalidAuthorizationCodeException() {
            super("Invalid or expired authorization code");
        }
    }
}
