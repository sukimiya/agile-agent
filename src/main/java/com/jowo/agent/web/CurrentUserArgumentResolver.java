package com.jowo.agent.web;

import java.time.Instant;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;

import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.OAuth2Client;

import reactor.core.publisher.Mono;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final java.time.Duration REFRESH_SKEW = java.time.Duration.ofSeconds(30);

    private final AuthSessionStore sessions;
    private final OAuth2Client oauth2;

    public CurrentUserArgumentResolver(AuthSessionStore sessions, OAuth2Client oauth2) {
        this.sessions = sessions;
        this.oauth2 = oauth2;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class);
    }

    @Override
    public Mono<Object> resolveArgument(MethodParameter parameter, BindingContext bindingContext,
                                         ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        if (token == null) return Mono.error(new UnauthorizedException());
        return sessions.resolve(token)
                .switchIfEmpty(Mono.error(new UnauthorizedException()))
                .flatMap(user -> refreshIfNearExpiry(token, user))
                .cast(Object.class);
    }

    private Mono<AuthSessionStore.AuthUser> refreshIfNearExpiry(String token, AuthSessionStore.AuthUser user) {
        if (Instant.now().isBefore(user.expiresAt().minus(REFRESH_SKEW))) {
            return Mono.just(user);
        }
        return oauth2.refresh(user.refreshToken())
                .map(tokens -> new AuthSessionStore.AuthUser(user.userId(), user.name(),
                        tokens.accessToken(), tokens.refreshToken(),
                        Instant.now().plusSeconds(tokens.expiresInSeconds()), user.userType()))
                .flatMap(refreshed -> sessions.update(token, refreshed).thenReturn(refreshed))
                .onErrorMap(e -> !(e instanceof UnauthorizedException), e -> new UnauthorizedException());
    }
}
