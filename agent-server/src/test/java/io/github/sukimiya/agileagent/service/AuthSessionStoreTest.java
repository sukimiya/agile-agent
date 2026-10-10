package io.github.sukimiya.agileagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.sukimiya.agileagent.TestAuthUsers.identity;
import static io.github.sukimiya.agileagent.TestAuthUsers.user;

import java.time.Instant;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import io.github.sukimiya.agileagent.LocalRedisTestServer;

class AuthSessionStoreTest {

    private LettuceConnectionFactory connectionFactory;
    private ReactiveStringRedisTemplate redisTemplate;
    private AuthSessionStore store;
    private String issuedToken;
    private static final Instant EXPIRES = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant IDENTITY_FETCHED = Instant.parse("2026-07-31T23:00:00Z");

    @BeforeEach
    void setUp() {
        var config = LocalRedisTestServer.configuration(1);
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
        var identity = identity("user-1", "周琳琳", Set.of("admin"));
        issuedToken = store.create("at-1", "rt-1", EXPIRES, identity, IDENTITY_FETCHED).block();

        var resolved = store.resolve(issuedToken).blockOptional();

        assertThat(resolved).isPresent();
        assertThat(resolved.get().userId()).isEqualTo("user-1");
        assertThat(resolved.get().name()).isEqualTo("周琳琳");
        assertThat(resolved.get().accessToken()).isEqualTo("at-1");
        assertThat(resolved.get().refreshToken()).isEqualTo("rt-1");
        assertThat(resolved.get().expiresAt()).isEqualTo(EXPIRES);
        assertThat(resolved.get().identity()).isEqualTo(identity);
        assertThat(resolved.get().identityFetchedAt()).isEqualTo(IDENTITY_FETCHED);
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
                        "{\"userId\":\"user-1\",\"name\":\"周琳琳\",\"legacyOpaqueField\":\"ignored\"}")
                .block();

        assertThat(store.resolve(issuedToken).blockOptional()).isEmpty();
    }

    @Test
    void resolveTreatsOAuth2SessionWithoutStoredIdentityAsAbsent() {
        issuedToken = "missing-identity-token";
        redisTemplate.opsForValue()
                .set("authsession:" + issuedToken,
                        "{\"userId\":\"user-1\",\"name\":\"周琳琳\",\"accessToken\":\"at-1\","
                                + "\"refreshToken\":\"rt-1\",\"expiresAt\":\"2026-08-01T00:00:00Z\","
                                + "\"userType\":\"admin\"}")
                .block();

        assertThat(store.resolve(issuedToken).blockOptional()).isEmpty();
    }

    @Test
    void createPersistsIdentityAndFreshnessAlongsideCredentials() throws Exception {
        issuedToken = store.create("at-1", "rt-1", EXPIRES,
                identity("user-1", "周琳琳", Set.of("admin")), IDENTITY_FETCHED).block();

        JsonNode payload = new ObjectMapper().readTree(redisTemplate.opsForValue()
                .get("authsession:" + issuedToken).block());

        assertThat(payload.hasNonNull("identity")).isTrue();
        assertThat(payload.path("identity").path("subject").asText()).isEqualTo("user-1");
        assertThat(payload.path("identity").path("roles").toString()).contains("admin");
        assertThat(payload.path("identityFetchedAt").asLong()).isEqualTo(IDENTITY_FETCHED.getEpochSecond());
        assertThat(payload.path("accessToken").asText()).isEqualTo("at-1");
        assertThat(payload.path("refreshToken").asText()).isEqualTo("rt-1");
    }

    @Test
    void resolveTreatsOAuth2SessionWithoutIdentityTimestampAsAbsent() {
        issuedToken = "missing-identity-time-token";
        redisTemplate.opsForValue()
                .set("authsession:" + issuedToken,
                        "{\"subject\":\"user-1\",\"displayName\":\"周琳琳\",\"accessToken\":\"at-1\","
                                + "\"refreshToken\":\"rt-1\",\"expiresAt\":\"2026-08-01T00:00:00Z\","
                                + "\"identity\":{\"subject\":\"user-1\",\"displayName\":\"周琳琳\","
                                + "\"roles\":[],\"manageableOrgIds\":[],\"readableOrgIds\":[],"
                                + "\"platformAdmin\":false,\"tenantAdmin\":false}}")
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
        issuedToken = store.create("at-1", "rt-1", EXPIRES,
                identity("user-1", "周琳琳", Set.of("admin")), IDENTITY_FETCHED).block();

        store.delete(issuedToken).block();

        assertThat(store.resolve(issuedToken).blockOptional()).isEmpty();
    }

    @Test
    void tokensAreUniquePerCall() {
        var identity = identity("user-1", "A", Set.of("admin"));
        var t1 = store.create("at-1", "rt-1", EXPIRES, identity, IDENTITY_FETCHED).block();
        var t2 = store.create("at-1", "rt-1", EXPIRES, identity, IDENTITY_FETCHED).block();
        issuedToken = t1;
        redisTemplate.delete("authsession:" + t2).block();

        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void updateOverwritesStoredUserUnderSameToken() {
        var identity = identity("user-1", "周琳琳", Set.of("admin"));
        issuedToken = store.create("at-1", "rt-1", EXPIRES, identity, IDENTITY_FETCHED).block();
        var refreshed = user("user-1", "周琳琳", "at-2", "rt-2", EXPIRES.plusSeconds(3600),
                identity, IDENTITY_FETCHED.plusSeconds(1));

        store.update(issuedToken, refreshed).block();

        assertThat(store.resolve(issuedToken).blockOptional()).contains(refreshed);
    }

    @Test
    void authUserStringRepresentationOmitsCredentialValues() {
        String accessSentinel = "sentinel-access-8f37";
        String refreshSentinel = "sentinel-refresh-2c1a";
        var user = user("user-1", "周琳琳", accessSentinel, refreshSentinel, EXPIRES,
                Set.of("admin"), IDENTITY_FETCHED);

        assertThat(user.toString()).doesNotContain(accessSentinel, refreshSentinel);
    }
}
