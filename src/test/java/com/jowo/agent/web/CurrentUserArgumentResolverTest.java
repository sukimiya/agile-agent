package com.jowo.agent.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.jowo.agent.service.AuthSessionStore;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CurrentUserArgumentResolverTest {

    @Test
    void supportsParameterOnlyWhenAnnotationPresent() throws NoSuchMethodException {
        var sessions = mock(AuthSessionStore.class);
        var resolver = new CurrentUserArgumentResolver(sessions);
        var annotatedParam = new MethodParameter(
                Probe.class.getDeclaredMethod("annotated", AuthSessionStore.AuthUser.class), 0);
        var plainParam = new MethodParameter(
                Probe.class.getDeclaredMethod("plain", String.class), 0);

        org.assertj.core.api.Assertions.assertThat(resolver.supportsParameter(annotatedParam)).isTrue();
        org.assertj.core.api.Assertions.assertThat(resolver.supportsParameter(plainParam)).isFalse();
    }

    @Test
    void resolvesUserFromBearerToken() {
        var sessions = mock(AuthSessionStore.class);
        var user = new AuthSessionStore.AuthUser("user-1", "周琳琳", "sl-tok");
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(user));
        var resolver = new CurrentUserArgumentResolver(sessions);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/x").header("Authorization", "Bearer tok-1").build());

        StepVerifier.create(resolver.resolveArgument(null, null, exchange))
                .expectNext(user)
                .verifyComplete();
    }

    @Test
    void missingHeaderErrorsUnauthorized() {
        var resolver = new CurrentUserArgumentResolver(mock(AuthSessionStore.class));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x").build());

        StepVerifier.create(resolver.resolveArgument(null, null, exchange))
                .expectError(UnauthorizedException.class)
                .verify();
    }

    @Test
    void unknownTokenErrorsUnauthorized() {
        var sessions = mock(AuthSessionStore.class);
        when(sessions.resolve("bad-token")).thenReturn(Mono.empty());
        var resolver = new CurrentUserArgumentResolver(sessions);
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
