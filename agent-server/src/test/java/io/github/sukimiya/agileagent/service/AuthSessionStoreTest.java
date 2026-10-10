package io.github.sukimiya.agileagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

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
    private static final Instant EXPIRES = Instant.parse("2026-08-01T00:00:00Z");

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
        issuedToken = store.create("user-1", "周琳琳", "at-1", "rt-1", EXPIRES, "admin").block();

        var resolved = store.resolve(issuedToken).blockOptional();

        assertThat(resolved).isPresent();
        assertThat(resolved.get().userId()).isEqualTo("user-1");
        assertThat(resolved.get().name()).isEqualTo("周琳琳");
        assertThat(resolved.get().accessToken()).isEqualTo("at-1");
        assertThat(resolved.get().refreshToken()).isEqualTo("rt-1");
        assertThat(resolved.get().expiresAt()).isEqualTo(EXPIRES);
        assertThat(resolved.get().userType()).isEqualTo("admin");
    }

    @Test
    void resolveOnUnknownTokenReturnsEmpty() {
        assertThat(store.resolve("no-such-token").blockOptional()).isEmpty();
    }

    @Test
    void resolveTreatsPreMigrationSessionPayloadAsAbsent() {
        issuedToken = "legacy-shape-token";
        redisTemplate.opsForValue()
                .set("authsession:" + issuedToken,
                        "{\"userId\":\"user-1\",\"name\":\"周琳琳\",\"smartLinkToken\":\"sl-1\"}")
                .block();

        assertThat(store.resolve(issuedToken).blockOptional()).isEmpty();
    }

    @Test
    void resolveTreatsUnparseablePayloadAsAbsent() {
        issuedToken = "corrupt-json-token";
        redisTemplate.opsForValue().set("authsession:" + issuedToken, "not-json-at-all").block();

        assertThat(store.resolve(issuedToken).blockOptional()).isEmpty();
    }

    @Test
    void deleteInvalidatesToken() {
        issuedToken = store.create("user-1", "周琳琳", "at-1", "rt-1", EXPIRES, "admin").block();

        store.delete(issuedToken).block();

        assertThat(store.resolve(issuedToken).blockOptional()).isEmpty();
    }

    @Test
    void tokensAreUniquePerCall() {
        var t1 = store.create("user-1", "A", "at-1", "rt-1", EXPIRES, "admin").block();
        var t2 = store.create("user-1", "A", "at-1", "rt-1", EXPIRES, "admin").block();
        issuedToken = t1;
        redisTemplate.delete("authsession:" + t2).block();

        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void updateOverwritesStoredUserUnderSameToken() {
        issuedToken = store.create("user-1", "周琳琳", "at-1", "rt-1", EXPIRES, "admin").block();
        var refreshed = new AuthSessionStore.AuthUser("user-1", "周琳琳", "at-2", "rt-2", EXPIRES.plusSeconds(3600), "admin");

        store.update(issuedToken, refreshed).block();

        assertThat(store.resolve(issuedToken).blockOptional()).contains(refreshed);
    }

    @Test
    void authUserStringRepresentationOmitsCredentialValues() {
        String accessSentinel = "sentinel-access-8f37";
        String refreshSentinel = "sentinel-refresh-2c1a";
        var user = new AuthSessionStore.AuthUser(
                "user-1", "周琳琳", accessSentinel, refreshSentinel, EXPIRES, "admin");

        assertThat(user.toString()).doesNotContain(accessSentinel, refreshSentinel);
    }
}
