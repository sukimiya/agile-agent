package com.jowo.agent.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import reactor.core.publisher.Mono;

@Component
public class AuthSessionStore {

    private static final String KEY_PREFIX = "authsession:";
    private static final Duration TTL = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

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

    public Mono<AuthUser> resolve(String token) {
        return redis.opsForValue().get(KEY_PREFIX + token).map(raw -> {
            try {
                return json.readValue(raw, AuthUser.class);
            } catch (Exception e) {
                throw new IllegalStateException("Corrupt auth session JSON for token " + token, e);
            }
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
                            Instant expiresAt, String userType) {}
}
