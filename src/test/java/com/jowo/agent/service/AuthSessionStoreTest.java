package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

class AuthSessionStoreTest {

    private LettuceConnectionFactory connectionFactory;
    private ReactiveStringRedisTemplate redisTemplate;
    private AuthSessionStore store;
    private String issuedToken;

    @BeforeEach
    void setUp() {
        var config = new RedisStandaloneConfiguration("192.168.3.232", 6379);
        config.setPassword("FeetxXuFkZIaM2Pq");
        config.setDatabase(1);
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new ReactiveStringRedisTemplate(connectionFactory);
        store = new AuthSessionStore(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        if (issuedToken != null) redisTemplate.delete("authsession:" + issuedToken).block();
        connectionFactory.destroy();
    }

    @Test
    void createThenResolveReturnsSameUser() {
        issuedToken = store.create("user-1", "周琳琳", "sl-tok").block();

        var resolved = store.resolve(issuedToken).blockOptional();

        assertThat(resolved).isPresent();
        assertThat(resolved.get().userId()).isEqualTo("user-1");
        assertThat(resolved.get().name()).isEqualTo("周琳琳");
        assertThat(resolved.get().smartLinkToken()).isEqualTo("sl-tok");
    }

    @Test
    void resolveOnUnknownTokenReturnsEmpty() {
        assertThat(store.resolve("no-such-token").blockOptional()).isEmpty();
    }

    @Test
    void deleteInvalidatesToken() {
        issuedToken = store.create("user-1", "周琳琳", "sl-tok").block();

        store.delete(issuedToken).block();

        assertThat(store.resolve(issuedToken).blockOptional()).isEmpty();
    }

    @Test
    void tokensAreUniquePerCall() {
        var t1 = store.create("user-1", "A", "sl-tok").block();
        var t2 = store.create("user-1", "A", "sl-tok").block();
        issuedToken = t1;
        redisTemplate.delete("authsession:" + t2).block();

        assertThat(t1).isNotEqualTo(t2);
    }
}
