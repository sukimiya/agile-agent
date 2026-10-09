package io.github.sukimiya.agileagent.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

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
        var user = new AuthSessionStore.AuthUser("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(3600), "admin");
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
        var expiring = new AuthSessionStore.AuthUser("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10), "admin");
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(expiring));
        when(oauth2.refresh("rt-1")).thenReturn(Mono.just(new OAuth2Client.TokenResult("at-2", "rt-2", 3600)));
        when(sessions.update(any(), any())).thenReturn(Mono.empty());
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, lockAlwaysAcquired());

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .assertNext(result -> {
                    var refreshed = (AuthSessionStore.AuthUser) result;
                    org.assertj.core.api.Assertions.assertThat(refreshed.accessToken()).isEqualTo("at-2");
                    org.assertj.core.api.Assertions.assertThat(refreshed.refreshToken()).isEqualTo("rt-2");
                })
                .verifyComplete();
        verify(sessions).update(org.mockito.ArgumentMatchers.eq("tok-1"), any());
    }

    @Test
    void keepsExistingRefreshTokenWhenProviderOmitsANewOne() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var expiring = new AuthSessionStore.AuthUser("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10), "admin");
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(expiring));
        when(oauth2.refresh("rt-1")).thenReturn(Mono.just(new OAuth2Client.TokenResult("at-2", null, 3600)));
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
        var expiring = new AuthSessionStore.AuthUser("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10), "admin");
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
        var expiring = new AuthSessionStore.AuthUser("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10), "admin");
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
        var expiring = new AuthSessionStore.AuthUser("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10), "admin");
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(expiring));
        when(oauth2.refresh("rt-1")).thenReturn(Mono.just(new OAuth2Client.TokenResult("at-2", "rt-2", 3600)));
        when(sessions.update(any(), any())).thenReturn(Mono.error(new IllegalStateException("redis down")));
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, lockAlwaysAcquired());

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void concurrentNearExpiryRequestsRefreshAtMostOnce() {
        var config = new RedisStandaloneConfiguration("192.168.3.232", 6379);
        config.setPassword("FeetxXuFkZIaM2Pq");
        config.setDatabase(1);
        var connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        var redis = new ReactiveStringRedisTemplate(connectionFactory);
        String token = "concurrent-" + java.util.UUID.randomUUID();
        try {
            var sessions = mock(AuthSessionStore.class);
            var oauth2 = mock(OAuth2Client.class);
            var expiring = new AuthSessionStore.AuthUser("user-1", "周琳琳", "at-1", "rt-1",
                    Instant.now().plusSeconds(10), "admin");
            when(sessions.resolve(token)).thenReturn(Mono.just(expiring));
            when(oauth2.refresh("rt-1")).thenReturn(Mono.just(new OAuth2Client.TokenResult("at-2", "rt-2", 3600)));
            when(sessions.update(any(), any())).thenReturn(Mono.empty());
            var resolver = new CurrentUserArgumentResolver(sessions, oauth2, redis);

            var first = resolver.resolveArgument(null, null, bearer(token));
            var second = resolver.resolveArgument(null, null, bearer(token));

            StepVerifier.create(Flux.merge(first, second).collectList())
                    .assertNext(results -> org.assertj.core.api.Assertions.assertThat(results).hasSize(2))
                    .verifyComplete();

            verify(oauth2, times(1)).refresh("rt-1");
        } finally {
            redis.delete("oauth2refreshlock:" + token).block();
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
