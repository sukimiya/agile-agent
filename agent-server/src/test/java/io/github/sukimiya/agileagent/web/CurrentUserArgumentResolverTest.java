package io.github.sukimiya.agileagent.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static io.github.sukimiya.agileagent.TestAuthUsers.user;
import static io.github.sukimiya.agileagent.TestAuthUsers.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.OAuth2Client;
import io.github.sukimiya.agileagent.auth.IdentityMappingException;
import io.github.sukimiya.agileagent.auth.JsonPointerOAuth2IdentityMapper;
import io.github.sukimiya.agileagent.config.OAuth2Properties;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CurrentUserArgumentResolverTest {

    /** Redis stub whose SETNX always succeeds — i.e. this request is the single-flight winner. */
    @SuppressWarnings("unchecked")
    private static ReactiveStringRedisTemplate lockAlwaysAcquired() {
        var redis = mock(ReactiveStringRedisTemplate.class);
        var ops = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(redis.delete(anyString())).thenReturn(Mono.just(1L));
        return redis;
    }

    private static MockServerWebExchange bearer(String token) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/x").header("Authorization", "Bearer " + token).build());
    }

    @Test
    void supportsParameterOnlyWhenAnnotationPresent() throws NoSuchMethodException {
        var resolver = new CurrentUserArgumentResolver(mock(AuthSessionStore.class), mock(OAuth2Client.class),
                mock(ReactiveStringRedisTemplate.class));
        var annotatedParam = new MethodParameter(
                Probe.class.getDeclaredMethod("annotated", AuthSessionStore.AuthUser.class), 0);
        var plainParam = new MethodParameter(
                Probe.class.getDeclaredMethod("plain", String.class), 0);

        org.assertj.core.api.Assertions.assertThat(resolver.supportsParameter(annotatedParam)).isTrue();
        org.assertj.core.api.Assertions.assertThat(resolver.supportsParameter(plainParam)).isFalse();
    }

    @Test
    void resolvesUserFromBearerTokenWithoutRefreshWhenTokenStillFresh() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var user = user("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(3600),
                java.util.Set.of("admin"), Instant.now());
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(user));
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, mock(ReactiveStringRedisTemplate.class));

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .expectNext(user)
                .verifyComplete();
        verifyNoInteractions(oauth2);
    }

    @Test
    void refreshesAndPersistsWhenTokenNearExpiry() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var expiring = user("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10),
                java.util.Set.of("admin"), Instant.now());
        var refreshedIdentity = identity("user-1", "周琳琳", Set.of("reader"));
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(expiring));
        when(oauth2.refresh("rt-1")).thenReturn(Mono.just(new OAuth2Client.TokenResult("at-2", "rt-2", 3600)));
        when(oauth2.fetchIdentity("at-2")).thenReturn(Mono.just(refreshedIdentity));
        when(sessions.update(any(), any())).thenReturn(Mono.empty());
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, lockAlwaysAcquired());

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .assertNext(result -> {
                    var refreshed = (AuthSessionStore.AuthUser) result;
                    org.assertj.core.api.Assertions.assertThat(refreshed.accessToken()).isEqualTo("at-2");
                    org.assertj.core.api.Assertions.assertThat(refreshed.refreshToken()).isEqualTo("rt-2");
                    org.assertj.core.api.Assertions.assertThat(refreshed.identity()).isEqualTo(refreshedIdentity);
                })
                .verifyComplete();
        verify(sessions).update(org.mockito.ArgumentMatchers.eq("tok-1"), any());
    }

    @Test
    void keepsExistingRefreshTokenWhenProviderOmitsANewOne() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var expiring = user("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10),
                java.util.Set.of("admin"), Instant.now());
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(expiring));
        when(oauth2.refresh("rt-1")).thenReturn(Mono.just(new OAuth2Client.TokenResult("at-2", null, 3600)));
        when(oauth2.fetchIdentity("at-2")).thenReturn(Mono.just(expiring.identity()));
        when(sessions.update(any(), any())).thenReturn(Mono.empty());
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, lockAlwaysAcquired());

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .assertNext(result -> org.assertj.core.api.Assertions
                        .assertThat(((AuthSessionStore.AuthUser) result).refreshToken()).isEqualTo("rt-1"))
                .verifyComplete();

        var persisted = org.mockito.ArgumentCaptor.forClass(AuthSessionStore.AuthUser.class);
        verify(sessions).update(org.mockito.ArgumentMatchers.eq("tok-1"), persisted.capture());
        org.assertj.core.api.Assertions.assertThat(persisted.getValue().refreshToken()).isEqualTo("rt-1");
        org.assertj.core.api.Assertions.assertThat(persisted.getValue().accessToken()).isEqualTo("at-2");
    }

    @Test
    void refreshFailureErrorsUnauthorized() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var expiring = user("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10),
                java.util.Set.of("admin"), Instant.now());
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(expiring));
        when(oauth2.refresh("rt-1")).thenReturn(Mono.error(new OAuth2Client.OAuth2AuthException("token revoked")));
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, lockAlwaysAcquired());

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .expectError(UnauthorizedException.class)
                .verify();
    }

    @Test
    void providerUnavailableDuringRefreshIsNotReportedAsUnauthorized() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var expiring = user("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10),
                java.util.Set.of("admin"), Instant.now());
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(expiring));
        when(oauth2.refresh("rt-1"))
                .thenReturn(Mono.error(new OAuth2Client.OAuth2UnavailableException("connection refused")));
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, lockAlwaysAcquired());

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .expectError(OAuth2Client.OAuth2UnavailableException.class)
                .verify();
    }

    @Test
    void sessionStoreFailureDuringRefreshIsNotReportedAsUnauthorized() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var expiring = user("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10),
                java.util.Set.of("admin"), Instant.now());
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(expiring));
        when(oauth2.refresh("rt-1")).thenReturn(Mono.just(new OAuth2Client.TokenResult("at-2", "rt-2", 3600)));
        when(oauth2.fetchIdentity("at-2")).thenReturn(Mono.just(expiring.identity()));
        when(sessions.update(any(), any())).thenReturn(Mono.error(new IllegalStateException("redis down")));
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, lockAlwaysAcquired());

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void staleIdentityIsRefreshedWithTheCurrentTokenAndRemovedRolesTakeEffect() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var oldIdentity = identity("user-1", "周琳琳", Set.of("tenant_admin", "reader"));
        var freshIdentity = identity("user-1", "周琳琳", Set.of("reader"));
        var stale = user("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(3600),
                oldIdentity, Instant.now().minus(Duration.ofMinutes(5)));
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(stale));
        when(oauth2.fetchIdentity("at-1")).thenReturn(Mono.just(freshIdentity));
        when(sessions.update(any(), any())).thenReturn(Mono.empty());
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, lockAlwaysAcquired());

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .assertNext(result -> {
                    var refreshed = (AuthSessionStore.AuthUser) result;
                    org.assertj.core.api.Assertions.assertThat(refreshed.identity()).isEqualTo(freshIdentity);
                    org.assertj.core.api.Assertions.assertThat(refreshed.identity().tenantAdmin()).isFalse();
                    org.assertj.core.api.Assertions.assertThat(refreshed.accessToken()).isEqualTo("at-1");
                })
                .verifyComplete();

        verify(oauth2).fetchIdentity("at-1");
        verify(oauth2, org.mockito.Mockito.never()).refresh(any());
    }

    @Test
    void staleIdentityProviderFailureDoesNotReturnPreviouslyStoredIdentity() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var stale = user("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(3600),
                Set.of("tenant_admin"), Instant.now().minus(Duration.ofMinutes(6)));
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(stale));
        when(oauth2.fetchIdentity("at-1"))
                .thenReturn(Mono.error(new OAuth2Client.OAuth2UnavailableException("userinfo provider failed")));
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, lockAlwaysAcquired());

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .expectError(OAuth2Client.OAuth2UnavailableException.class)
                .verify();

        verify(sessions, org.mockito.Mockito.never()).update(any(), any());
    }

    @Test
    void staleIdentityMappingFailureDoesNotReturnPreviouslyStoredPrivileges() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var stale = user("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(3600),
                Set.of("tenant_admin"), Instant.now().minus(Duration.ofMinutes(6)));
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(stale));
        var mappingFailure = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> new JsonPointerOAuth2IdentityMapper(new OAuth2Properties(null, null, null, null,
                        null, null, null, null, null, null)).map(new ObjectMapper().createObjectNode()),
                IdentityMappingException.class);
        when(oauth2.fetchIdentity("at-1")).thenReturn(Mono.error(mappingFailure));
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, lockAlwaysAcquired());

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .expectError(IdentityMappingException.class)
                .verify();

        verify(sessions, org.mockito.Mockito.never()).update(any(), any());
    }

    @Test
    void concurrentNearExpiryAndStaleIdentityRequestsConvergeOnPersistedWinner() {
        var config = new RedisStandaloneConfiguration("192.168.3.232", 6379);
        config.setPassword("FeetxXuFkZIaM2Pq");
        config.setDatabase(1);
        var connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        var redis = new ReactiveStringRedisTemplate(connectionFactory);
        String appToken = null;
        try {
            var sessions = new AuthSessionStore(redis);
            var oauth2 = mock(OAuth2Client.class);
            var oldIdentity = identity("user-1", "周琳琳", Set.of("tenant_admin", "reader"));
            var freshIdentity = identity("user-1", "周琳琳", Set.of("reader"));
            appToken = sessions.create("at-1", "rt-1", Instant.now().minusSeconds(10), oldIdentity,
                    Instant.now().minus(Duration.ofMinutes(6))).block();
            AtomicInteger refreshCalls = new AtomicInteger();
            AtomicInteger identityCalls = new AtomicInteger();
            when(oauth2.refresh("rt-1")).thenReturn(Mono.defer(() -> {
                refreshCalls.incrementAndGet();
                return Mono.delay(Duration.ofMillis(150))
                        .thenReturn(new OAuth2Client.TokenResult("at-2", "rt-2", 3600));
            }));
            when(oauth2.fetchIdentity("at-2")).thenReturn(Mono.defer(() -> {
                identityCalls.incrementAndGet();
                return Mono.delay(Duration.ofMillis(100)).thenReturn(freshIdentity);
            }));
            var resolver = new CurrentUserArgumentResolver(sessions, oauth2, redis);

            var first = resolver.resolveArgument(null, null, bearer(appToken));
            var second = resolver.resolveArgument(null, null, bearer(appToken));

            StepVerifier.create(Flux.merge(first, second).collectList())
                    .assertNext(results -> {
                        org.assertj.core.api.Assertions.assertThat(results).hasSize(2);
                        org.assertj.core.api.Assertions.assertThat(results).allSatisfy(result -> {
                            var resolved = (AuthSessionStore.AuthUser) result;
                            org.assertj.core.api.Assertions.assertThat(resolved.accessToken()).isEqualTo("at-2");
                            org.assertj.core.api.Assertions.assertThat(resolved.refreshToken()).isEqualTo("rt-2");
                            org.assertj.core.api.Assertions.assertThat(resolved.identity()).isEqualTo(freshIdentity);
                            org.assertj.core.api.Assertions.assertThat(resolved.identity().tenantAdmin()).isFalse();
                        });
                    })
                    .verifyComplete();

            org.assertj.core.api.Assertions.assertThat(refreshCalls).hasValue(1);
            org.assertj.core.api.Assertions.assertThat(identityCalls).hasValue(1);
            var persisted = sessions.resolve(appToken).block();
            org.assertj.core.api.Assertions.assertThat(persisted.accessToken()).isEqualTo("at-2");
            org.assertj.core.api.Assertions.assertThat(persisted.identity()).isEqualTo(freshIdentity);
        } finally {
            if (appToken != null) {
                redis.delete("oauth2refreshlock:" + appToken).block();
                redis.delete("authsession:" + appToken).block();
            }
            connectionFactory.destroy();
        }
    }

    @Test
    void missingHeaderErrorsUnauthorized() {
        var resolver = new CurrentUserArgumentResolver(mock(AuthSessionStore.class), mock(OAuth2Client.class),
                mock(ReactiveStringRedisTemplate.class));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x").build());

        StepVerifier.create(resolver.resolveArgument(null, null, exchange))
                .expectError(UnauthorizedException.class)
                .verify();
    }

    @Test
    void unknownTokenErrorsUnauthorized() {
        var sessions = mock(AuthSessionStore.class);
        when(sessions.resolve("bad-token")).thenReturn(Mono.empty());
        var resolver = new CurrentUserArgumentResolver(sessions, mock(OAuth2Client.class),
                mock(ReactiveStringRedisTemplate.class));

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("bad-token")))
                .expectError(UnauthorizedException.class)
                .verify();
    }

    private static class Probe {
        void annotated(@CurrentUser AuthSessionStore.AuthUser user) {}
        void plain(String s) {}
    }
}
