package io.github.sukimiya.agileagent.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.OAuth2Client;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
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
        when(redis.hasKey(anyString())).thenReturn(Mono.just(false));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(Flux.just(1L));
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
        when(sessions.delete("tok-1")).thenReturn(Mono.empty());
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
        var callOrder = org.mockito.Mockito.inOrder(sessions, oauth2);
        callOrder.verify(sessions).delete("tok-1");
        callOrder.verify(oauth2).refresh("rt-1");
        callOrder.verify(oauth2).fetchIdentity("at-2");
        callOrder.verify(sessions).update(org.mockito.ArgumentMatchers.eq("tok-1"), any());
    }

    @Test
    void keepsExistingRefreshTokenWhenProviderOmitsANewOne() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var expiring = user("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10),
                java.util.Set.of("admin"), Instant.now());
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(expiring));
        when(sessions.delete("tok-1")).thenReturn(Mono.empty());
        when(oauth2.refresh("rt-1")).thenReturn(Mono.just(new OAuth2Client.TokenResult("at-2", null, 3600)));
        when(oauth2.fetchIdentity("at-2")).thenReturn(Mono.just(expiring.identity()));
        when(sessions.update(any(), any())).thenReturn(Mono.empty());
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, lockAlwaysAcquired());

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .assertNext(result -> org.assertj.core.api.Assertions
                        .assertThat(((AuthSessionStore.AuthUser) result).refreshToken()).isEqualTo("rt-1"))
                .verifyComplete();
        verify(sessions).delete("tok-1");

        var persisted = org.mockito.ArgumentCaptor.forClass(AuthSessionStore.AuthUser.class);
        verify(sessions).update(org.mockito.ArgumentMatchers.eq("tok-1"), persisted.capture());
        org.assertj.core.api.Assertions.assertThat(persisted.getValue().refreshToken()).isEqualTo("rt-1");
        org.assertj.core.api.Assertions.assertThat(persisted.getValue().accessToken()).isEqualTo("at-2");
    }

    @Test
    void keepsExistingRefreshTokenWhenProviderReturnsTheUnchangedValue() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var expiring = user("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10),
                Set.of("admin"), Instant.now());
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(expiring));
        when(sessions.delete("tok-1")).thenReturn(Mono.empty());
        when(oauth2.refresh("rt-1")).thenReturn(Mono.just(new OAuth2Client.TokenResult("at-2", "rt-1", 3600)));
        when(oauth2.fetchIdentity("at-2")).thenReturn(Mono.just(expiring.identity()));
        when(sessions.update(any(), any())).thenReturn(Mono.empty());
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, lockAlwaysAcquired());

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .assertNext(result -> {
                    var refreshed = (AuthSessionStore.AuthUser) result;
                    org.assertj.core.api.Assertions.assertThat(refreshed.accessToken()).isEqualTo("at-2");
                    org.assertj.core.api.Assertions.assertThat(refreshed.refreshToken()).isEqualTo("rt-1");
                })
                .verifyComplete();

        var persisted = org.mockito.ArgumentCaptor.forClass(AuthSessionStore.AuthUser.class);
        verify(sessions).update(org.mockito.ArgumentMatchers.eq("tok-1"), persisted.capture());
        org.assertj.core.api.Assertions.assertThat(persisted.getValue().refreshToken()).isEqualTo("rt-1");
    }

    @Test
    void refreshNeverStartsWhenPreRefreshSessionInvalidationFails() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var expiring = user("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10),
                Set.of("admin"), Instant.now());
        var refreshCalls = new AtomicInteger();
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(expiring));
        when(sessions.delete("tok-1")).thenReturn(
                Mono.error(new IllegalStateException("redis unavailable")), Mono.empty());
        when(oauth2.refresh("rt-1")).thenReturn(Mono.defer(() -> {
            refreshCalls.incrementAndGet();
            return Mono.just(new OAuth2Client.TokenResult("at-2", "rt-2", 3600));
        }));
        when(oauth2.fetchIdentity("at-2")).thenReturn(Mono.just(identity("user-1", "周琳琳", Set.of("reader"))));
        when(sessions.update(any(), any())).thenReturn(Mono.empty());
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, lockAlwaysAcquired());

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .expectError(IllegalStateException.class)
                .verify();

        verify(sessions).delete("tok-1");
        verifyNoInteractions(oauth2);
        org.assertj.core.api.Assertions.assertThat(refreshCalls).hasValue(0);

        // The rejected attempt did not consume rt-1; after Redis is available, retry can use it.
        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .assertNext(result -> org.assertj.core.api.Assertions
                        .assertThat(((AuthSessionStore.AuthUser) result).accessToken()).isEqualTo("at-2"))
                .verifyComplete();
        org.assertj.core.api.Assertions.assertThat(refreshCalls).hasValue(1);
        verify(oauth2).refresh("rt-1");
    }

    @Test
    void cancellationAfterRotatedRefreshCannotRestoreTheOldSessionAfterLockExpiry() throws InterruptedException {
        var config = new RedisStandaloneConfiguration("192.168.3.232", 6379);
        config.setPassword("FeetxXuFkZIaM2Pq");
        config.setDatabase(1);
        var connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        var redis = new ReactiveStringRedisTemplate(connectionFactory);
        String appToken = null;
        reactor.core.Disposable request = null;
        try {
            var sessions = new AuthSessionStore(redis);
            var oauth2 = mock(OAuth2Client.class);
            var oldIdentity = identity("user-1", "周琳琳", Set.of("tenant_admin"));
            appToken = sessions.create("at-1", "rt-1", Instant.now().minusSeconds(10), oldIdentity,
                    Instant.now().minus(Duration.ofMinutes(6))).block();
            var refreshCalls = new AtomicInteger();
            var userinfoStarted = new CountDownLatch(1);
            when(oauth2.refresh("rt-1")).thenReturn(Mono.defer(() -> {
                refreshCalls.incrementAndGet();
                return Mono.just(new OAuth2Client.TokenResult("at-2", "rt-2", 3600));
            }));
            when(oauth2.fetchIdentity("at-2")).thenReturn(Mono.create(sink -> userinfoStarted.countDown()),
                    Mono.error(new OAuth2Client.OAuth2UnavailableException("userinfo unavailable")));
            var resolver = new CurrentUserArgumentResolver(sessions, oauth2, redis);

            request = resolver.resolveArgument(null, null, bearer(appToken)).subscribe();
            org.assertj.core.api.Assertions.assertThat(userinfoStarted.await(5, TimeUnit.SECONDS)).isTrue();
            org.assertj.core.api.Assertions.assertThat(sessions.resolve(appToken).blockOptional()).isEmpty();

            request.dispose();
            redis.delete("oauth2refreshlock:" + appToken).block(); // Simulate the bounded lease expiring.

            org.assertj.core.api.Assertions.assertThat(sessions.resolve(appToken).blockOptional()).isEmpty();
            StepVerifier.create(resolver.resolveArgument(null, null, bearer(appToken)))
                    .expectError(UnauthorizedException.class)
                    .verify();
            org.assertj.core.api.Assertions.assertThat(refreshCalls).hasValue(1);
        } finally {
            if (request != null) request.dispose();
            if (appToken != null) {
                redis.delete("oauth2refreshlock:" + appToken).block();
                redis.delete("authsession:" + appToken).block();
            }
            connectionFactory.destroy();
        }
    }

    @Test
    void refreshFailureErrorsUnauthorized() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var expiring = user("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10),
                java.util.Set.of("admin"), Instant.now());
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(expiring));
        when(sessions.delete("tok-1")).thenReturn(Mono.empty());
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
        when(sessions.delete("tok-1")).thenReturn(Mono.empty());
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
        when(sessions.delete("tok-1")).thenReturn(Mono.empty());
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, lockAlwaysAcquired());

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .expectError(IllegalStateException.class)
                .verify();
        verify(sessions).delete("tok-1");
    }

    @Test
    void postLockSessionRereadFailureReleasesOnlyTheAcquiredOwnerLock() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var stale = user("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10),
                Set.of("admin"), Instant.now());
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(stale),
                Mono.error(new IllegalStateException("redis read failed")));
        var redis = lockAlwaysAcquired();
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, redis);

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .expectError(IllegalStateException.class)
                .verify();

        verifyNoInteractions(oauth2);
        verify(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void postLockRereadUsesPersistedWinnerInsteadOfPreLockSnapshot() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var staleSnapshot = user("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10),
                Set.of("tenant_admin"), Instant.now().minus(Duration.ofMinutes(6)));
        var persistedWinner = user("user-1", "周琳琳", "at-2", "rt-2", Instant.now().plusSeconds(3600),
                Set.of("reader"), Instant.now());
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(staleSnapshot), Mono.just(persistedWinner));
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, lockAlwaysAcquired());

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .expectNext(persistedWinner)
                .verifyComplete();

        verifyNoInteractions(oauth2);
    }

    @Test
    void nonRotatingRefreshFailureFailsClosedInsteadOfReusingPotentiallyConsumedRefreshToken() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var stale = user("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10),
                Set.of("admin"), Instant.now().minus(Duration.ofMinutes(6)));
        var sessionPresent = new java.util.concurrent.atomic.AtomicBoolean(true);
        when(sessions.resolve("tok-1")).thenAnswer(ignored -> sessionPresent.get()
                ? Mono.just(stale) : Mono.empty());
        when(sessions.delete("tok-1")).thenAnswer(ignored -> {
            sessionPresent.set(false);
            return Mono.empty();
        });
        when(oauth2.refresh("rt-1")).thenReturn(Mono.just(new OAuth2Client.TokenResult("at-2", null, 3600)));
        when(oauth2.fetchIdentity("at-2")).thenReturn(Mono.error(
                new OAuth2Client.OAuth2UnavailableException("userinfo unavailable")));
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, lockAlwaysAcquired());

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .expectError(OAuth2Client.OAuth2UnavailableException.class)
                .verify();
        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .expectError(UnauthorizedException.class)
                .verify();

        verify(oauth2, times(1)).refresh("rt-1");
        verify(sessions, times(1)).delete("tok-1");
    }

    @Test
    void rotatedRefreshThenSessionWriteFailureInvalidatesSessionBeforeRetry() {
        var config = new RedisStandaloneConfiguration("192.168.3.232", 6379);
        config.setPassword("FeetxXuFkZIaM2Pq");
        config.setDatabase(1);
        var connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        var redis = new ReactiveStringRedisTemplate(connectionFactory);
        String appToken = null;
        try {
            var sessions = new AuthSessionStore(redis) {
                @Override
                public Mono<Void> update(String token, AuthUser refreshed) {
                    return Mono.error(new IllegalStateException("session write failed"));
                }
            };
            var oauth2 = mock(OAuth2Client.class);
            var stale = identity("user-1", "周琳琳", Set.of("tenant_admin"));
            appToken = sessions.create("at-1", "rt-1", Instant.now().minusSeconds(10), stale,
                    Instant.now().minus(Duration.ofMinutes(6))).block();
            var refreshCalls = new AtomicInteger();
            when(oauth2.refresh("rt-1")).thenReturn(Mono.defer(() -> {
                refreshCalls.incrementAndGet();
                return Mono.just(new OAuth2Client.TokenResult("at-2", "rt-2", 3600));
            }));
            when(oauth2.fetchIdentity("at-2"))
                    .thenReturn(Mono.just(identity("user-1", "周琳琳", Set.of("reader"))));
            var resolver = new CurrentUserArgumentResolver(sessions, oauth2, redis);

            StepVerifier.create(resolver.resolveArgument(null, null, bearer(appToken)))
                    .expectError(IllegalStateException.class)
                    .verify();

            org.assertj.core.api.Assertions.assertThat(sessions.resolve(appToken).blockOptional()).isEmpty();
            StepVerifier.create(resolver.resolveArgument(null, null, bearer(appToken)))
                    .expectError(UnauthorizedException.class)
                    .verify();
            org.assertj.core.api.Assertions.assertThat(refreshCalls).hasValue(1);
        } finally {
            if (appToken != null) {
                redis.delete("oauth2refreshlock:" + appToken).block();
                redis.delete("authsession:" + appToken).block();
            }
            connectionFactory.destroy();
        }
    }

    @Test
    void rotatedRefreshThenIdentityFailureInvalidatesSessionAndCannotDeleteSuccessorLock()
            throws InterruptedException {
        var config = new RedisStandaloneConfiguration("192.168.3.232", 6379);
        config.setPassword("FeetxXuFkZIaM2Pq");
        config.setDatabase(1);
        var connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        var redis = new ReactiveStringRedisTemplate(connectionFactory);
        String appToken = null;
        AtomicReference<reactor.core.publisher.MonoSink<IdentityContext>> identitySink = new AtomicReference<>();
        try {
            var sessions = new AuthSessionStore(redis);
            var oauth2 = mock(OAuth2Client.class);
            var oldIdentity = identity("user-1", "周琳琳", Set.of("tenant_admin"));
            appToken = sessions.create("at-1", "rt-1", Instant.now().minusSeconds(10), oldIdentity,
                    Instant.now().minus(Duration.ofMinutes(6))).block();
            var refreshCalls = new AtomicInteger();
            var userinfoStarted = new CountDownLatch(1);
            when(oauth2.refresh("rt-1")).thenReturn(Mono.defer(() -> {
                refreshCalls.incrementAndGet();
                return Mono.just(new OAuth2Client.TokenResult("at-2", "rt-2", 3600));
            }));
            when(oauth2.fetchIdentity("at-2")).thenReturn(Mono.create(sink -> {
                identitySink.set(sink);
                userinfoStarted.countDown();
            }), Mono.error(new OAuth2Client.OAuth2UnavailableException("provider unavailable")));
            var resolver = new CurrentUserArgumentResolver(sessions, oauth2, redis);
            var finished = new CountDownLatch(1);
            var failure = new AtomicReference<Throwable>();

            resolver.resolveArgument(null, null, bearer(appToken))
                    .subscribe(ignored -> {}, error -> { failure.set(error); finished.countDown(); }, finished::countDown);

            org.assertj.core.api.Assertions.assertThat(userinfoStarted.await(5, TimeUnit.SECONDS)).isTrue();
            String lockKey = "oauth2refreshlock:" + appToken;
            String oldOwner = redis.opsForValue().get(lockKey).block();
            org.assertj.core.api.Assertions.assertThat(oldOwner).isNotBlank()
                    .isNotEqualTo("1")
                    .doesNotContain("rt-1", "rt-2", "at-1", "at-2");
            redis.opsForValue().set(lockKey, "successor-owner", Duration.ofMinutes(2)).block();

            identitySink.get().error(new OAuth2Client.OAuth2UnavailableException("userinfo unavailable"));

            org.assertj.core.api.Assertions.assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            org.assertj.core.api.Assertions.assertThat(failure.get())
                    .isInstanceOf(OAuth2Client.OAuth2UnavailableException.class);
            org.assertj.core.api.Assertions.assertThat(redis.opsForValue().get(lockKey).block())
                    .isEqualTo("successor-owner");
            org.assertj.core.api.Assertions.assertThat(sessions.resolve(appToken).blockOptional()).isEmpty();

            redis.delete(lockKey).block();
            StepVerifier.create(resolver.resolveArgument(null, null, bearer(appToken)))
                    .expectError(UnauthorizedException.class)
                    .verify();
            org.assertj.core.api.Assertions.assertThat(refreshCalls).hasValue(1);
        } finally {
            if (appToken != null) {
                identitySink.updateAndGet(sink -> {
                    if (sink != null) sink.error(new IllegalStateException("test cleanup"));
                    return null;
                });
                redis.delete("oauth2refreshlock:" + appToken).block();
                redis.delete("authsession:" + appToken).block();
            }
            connectionFactory.destroy();
        }
    }

    @Test
    void refreshLeaseOutlastsOwnerOperationAndCleanupBudgets() {
        org.assertj.core.api.Assertions.assertThat(CurrentUserArgumentResolver.LOCKED_OPERATION_TIMEOUT)
                .isEqualTo(Duration.ofSeconds(75));
        org.assertj.core.api.Assertions.assertThat(CurrentUserArgumentResolver.LOCK_ACQUIRE_TIMEOUT)
                .isEqualTo(Duration.ofSeconds(5));
        org.assertj.core.api.Assertions.assertThat(CurrentUserArgumentResolver.LOCK_CLEANUP_TIMEOUT)
                .isEqualTo(Duration.ofSeconds(10));
        org.assertj.core.api.Assertions.assertThat(CurrentUserArgumentResolver.SESSION_INVALIDATION_TIMEOUT)
                .isEqualTo(Duration.ofSeconds(10));
        org.assertj.core.api.Assertions.assertThat(CurrentUserArgumentResolver.WINNER_WAIT_TIMEOUT)
                .isEqualTo(Duration.ofSeconds(105));
        org.assertj.core.api.Assertions.assertThat(CurrentUserArgumentResolver.LOCKED_OPERATION_TIMEOUT
                        .plus(CurrentUserArgumentResolver.LOCK_ACQUIRE_TIMEOUT)
                        .plus(CurrentUserArgumentResolver.LOCK_CLEANUP_TIMEOUT))
                .isLessThan(CurrentUserArgumentResolver.REFRESH_LOCK_TTL);
        org.assertj.core.api.Assertions.assertThat(CurrentUserArgumentResolver.REFRESH_LOCK_TTL)
                .isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void ownerOperationTimeoutBoundsAnUnresponsiveRefreshProvider() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var expiring = user("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10),
                Set.of("admin"), Instant.now());
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(expiring));
        when(sessions.delete("tok-1")).thenReturn(Mono.empty());
        when(oauth2.refresh("rt-1")).thenReturn(Mono.never());
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, lockAlwaysAcquired());

        StepVerifier.withVirtualTime(() -> resolver.resolveArgument(null, null, bearer("tok-1")))
                .thenAwait(CurrentUserArgumentResolver.LOCKED_OPERATION_TIMEOUT)
                .expectError(java.util.concurrent.TimeoutException.class)
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void slowRedisReadsDoNotOverflowWinnerPolling() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var stale = user("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10),
                Set.of("tenant_admin"), Instant.now().minus(Duration.ofMinutes(6)));
        var persistedWinner = user("user-1", "周琳琳", "at-2", "rt-2", Instant.now().plusSeconds(3600),
                Set.of("reader"), Instant.now());
        var resolveCalls = new AtomicInteger();
        when(sessions.resolve("tok-1")).thenAnswer(invocation -> {
            int call = resolveCalls.incrementAndGet();
            if (call == 1) return Mono.just(stale);
            // Model Redis reads slower than the 100ms polling cadence to expose timer overrun.
            return Mono.delay(Duration.ofMillis(250))
                    .thenReturn(call < 4 ? stale : persistedWinner);
        });
        var redis = mock(ReactiveStringRedisTemplate.class);
        var ops = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(false));
        when(redis.hasKey("oauth2refreshlock:tok-1")).thenReturn(Mono.just(true));
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2, redis);

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("tok-1")))
                .expectNext(persistedWinner)
                .verifyComplete();
    }

    @Test
    void loserConvergesWhenSessionIsTemporarilyAbsentDuringWinnerRefresh() throws InterruptedException {
        var config = new RedisStandaloneConfiguration("192.168.3.232", 6379);
        config.setPassword("FeetxXuFkZIaM2Pq");
        config.setDatabase(1);
        var connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        var redis = new ReactiveStringRedisTemplate(connectionFactory);
        String appToken = null;
        var refreshSink = new AtomicReference<reactor.core.publisher.MonoSink<OAuth2Client.TokenResult>>();
        var refreshStarted = new CountDownLatch(1);
        var loserSawMissingSession = new CountDownLatch(1);
        var resolveCalls = new AtomicInteger();
        var firstDone = new CountDownLatch(1);
        var secondDone = new CountDownLatch(1);
        var results = new ConcurrentLinkedQueue<AuthSessionStore.AuthUser>();
        var failures = new ConcurrentLinkedQueue<Throwable>();
        try {
            var sessions = new AuthSessionStore(redis) {
                @Override
                public Mono<AuthUser> resolve(String token) {
                    int call = resolveCalls.incrementAndGet();
                    return super.resolve(token).doOnEach(signal -> {
                        if (call == 3 && signal.isOnComplete()) loserSawMissingSession.countDown();
                    });
                }
            };
            var oauth2 = mock(OAuth2Client.class);
            var oldIdentity = identity("user-1", "周琳琳", Set.of("tenant_admin", "reader"));
            var freshIdentity = identity("user-1", "周琳琳", Set.of("reader"));
            appToken = sessions.create("at-1", "rt-1", Instant.now().minusSeconds(10), oldIdentity,
                    Instant.now().minus(Duration.ofMinutes(6))).block();
            var refreshCalls = new AtomicInteger();
            when(oauth2.refresh("rt-1")).thenReturn(Mono.defer(() -> {
                refreshCalls.incrementAndGet();
                return Mono.create(sink -> {
                    refreshSink.set(sink);
                    refreshStarted.countDown();
                });
            }));
            when(oauth2.fetchIdentity("at-2")).thenReturn(Mono.just(freshIdentity));
            var resolver = new CurrentUserArgumentResolver(sessions, oauth2, redis);

            resolver.resolveArgument(null, null, bearer(appToken)).subscribe(
                    value -> results.add((AuthSessionStore.AuthUser) value), failures::add, firstDone::countDown);
            org.assertj.core.api.Assertions.assertThat(refreshStarted.await(5, TimeUnit.SECONDS)).isTrue();
            org.assertj.core.api.Assertions.assertThat(redis.hasKey("authsession:" + appToken).block()).isFalse();

            resolver.resolveArgument(null, null, bearer(appToken)).subscribe(
                    value -> results.add((AuthSessionStore.AuthUser) value), failures::add, secondDone::countDown);
            org.assertj.core.api.Assertions.assertThat(loserSawMissingSession.await(5, TimeUnit.SECONDS)).isTrue();
            refreshSink.get().success(new OAuth2Client.TokenResult("at-2", "rt-2", 3600));

            org.assertj.core.api.Assertions.assertThat(firstDone.await(5, TimeUnit.SECONDS)).isTrue();
            org.assertj.core.api.Assertions.assertThat(secondDone.await(5, TimeUnit.SECONDS)).isTrue();
            org.assertj.core.api.Assertions.assertThat(failures).isEmpty();
            org.assertj.core.api.Assertions.assertThat(results).hasSize(2).allSatisfy(resolved -> {
                org.assertj.core.api.Assertions.assertThat(resolved.accessToken()).isEqualTo("at-2");
                org.assertj.core.api.Assertions.assertThat(resolved.refreshToken()).isEqualTo("rt-2");
                org.assertj.core.api.Assertions.assertThat(resolved.identity()).isEqualTo(freshIdentity);
                org.assertj.core.api.Assertions.assertThat(resolved.identity().tenantAdmin()).isFalse();
            });
            org.assertj.core.api.Assertions.assertThat(refreshCalls).hasValue(1);
        } finally {
            if (appToken != null) {
                var sink = refreshSink.get();
                if (sink != null) sink.error(new IllegalStateException("test cleanup"));
                redis.delete("oauth2refreshlock:" + appToken).block();
                redis.delete("authsession:" + appToken).block();
            }
            connectionFactory.destroy();
        }
    }

    @Test
    void loserWaitHasAnElapsedTimeoutEvenWhenRedisReadStalls() {
        var sessions = mock(AuthSessionStore.class);
        var redis = mock(ReactiveStringRedisTemplate.class);
        when(sessions.resolve("tok-1")).thenReturn(Mono.empty(), Mono.never());
        when(redis.hasKey("oauth2refreshlock:tok-1")).thenReturn(Mono.just(true));
        var resolver = new CurrentUserArgumentResolver(sessions, mock(OAuth2Client.class), redis);

        StepVerifier.withVirtualTime(() -> resolver.resolveArgument(null, null, bearer("tok-1")))
                .thenAwait(Duration.ofSeconds(105))
                .expectError(OAuth2Client.OAuth2UnavailableException.class)
                .verify(Duration.ofSeconds(5));
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
            org.assertj.core.api.Assertions.assertThat(redis.hasKey("oauth2refreshlock:" + appToken).block())
                    .isFalse();
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
        var redis = mock(ReactiveStringRedisTemplate.class);
        when(sessions.resolve("bad-token")).thenReturn(Mono.empty());
        when(redis.hasKey("oauth2refreshlock:bad-token")).thenReturn(Mono.just(false));
        var resolver = new CurrentUserArgumentResolver(sessions, mock(OAuth2Client.class), redis);

        StepVerifier.create(resolver.resolveArgument(null, null, bearer("bad-token")))
                .expectError(UnauthorizedException.class)
                .verify();
    }

    private static class Probe {
        void annotated(@CurrentUser AuthSessionStore.AuthUser user) {}
        void plain(String s) {}
    }
}
