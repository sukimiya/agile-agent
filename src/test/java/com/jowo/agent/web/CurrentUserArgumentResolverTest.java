package com.jowo.agent.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.OAuth2Client;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CurrentUserArgumentResolverTest {

    @Test
    void supportsParameterOnlyWhenAnnotationPresent() throws NoSuchMethodException {
        var resolver = new CurrentUserArgumentResolver(mock(AuthSessionStore.class), mock(OAuth2Client.class));
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
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/x").header("Authorization", "Bearer tok-1").build());

        StepVerifier.create(resolver.resolveArgument(null, null, exchange))
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
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/x").header("Authorization", "Bearer tok-1").build());

        StepVerifier.create(resolver.resolveArgument(null, null, exchange))
                .assertNext(result -> {
                    var refreshed = (AuthSessionStore.AuthUser) result;
                    org.assertj.core.api.Assertions.assertThat(refreshed.accessToken()).isEqualTo("at-2");
                    org.assertj.core.api.Assertions.assertThat(refreshed.refreshToken()).isEqualTo("rt-2");
                })
                .verifyComplete();
        verify(sessions).update(org.mockito.ArgumentMatchers.eq("tok-1"), any());
    }

    @Test
    void missingHeaderErrorsUnauthorized() {
        var resolver = new CurrentUserArgumentResolver(mock(AuthSessionStore.class), mock(OAuth2Client.class));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x").build());

        StepVerifier.create(resolver.resolveArgument(null, null, exchange))
                .expectError(UnauthorizedException.class)
                .verify();
    }

    @Test
    void unknownTokenErrorsUnauthorized() {
        var sessions = mock(AuthSessionStore.class);
        when(sessions.resolve("bad-token")).thenReturn(Mono.empty());
        var resolver = new CurrentUserArgumentResolver(sessions, mock(OAuth2Client.class));
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/x").header("Authorization", "Bearer bad-token").build());

        StepVerifier.create(resolver.resolveArgument(null, null, exchange))
                .expectError(UnauthorizedException.class)
                .verify();
    }

    private static class Probe {
        void annotated(@CurrentUser AuthSessionStore.AuthUser user) {}
        void plain(String s) {}
    }
}
