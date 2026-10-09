package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

class OAuth2StateStoreTest {

    private LettuceConnectionFactory connectionFactory;
    private ReactiveStringRedisTemplate redisTemplate;
    private OAuth2StateStore store;
    private String issuedState;

    @BeforeEach
    void setUp() {
        var config = new RedisStandaloneConfiguration("192.168.3.232", 6379);
        config.setPassword("FeetxXuFkZIaM2Pq");
        config.setDatabase(1);
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new ReactiveStringRedisTemplate(connectionFactory);
        store = new OAuth2StateStore(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        if (issuedState != null) redisTemplate.delete("oauth2state:" + issuedState).block();
        connectionFactory.destroy();
    }

    @Test
    void saveThenConsumeReturnsCodeVerifierOnce() {
        issuedState = store.save("verifier-abc").block();

        assertThat(store.consume(issuedState).blockOptional()).contains("verifier-abc");
        assertThat(store.consume(issuedState).blockOptional()).isEmpty();
    }

    @Test
    void consumeOnUnknownStateReturnsEmpty() {
        assertThat(store.consume("no-such-state").blockOptional()).isEmpty();
    }

    @Test
    void statesAreUniquePerCall() {
        var s1 = store.save("verifier-1").block();
        var s2 = store.save("verifier-2").block();
        issuedState = s1;
        redisTemplate.delete("oauth2state:" + s2).block();

        assertThat(s1).isNotEqualTo(s2);
    }
}
