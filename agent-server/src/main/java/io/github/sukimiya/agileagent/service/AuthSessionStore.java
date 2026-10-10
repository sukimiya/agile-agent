package io.github.sukimiya.agileagent.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import reactor.core.publisher.Mono;

@Component
public class AuthSessionStore {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionStore.class);

    private static final String KEY_PREFIX = "authsession:";
    private static final Duration TTL = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public AuthSessionStore(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<String> create(String userId, String name, String accessToken, String refreshToken,
                                Instant expiresAt, String userType) {
        String token = randomToken();
        return write(token, new AuthUser(userId, name, accessToken, refreshToken, expiresAt, userType))
                .thenReturn(token);
    }

    public Mono<Void> update(String token, AuthUser refreshed) {
        return write(token, refreshed);
    }

    private Mono<Void> write(String token, AuthUser user) {
        String payload;
        try {
            payload = json.writeValueAsString(user);
        } catch (Exception e) {
            return Mono.error(new IllegalStateException("Failed to serialize auth session for user " + user.userId(), e));
        }
        return redis.opsForValue().set(KEY_PREFIX + token, payload, TTL).then();
    }

    /**
     * A stored payload that cannot be read back as a usable session (unparseable JSON, or an
     * old-shape payload written before the OAuth2 migration that has no expiresAt) is treated as
     * absent rather than as an error, so the caller's "no session" path yields a clean 401 and the
     * user simply logs in again instead of seeing an opaque 500.
     */
    public Mono<AuthUser> resolve(String token) {
        return redis.opsForValue().get(KEY_PREFIX + token).flatMap(raw -> {
            AuthUser user;
            try {
                user = json.readValue(raw, AuthUser.class);
            } catch (Exception e) {
                log.warn("Discarding unreadable auth session payload, treating as logged out", e);
                return Mono.<AuthUser>empty();
            }
            if (user.expiresAt() == null) {
                log.warn("Discarding auth session without expiresAt (pre-OAuth2 shape), treating as logged out");
                return Mono.<AuthUser>empty();
            }
            return Mono.just(user);
        });
    }

    public Mono<Void> delete(String token) {
        return redis.delete(KEY_PREFIX + token).then();
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record AuthUser(String userId, String name, String accessToken, String refreshToken,
                            Instant expiresAt, String userType) {
        @Override
        public String toString() {
            return "AuthUser[userId=" + userId + ", name=" + name
                    + ", accessToken=[REDACTED], refreshToken=[REDACTED]"
                    + ", expiresAt=" + expiresAt + ", userType=" + userType + "]";
        }
    }
}
