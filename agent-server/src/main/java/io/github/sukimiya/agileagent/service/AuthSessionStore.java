package io.github.sukimiya.agileagent.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

import reactor.core.publisher.Mono;

@Component
public class AuthSessionStore {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionStore.class);

    private static final String KEY_PREFIX = "authsession:";
    private static final Duration TTL = Duration.ofDays(30);
    private static final String LOGOUT_TOMBSTONE = "__AUTH_SESSION_LOGGED_OUT_V1__";
    private static final RedisScript<Long> UPDATE_IF_NOT_LOGGED_OUT_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[2] then return 0 end\n"
                    + "redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[3])\n"
                    + "return 1",
            Long.class);
    private static final RedisScript<Long> LOGOUT_SCRIPT = new DefaultRedisScript<>(
            "redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2])\n"
                    + "return 1",
            Long.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public AuthSessionStore(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<String> create(String accessToken, String refreshToken, Instant expiresAt,
                                IdentityContext identity, Instant identityFetchedAt) {
        String token = randomToken();
        return write(token, new AuthUser(identity.subject(), identity.displayName(), accessToken,
                        refreshToken, expiresAt, identity, identityFetchedAt))
                .thenReturn(token);
    }

    public Mono<Void> update(String token, AuthUser refreshed) {
        String payload;
        try {
            payload = json.writeValueAsString(refreshed);
        } catch (Exception e) {
            return Mono.error(new IllegalStateException("Failed to serialize auth session", e));
        }
        return redis.execute(UPDATE_IF_NOT_LOGGED_OUT_SCRIPT,
                        List.of(sessionKey(token)), payload, LOGOUT_TOMBSTONE, Long.toString(TTL.toMillis()))
                .next()
                .switchIfEmpty(Mono.error(new IllegalStateException("Failed to update auth session")))
                .flatMap(updated -> Long.valueOf(1L).equals(updated)
                        ? Mono.<Void>empty()
                        : Mono.error(new SessionLoggedOutException()));
    }

    /**
     * Atomically replaces the app-session payload with an unusable tombstone for the session TTL.
     * Keeping it on the existing key makes the logout/update scripts single-key and Redis Cluster safe.
     */
    public Mono<Void> logout(String token) {
        return redis.execute(LOGOUT_SCRIPT,
                        List.of(sessionKey(token)), LOGOUT_TOMBSTONE, Long.toString(TTL.toMillis()))
                .next()
                .switchIfEmpty(Mono.error(new IllegalStateException("Failed to invalidate auth session")))
                .flatMap(invalidated -> Long.valueOf(1L).equals(invalidated)
                        ? Mono.<Void>empty()
                        : Mono.error(new IllegalStateException("Failed to invalidate auth session")));
    }

    private Mono<Void> write(String token, AuthUser user) {
        String payload;
        try {
            payload = json.writeValueAsString(user);
        } catch (Exception e) {
            return Mono.error(new IllegalStateException("Failed to serialize auth session", e));
        }
        return redis.opsForValue().set(sessionKey(token), payload, TTL).then();
    }

    /**
     * A stored payload that cannot be read back as a usable session (unparseable JSON, or an
     * old-shape payload missing its token expiry, mapped identity, or identity timestamp) is treated
     * as absent rather than as an error, so the caller's "no session" path yields a clean 401 and the
     * user simply logs in again instead of seeing an opaque 500.
     */
    public Mono<AuthUser> resolve(String token) {
        return redis.opsForValue().get(KEY_PREFIX + token).flatMap(raw -> {
            if (LOGOUT_TOMBSTONE.equals(raw)) return Mono.empty();
            AuthUser user;
            try {
                user = json.readValue(raw, AuthUser.class);
            } catch (Exception e) {
                log.warn("Discarding unreadable auth session payload, treating as logged out");
                return Mono.<AuthUser>empty();
            }
            if (user.expiresAt() == null || user.identity() == null || user.identityFetchedAt() == null
                    || !Objects.equals(user.subject(), user.identity().subject())
                    || !Objects.equals(user.displayName(), user.identity().displayName())) {
                log.warn("Discarding incomplete auth session payload, treating as logged out");
                return Mono.<AuthUser>empty();
            }
            return Mono.just(user);
        });
    }

    public Mono<Void> delete(String token) {
        return redis.delete(sessionKey(token)).then();
    }

    private static String sessionKey(String token) {
        return KEY_PREFIX + token;
    }

    public static final class SessionLoggedOutException extends RuntimeException {
        public SessionLoggedOutException() {
            super("Auth session was logged out");
        }
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record AuthUser(String subject, String displayName, String accessToken, String refreshToken,
                            Instant expiresAt, IdentityContext identity, Instant identityFetchedAt) {

        public String userId() {
            return subject;
        }

        public String name() {
            return displayName;
        }

        /** Compatibility projection for the legacy endpoint; authorization consumes identity directly. */
        public String userType() {
            if (identity == null || identity.roles().isEmpty()) return null;
            return String.join(",", identity.roles().stream().sorted().toList());
        }

        @Override
        public String toString() {
            return "AuthUser[subject=" + subject + ", displayName=" + displayName
                    + ", accessToken=[REDACTED], refreshToken=[REDACTED]"
                    + ", expiresAt=" + expiresAt + ", identity=[PRESENT], identityFetchedAt="
                    + identityFetchedAt + "]";
        }
    }
}
