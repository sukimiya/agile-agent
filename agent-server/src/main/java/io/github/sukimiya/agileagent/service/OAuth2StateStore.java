package io.github.sukimiya.agileagent.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
public class OAuth2StateStore {

    private static final String KEY_PREFIX = "oauth2state:";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReactiveStringRedisTemplate redis;

    public OAuth2StateStore(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<String> save(String codeVerifier) {
        String state = randomState();
        return redis.opsForValue().set(KEY_PREFIX + state, codeVerifier, TTL).thenReturn(state);
    }

    public Mono<String> consume(String state) {
        return redis.opsForValue().getAndDelete(KEY_PREFIX + state);
    }

    private static String randomState() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
