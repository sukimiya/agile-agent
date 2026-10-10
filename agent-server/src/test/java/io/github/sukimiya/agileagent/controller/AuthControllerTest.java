package io.github.sukimiya.agileagent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.sukimiya.agileagent.TestAuthUsers.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.github.sukimiya.agileagent.LocalRedisTestServer;
import io.github.sukimiya.agileagent.config.OAuth2Properties;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.OAuth2Client;
import io.github.sukimiya.agileagent.service.OAuth2StateStore;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.AuthExceptionHandler;
import io.github.sukimiya.agileagent.web.CurrentUserArgumentResolver;
import io.github.sukimiya.agileagent.web.UnauthorizedException;
import io.github.sukimiya.agileagent.web.WebConfig;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

@WebFluxTest(AuthController.class)
@Import({WebConfig.class, AuthExceptionHandler.class, CurrentUserArgumentResolver.class})
class AuthControllerTest {

    private static final String STATE_COOKIE = "oauth2_state_binding";

    @Autowired WebTestClient client;
    @MockBean OAuth2Client oauth2;
    @MockBean OAuth2StateStore states;
    @MockBean AuthSessionStore sessions;
    @MockBean OAuth2Properties props;
    @MockBean org.springframework.data.redis.core.ReactiveStringRedisTemplate redis;

    @Test
    void authorizeRedirectsToProviderAuthorizationUrl() {
        when(states.save(org.mockito.ArgumentMatchers.anyString())).thenReturn(Mono.just("state-1"));
        when(oauth2.buildAuthorizationUrl(org.mockito.ArgumentMatchers.eq("state-1"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("https://idp.example.com/oauth2/authorize?state=state-1");

        client.get().uri("/api/auth/oauth2/authorize")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "https://idp.example.com/oauth2/authorize?state=state-1")
                .expectCookie().valueEquals(STATE_COOKIE, "state-1")
                .expectCookie().httpOnly(STATE_COOKIE, true)
                .expectCookie().sameSite(STATE_COOKIE, "Lax");
    }

    @Test
    void callbackExchangesCodeAndRedirectsWithAppToken() {
        when(props.frontendRedirectUri()).thenReturn("http://localhost:5173/login/callback");
        when(states.consume("state-1")).thenReturn(Mono.just("verifier-1"));
        when(oauth2.exchangeCode("code-1", "verifier-1"))
                .thenReturn(Mono.just(new OAuth2Client.TokenResult("at-1", "rt-1", 3600)));
        var identity = new IdentityContext("u1", "周琳琳", "tenant-1", "Tenant One", "org-1", "Org One",
                java.util.Set.of("admin"), java.util.Set.of("org-1"), java.util.Set.of("org-1"), false, false);
        when(oauth2.fetchIdentity("at-1")).thenReturn(Mono.just(identity));
        when(sessions.create(org.mockito.ArgumentMatchers.eq("at-1"), org.mockito.ArgumentMatchers.eq("rt-1"),
                org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.eq(identity),
                org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(Mono.just("app-tok"));

        client.get().uri("/api/auth/oauth2/callback?code=code-1&state=state-1")
                .cookie(STATE_COOKIE, "state-1")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "http://localhost:5173/login/callback#token=app-tok");

        var fetchedAt = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(sessions).create(org.mockito.ArgumentMatchers.eq("at-1"), org.mockito.ArgumentMatchers.eq("rt-1"),
                org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.eq(identity),
                fetchedAt.capture());
        assertThat(fetchedAt.getValue()).isAfter(Instant.now().minusSeconds(30));
    }

    @Test
    void callbackRedirectsWithErrorFragmentOnAuthFailure() {
        when(props.frontendRedirectUri()).thenReturn("http://localhost:5173/login/callback");
        when(states.consume("state-1")).thenReturn(Mono.just("verifier-1"));
        when(oauth2.exchangeCode("bad-code", "verifier-1"))
                .thenReturn(Mono.error(new OAuth2Client.OAuth2AuthException("invalid_grant")));

        client.get().uri("/api/auth/oauth2/callback?code=bad-code&state=state-1")
                .cookie(STATE_COOKIE, "state-1")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "http://localhost:5173/login/callback#error=auth_failed");
    }

    @Test
    void callbackRedirectsWithProviderUnavailableFragmentWhenIdpIsDown() {
        when(props.frontendRedirectUri()).thenReturn("http://localhost:5173/login/callback");
        when(states.consume("state-1")).thenReturn(Mono.just("verifier-1"));
        when(oauth2.exchangeCode("code-1", "verifier-1"))
                .thenReturn(Mono.error(new OAuth2Client.OAuth2UnavailableException("connection refused")));

        client.get().uri("/api/auth/oauth2/callback?code=code-1&state=state-1")
                .cookie(STATE_COOKIE, "state-1")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "http://localhost:5173/login/callback#error=provider_unavailable");
    }

    @Test
    void callbackRedirectsWithErrorFragmentWhenProviderReportsErrorWithoutCode() {
        when(props.frontendRedirectUri()).thenReturn("http://localhost:5173/login/callback");

        client.get().uri("/api/auth/oauth2/callback?error=access_denied&state=state-1")
                .cookie(STATE_COOKIE, "state-1")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "http://localhost:5173/login/callback#error=auth_failed");

        verify(states, never()).consume(any());
        verify(oauth2, never()).exchangeCode(any(), any());
    }

    @Test
    void callbackRedirectsWithErrorFragmentWhenStateBindingCookieIsMissing() {
        when(props.frontendRedirectUri()).thenReturn("http://localhost:5173/login/callback");

        client.get().uri("/api/auth/oauth2/callback?code=code-1&state=state-1")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "http://localhost:5173/login/callback#error=auth_failed");

        verify(states, never()).consume(any());
        verify(oauth2, never()).exchangeCode(any(), any());
    }

    @Test
    void callbackRedirectsWithErrorFragmentWhenStateBindingCookieMismatches() {
        when(props.frontendRedirectUri()).thenReturn("http://localhost:5173/login/callback");

        client.get().uri("/api/auth/oauth2/callback?code=code-1&state=state-1")
                .cookie(STATE_COOKIE, "someone-elses-state")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "http://localhost:5173/login/callback#error=auth_failed");

        verify(states, never()).consume(any());
        verify(oauth2, never()).exchangeCode(any(), any());
    }

    @Test
    void callbackRedirectsWithErrorFragmentWhenStateIsUnknownOrExpired() {
        when(props.frontendRedirectUri()).thenReturn("http://localhost:5173/login/callback");
        when(states.consume("state-1")).thenReturn(Mono.empty());

        client.get().uri("/api/auth/oauth2/callback?code=code-1&state=state-1")
                .cookie(STATE_COOKIE, "state-1")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "http://localhost:5173/login/callback#error=auth_failed");

        verify(oauth2, never()).exchangeCode(any(), any());
    }

    @Test
    void meReturnsCurrentUserForValidToken() {
        var identity = new IdentityContext("u1", "周琳琳", "tenant-1", "Tenant One", "org-1", "Org One",
                java.util.Set.of("admin"), java.util.Set.of("org-1"), java.util.Set.of("org-1"), false, true);
        when(sessions.resolve("app-tok")).thenReturn(Mono.just(user("u1", "周琳琳", "at-1", "rt-1",
                Instant.now().plusSeconds(3600), identity, Instant.now())));

        client.get().uri("/api/auth/me").header("Authorization", "Bearer app-tok")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo("u1")
                .jsonPath("$.name").isEqualTo("周琳琳")
                .jsonPath("$.subject").isEqualTo("u1")
                .jsonPath("$.displayName").isEqualTo("周琳琳")
                .jsonPath("$.tenantId").isEqualTo("tenant-1")
                .jsonPath("$.tenantName").isEqualTo("Tenant One")
                .jsonPath("$.primaryOrgId").isEqualTo("org-1")
                .jsonPath("$.primaryOrgName").isEqualTo("Org One")
                .jsonPath("$.roles[0]").isEqualTo("admin")
                .jsonPath("$.tenantAdmin").isEqualTo(true)
                .jsonPath("$.accessToken").doesNotExist()
                .jsonPath("$.refreshToken").doesNotExist();
    }

    @Test
    void meReturns401WithoutToken() {
        client.get().uri("/api/auth/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().json("{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");
    }

    @Test
    void logoutInvalidatesSessionBeforeRevokingRefreshTokenAndReturns204() {
        when(sessions.resolve("app-tok")).thenReturn(Mono.just(user("u1", "周琳琳", "at-1", "rt-1",
                Instant.now().plusSeconds(3600), java.util.Set.of("admin"), Instant.now())));
        when(oauth2.revoke("rt-1")).thenReturn(Mono.empty());
        when(sessions.logout("app-tok")).thenReturn(Mono.empty());

        client.post().uri("/api/auth/logout").header("Authorization", "Bearer app-tok")
                .exchange()
                .expectStatus().isNoContent();

        var order = inOrder(sessions, oauth2);
        order.verify(sessions).resolve("app-tok");
        order.verify(sessions).logout("app-tok");
        order.verify(oauth2).revoke("rt-1");
    }

    @Test
    void logoutStillInvalidatesSessionAndReturns204WhenRevocationFails() {
        when(sessions.resolve("app-tok")).thenReturn(Mono.just(user("u1", "周琳琳", "at-1", "rt-1",
                Instant.now().plusSeconds(3600), java.util.Set.of("admin"), Instant.now())));
        when(oauth2.revoke("rt-1"))
                .thenReturn(Mono.error(new OAuth2Client.OAuth2UnavailableException("revocation endpoint down")));
        when(sessions.logout("app-tok")).thenReturn(Mono.empty());

        client.post().uri("/api/auth/logout").header("Authorization", "Bearer app-tok")
                .exchange()
                .expectStatus().isNoContent();

        verify(sessions).logout("app-tok");
    }

    @Test
    void logoutWinsOverAnInFlightRefreshAndPreventsSessionRecreation() throws InterruptedException {
        var config = LocalRedisTestServer.configuration(1);
        var connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        var realRedis = new ReactiveStringRedisTemplate(connectionFactory);
        var refreshStarted = new CountDownLatch(1);
        var refreshDone = new CountDownLatch(1);
        var refreshSink = new AtomicReference<MonoSink<OAuth2Client.TokenResult>>();
        var refreshError = new AtomicReference<Throwable>();
        String appToken = null;
        Disposable refreshRequest = null;
        try {
            var realSessions = new AuthSessionStore(realRedis);
            var oldIdentity = new IdentityContext("u1", "周琳琳", "tenant-1", "Tenant One", "org-1", "Org One",
                    Set.of("tenant_admin"), Set.of("org-1"), Set.of("org-1"), false, false);
            var freshIdentity = new IdentityContext("u1", "周琳琳", "tenant-1", "Tenant One", "org-1", "Org One",
                    Set.of("reader"), Set.of("org-1"), Set.of("org-1"), false, false);
            appToken = realSessions.create("at-1", "rt-1", Instant.now().minusSeconds(10), oldIdentity,
                    Instant.now().minus(Duration.ofMinutes(6))).block(Duration.ofSeconds(5));
            when(oauth2.refresh("rt-1")).thenReturn(Mono.create(sink -> {
                refreshSink.set(sink);
                refreshStarted.countDown();
            }));
            when(oauth2.fetchIdentity("at-2")).thenReturn(Mono.just(freshIdentity));

            var resolver = new CurrentUserArgumentResolver(realSessions, oauth2, realRedis);
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/auth/me").header("Authorization", "Bearer " + appToken).build());
            refreshRequest = resolver.resolveArgument(null, null, exchange)
                    .subscribe(ignored -> {}, error -> {
                        refreshError.set(error);
                        refreshDone.countDown();
                    }, refreshDone::countDown);
            assertThat(refreshStarted.await(5, TimeUnit.SECONDS)).isTrue();

            var directController = new AuthController(oauth2, states, realSessions, props);
            var logoutResponse = directController.logout("Bearer " + appToken).block(Duration.ofSeconds(5));
            assertThat(logoutResponse).isNotNull();
            assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            refreshSink.get().success(new OAuth2Client.TokenResult("at-2", "rt-2", 3600));
            assertThat(refreshDone.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(realSessions.resolve(appToken).blockOptional()).isEmpty();
            assertThat(refreshError.get()).isInstanceOf(UnauthorizedException.class);
            assertThat(realRedis.opsForValue().get("authsession:" + appToken).block())
                    .isEqualTo("__AUTH_SESSION_LOGGED_OUT_V1__");
            assertThat(realRedis.hasKey("authsession:logout:" + appToken).block()).isFalse();
            assertThat(realRedis.getExpire("authsession:" + appToken).block())
                    .isBetween(Duration.ofSeconds(1), Duration.ofDays(30));
            StepVerifier.create(resolver.resolveArgument(null, null, exchange))
                    .expectError(UnauthorizedException.class)
                    .verify();
        } finally {
            if (refreshRequest != null) refreshRequest.dispose();
            var sink = refreshSink.get();
            if (sink != null) sink.error(new IllegalStateException("test cleanup"));
            if (appToken != null) {
                realRedis.delete("oauth2refreshlock:" + appToken).block();
                realRedis.delete("authsession:" + appToken).block();
                realRedis.delete("authsession:logout:" + appToken).block();
            }
            connectionFactory.destroy();
        }
    }

    @Test
    void logoutBetweenOwnerRereadAndPreRefreshInvalidationCannotResurrectSession() throws InterruptedException {
        var config = LocalRedisTestServer.configuration(1);
        var connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        var realRedis = new ReactiveStringRedisTemplate(connectionFactory);
        var invalidationReached = new CountDownLatch(1);
        var refreshDone = new CountDownLatch(1);
        var allowInvalidation = Sinks.one();
        var refreshError = new AtomicReference<Throwable>();
        var refreshResult = new AtomicReference<AuthSessionStore.AuthUser>();
        String appToken = null;
        Disposable refreshRequest = null;
        try {
            var sessionDelegate = new AuthSessionStore(realRedis);
            var realSessions = spy(sessionDelegate);
            var oldIdentity = new IdentityContext("u1", "周琳琳", "tenant-1", "Tenant One", "org-1", "Org One",
                    Set.of("tenant_admin"), Set.of("org-1"), Set.of("org-1"), false, false);
            var freshIdentity = new IdentityContext("u1", "周琳琳", "tenant-1", "Tenant One", "org-1", "Org One",
                    Set.of("reader"), Set.of("org-1"), Set.of("org-1"), false, false);
            appToken = sessionDelegate.create("at-1", "rt-1", Instant.now().minusSeconds(10), oldIdentity,
                    Instant.now().minus(Duration.ofMinutes(6))).block(Duration.ofSeconds(5));
            String token = appToken;
            doAnswer(invocation -> {
                invalidationReached.countDown();
                String generation = invocation.getArgument(1);
                Duration generationTtl = invocation.getArgument(2);
                return allowInvalidation.asMono()
                        .then(Mono.defer(() -> sessionDelegate.beginRefresh(token, generation, generationTtl)));
            }).when(realSessions).beginRefresh(eq(token), anyString(), any(Duration.class));
            when(oauth2.revoke("rt-1")).thenReturn(Mono.empty());
            when(oauth2.refresh("rt-1"))
                    .thenReturn(Mono.just(new OAuth2Client.TokenResult("at-2", "rt-2", 3600)));
            when(oauth2.fetchIdentity("at-2")).thenReturn(Mono.just(freshIdentity));

            var resolver = new CurrentUserArgumentResolver(realSessions, oauth2, realRedis);
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/auth/me").header("Authorization", "Bearer " + token).build());
            refreshRequest = resolver.resolveArgument(null, null, exchange).subscribe(
                    value -> refreshResult.set((AuthSessionStore.AuthUser) value), error -> {
                        refreshError.set(error);
                        refreshDone.countDown();
                    }, refreshDone::countDown);

            assertThat(invalidationReached.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(realSessions.resolve(token).blockOptional()).isPresent();
            var directController = new AuthController(oauth2, states, realSessions, props);
            var logoutResponse = directController.logout("Bearer " + token).block(Duration.ofSeconds(5));
            assertThat(logoutResponse).isNotNull();
            assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(realRedis.opsForValue().get("authsession:" + token).block())
                    .isEqualTo("__AUTH_SESSION_LOGGED_OUT_V1__");

            allowInvalidation.tryEmitEmpty();
            assertThat(refreshDone.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(realSessions.resolve(token).blockOptional()).isEmpty();
            assertThat(realRedis.opsForValue().get("authsession:" + token).block())
                    .isEqualTo("__AUTH_SESSION_LOGGED_OUT_V1__");
            assertThat(refreshResult.get()).isNull();
            assertThat(refreshError.get()).isInstanceOf(UnauthorizedException.class);
            verify(oauth2, never()).refresh("rt-1");
            StepVerifier.create(resolver.resolveArgument(null, null, exchange))
                    .expectError(UnauthorizedException.class)
                    .verify();
        } finally {
            allowInvalidation.tryEmitEmpty();
            if (refreshRequest != null) refreshRequest.dispose();
            if (appToken != null) {
                realRedis.delete("oauth2refreshlock:" + appToken).block();
                realRedis.delete("authsession:" + appToken).block();
            }
            connectionFactory.destroy();
        }
    }

    @Test
    void logoutTombstonesAnAlreadyMissingSessionAndReturns204() {
        when(sessions.resolve("app-tok")).thenReturn(Mono.empty());
        when(sessions.logout("app-tok")).thenReturn(Mono.empty());

        client.post().uri("/api/auth/logout").header("Authorization", "Bearer app-tok")
                .exchange()
                .expectStatus().isNoContent();

        verify(sessions).logout("app-tok");
        verify(oauth2, never()).revoke(any());
    }

    @Test
    void logoutForUnknownBearerReturns204WithoutCreatingRedisKey() {
        var config = LocalRedisTestServer.configuration(1);
        var connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        var realRedis = new ReactiveStringRedisTemplate(connectionFactory);
        String unknownToken = "unknown-" + UUID.randomUUID();
        try {
            var realSessions = new AuthSessionStore(realRedis);
            var directController = new AuthController(oauth2, states, realSessions, props);

            var response = directController.logout("Bearer " + unknownToken).block(Duration.ofSeconds(5));

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(realRedis.hasKey("authsession:" + unknownToken).block()).isFalse();
            assertThat(realRedis.hasKey("oauth2refreshlock:" + unknownToken).block()).isFalse();
        } finally {
            realRedis.delete("authsession:" + unknownToken).block();
            connectionFactory.destroy();
        }
    }
}
