package com.jowo.agent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.jowo.agent.config.OAuth2Properties;
import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.OAuth2Client;
import com.jowo.agent.service.OAuth2StateStore;
import com.jowo.agent.web.AuthExceptionHandler;
import com.jowo.agent.web.CurrentUserArgumentResolver;
import com.jowo.agent.web.WebConfig;

import reactor.core.publisher.Mono;

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
        when(oauth2.fetchUserInfo("at-1"))
                .thenReturn(Mono.just(new OAuth2Client.UserInfo("u1", "周琳琳", "admin")));
        when(sessions.create(org.mockito.ArgumentMatchers.eq("u1"), org.mockito.ArgumentMatchers.eq("周琳琳"),
                org.mockito.ArgumentMatchers.eq("at-1"), org.mockito.ArgumentMatchers.eq("rt-1"),
                org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.eq("admin")))
                .thenReturn(Mono.just("app-tok"));

        client.get().uri("/api/auth/oauth2/callback?code=code-1&state=state-1")
                .cookie(STATE_COOKIE, "state-1")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "http://localhost:5173/login/callback#token=app-tok");
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
        when(sessions.resolve("app-tok")).thenReturn(Mono.just(
                new AuthSessionStore.AuthUser("u1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(3600), "admin")));

        client.get().uri("/api/auth/me").header("Authorization", "Bearer app-tok")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo("u1")
                .jsonPath("$.name").isEqualTo("周琳琳");
    }

    @Test
    void meReturns401WithoutToken() {
        client.get().uri("/api/auth/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void logoutRevokesRefreshTokenThenDeletesSessionAndReturns204() {
        when(sessions.resolve("app-tok")).thenReturn(Mono.just(
                new AuthSessionStore.AuthUser("u1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(3600), "admin")));
        when(oauth2.revoke("rt-1")).thenReturn(Mono.empty());
        when(sessions.delete("app-tok")).thenReturn(Mono.empty());

        client.post().uri("/api/auth/logout").header("Authorization", "Bearer app-tok")
                .exchange()
                .expectStatus().isNoContent();

        verify(oauth2).revoke("rt-1");
        verify(sessions).delete("app-tok");
    }

    @Test
    void logoutStillDeletesSessionAndReturns204WhenRevocationFails() {
        when(sessions.resolve("app-tok")).thenReturn(Mono.just(
                new AuthSessionStore.AuthUser("u1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(3600), "admin")));
        when(oauth2.revoke("rt-1"))
                .thenReturn(Mono.error(new OAuth2Client.OAuth2UnavailableException("revocation endpoint down")));
        when(sessions.delete("app-tok")).thenReturn(Mono.empty());

        client.post().uri("/api/auth/logout").header("Authorization", "Bearer app-tok")
                .exchange()
                .expectStatus().isNoContent();

        verify(sessions).delete("app-tok");
    }

    @Test
    void logoutDeletesSessionAndReturns204WhenSessionAlreadyGone() {
        when(sessions.resolve("app-tok")).thenReturn(Mono.empty());
        when(sessions.delete("app-tok")).thenReturn(Mono.empty());

        client.post().uri("/api/auth/logout").header("Authorization", "Bearer app-tok")
                .exchange()
                .expectStatus().isNoContent();

        verify(sessions).delete("app-tok");
        verify(oauth2, never()).revoke(any());
    }
}
