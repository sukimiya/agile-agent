package io.github.sukimiya.agileagent.web;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;

import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.OAuth2Client;

import reactor.core.publisher.Mono;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserArgumentResolver.class);

    private static final Duration REFRESH_SKEW = Duration.ofSeconds(30);
    private static final String REFRESH_LOCK_PREFIX = "oauth2refreshlock:";
    private static final Duration REFRESH_LOCK_TTL = Duration.ofSeconds(10);

    private final AuthSessionStore sessions;
    private final OAuth2Client oauth2;
    private final ReactiveStringRedisTemplate redis;

    public CurrentUserArgumentResolver(AuthSessionStore sessions, OAuth2Client oauth2,
                                        ReactiveStringRedisTemplate redis) {
        this.sessions = sessions;
        this.oauth2 = oauth2;
        this.redis = redis;
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
        // Single-flight guard: the UI runs an SSE stream plus parallel REST calls, so several requests
        // for one session can all see "near expiry" before any of them persists the new token pair.
        // Against providers with refresh-token rotation + reuse detection, the second concurrent use of
        // the same refresh token looks like a replay and revokes the whole token family — a hard logout
        // during entirely normal usage. Only the SETNX winner refreshes; the losers keep using the
        // current (still-valid, just near-expiry) access token for this one request and will pick up the
        // refreshed pair from Redis on their next call.
        return redis.opsForValue().setIfAbsent(REFRESH_LOCK_PREFIX + token, "1", REFRESH_LOCK_TTL)
                .defaultIfEmpty(Boolean.FALSE)
                .flatMap(acquired -> Boolean.TRUE.equals(acquired) ? doRefresh(token, user) : Mono.just(user));
    }

    private Mono<AuthSessionStore.AuthUser> doRefresh(String token, AuthSessionStore.AuthUser user) {
        return oauth2.refresh(user.refreshToken())
                .map(tokens -> new AuthSessionStore.AuthUser(user.userId(), user.name(),
                        tokens.accessToken(),
                        // RFC 6749 §6 makes refresh_token optional in the response; non-rotating providers
                        // omit it and expect the existing one to keep being used. Storing null here would
                        // break every subsequent refresh for the lifetime of the session.
                        tokens.refreshToken() == null ? user.refreshToken() : tokens.refreshToken(),
                        Instant.now().plusSeconds(tokens.expiresInSeconds()), user.userType()))
                .flatMap(refreshed -> sessions.update(token, refreshed).thenReturn(refreshed))
                // On success the lock is deliberately left to expire with its TTL: a concurrent request
                // that read the pre-refresh session before we wrote it would otherwise grab a freed lock
                // and refresh a second time with the now-stale refresh token. On failure we release it so
                // the next request can retry immediately.
                .onErrorResume(e -> releaseRefreshLock(token).then(Mono.error(e)))
                .doOnError(e -> log.warn("OAuth2 access token refresh failed for current session", e))
                // Only a genuine auth failure (refresh token invalid/revoked) means "log in again".
                // Provider outages and Redis failures must not be relabelled as an invalid session.
                .onErrorMap(OAuth2Client.OAuth2AuthException.class, e -> new UnauthorizedException());
    }

    private Mono<Void> releaseRefreshLock(String token) {
        return redis.delete(REFRESH_LOCK_PREFIX + token).then().onErrorResume(e -> Mono.empty());
    }
}
