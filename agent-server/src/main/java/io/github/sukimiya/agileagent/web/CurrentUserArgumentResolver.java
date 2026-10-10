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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserArgumentResolver.class);

    private static final Duration REFRESH_SKEW = Duration.ofSeconds(30);
    private static final Duration IDENTITY_FRESHNESS = Duration.ofMinutes(5);
    private static final String REFRESH_LOCK_PREFIX = "oauth2refreshlock:";
    // Token refresh and userinfo each have 15s connect + 15s response limits in OAuth2Client.
    private static final Duration REFRESH_LOCK_TTL = Duration.ofSeconds(65);
    private static final Duration WINNER_POLL_INTERVAL = Duration.ofMillis(100);
    // Slightly outlast the lock so a canceled or failed winner cannot make a loser return stale state.
    private static final int WINNER_POLL_COUNT = 700;

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
                .flatMap(user -> refreshIfRequired(token, user))
                .cast(Object.class);
    }

    /**
     * Token validity and identity freshness share one per-app-session single-flight. A session is
     * returned only when both its access token and mapped identity are current.
     */
    private Mono<AuthSessionStore.AuthUser> refreshIfRequired(String token, AuthSessionStore.AuthUser user) {
        if (isReadyToReturn(user, Instant.now())) {
            return Mono.just(user);
        }
        String lockKey = REFRESH_LOCK_PREFIX + token;
        return redis.opsForValue().setIfAbsent(lockKey, "1", REFRESH_LOCK_TTL)
                .defaultIfEmpty(Boolean.FALSE)
                .flatMap(acquired -> Boolean.TRUE.equals(acquired)
                        ? sessions.resolve(token)
                                .switchIfEmpty(Mono.error(new UnauthorizedException()))
                                .flatMap(current -> isReadyToReturn(current, Instant.now())
                                        ? releaseRefreshLock(token).thenReturn(current)
                                        : refreshAndPersist(token, current))
                        : awaitWinner(token, lockKey));
    }

    private Mono<AuthSessionStore.AuthUser> refreshAndPersist(String token, AuthSessionStore.AuthUser user) {
        Instant now = Instant.now();
        boolean refreshAccessToken = isAccessTokenNearExpiry(user, now);
        Mono<TokenFamily> tokenFamily = refreshAccessToken
                ? oauth2.refresh(user.refreshToken())
                        .switchIfEmpty(Mono.error(new OAuth2Client.OAuth2UnavailableException(
                                "OAuth2 token provider response was empty")))
                        .map(tokens -> {
                            if (tokens.accessToken() == null || tokens.accessToken().isBlank()
                                    || tokens.expiresInSeconds() <= REFRESH_SKEW.toSeconds()) {
                                throw new OAuth2Client.OAuth2UnavailableException(
                                        "OAuth2 token provider response was invalid");
                            }
                            String refreshToken = tokens.refreshToken() == null || tokens.refreshToken().isBlank()
                                    ? user.refreshToken() : tokens.refreshToken();
                            return new TokenFamily(tokens.accessToken(), refreshToken,
                                    Instant.now().plusSeconds(tokens.expiresInSeconds()));
                        })
                : Mono.just(new TokenFamily(user.accessToken(), user.refreshToken(), user.expiresAt()));

        return tokenFamily.flatMap(tokens -> oauth2.fetchIdentity(tokens.accessToken())
                .switchIfEmpty(Mono.error(new OAuth2Client.OAuth2UnavailableException(
                        "OAuth2 userinfo response was empty")))
                .map(identity -> {
                    if (!user.subject().equals(identity.subject())) {
                        throw new OAuth2Client.OAuth2AuthException("userinfo subject changed for current session");
                    }
                    return new AuthSessionStore.AuthUser(identity.subject(), identity.displayName(),
                            tokens.accessToken(), tokens.refreshToken(), tokens.expiresAt(), identity, Instant.now());
                })
                .flatMap(refreshed -> sessions.update(token, refreshed).thenReturn(refreshed))
                .flatMap(refreshed -> isReadyToReturn(refreshed, Instant.now())
                        // Keep the lock until its short TTL expires. Requests that loaded the old
                        // session before this write must observe the lock and re-read this winner,
                        // not refresh again with the rotated token. A later lock winner re-checks
                        // Redis after acquisition, so it can safely release if the session is ready.
                        ? Mono.just(refreshed)
                        : Mono.error(new OAuth2Client.OAuth2UnavailableException(
                                "OAuth2 token provider returned a near-expiry access token"))))
                .onErrorResume(e -> releaseRefreshLock(token).then(Mono.error(e)))
                .doOnError(e -> log.warn("OAuth2 session refresh or identity update failed"))
                .onErrorMap(OAuth2Client.OAuth2AuthException.class, e -> new UnauthorizedException());
    }

    /**
     * A lock loser repeatedly re-reads Redis. It may return only the persisted winner's record,
     * never the snapshot it loaded before the lock race. If the lock disappears without a ready
     * session, fail closed; the loser must not race a second refresh or return stale privileges.
     */
    private Mono<AuthSessionStore.AuthUser> awaitWinner(String token, String lockKey) {
        return Flux.interval(Duration.ZERO, WINNER_POLL_INTERVAL)
                .take(WINNER_POLL_COUNT)
                .concatMap(tick -> sessions.resolve(token)
                        .switchIfEmpty(Mono.error(new UnauthorizedException()))
                        .flatMap(current -> isReadyToReturn(current, Instant.now())
                                ? Mono.just(current)
                                : redis.hasKey(lockKey).flatMap(lockExists -> Boolean.TRUE.equals(lockExists)
                                        ? Mono.<AuthSessionStore.AuthUser>empty()
                                        : Mono.error(new OAuth2Client.OAuth2UnavailableException(
                                                "OAuth2 session refresh did not complete")))))
                .next()
                .switchIfEmpty(Mono.error(new OAuth2Client.OAuth2UnavailableException(
                        "OAuth2 session refresh did not complete")));
    }

    private static boolean isReadyToReturn(AuthSessionStore.AuthUser user, Instant now) {
        return !isAccessTokenNearExpiry(user, now) && isIdentityFresh(user, now);
    }

    private static boolean isAccessTokenNearExpiry(AuthSessionStore.AuthUser user, Instant now) {
        return !user.expiresAt().isAfter(now.plus(REFRESH_SKEW));
    }

    private static boolean isIdentityFresh(AuthSessionStore.AuthUser user, Instant now) {
        Instant fetchedAt = user.identityFetchedAt();
        return fetchedAt != null && !fetchedAt.isAfter(now) && now.isBefore(fetchedAt.plus(IDENTITY_FRESHNESS));
    }

    private record TokenFamily(String accessToken, String refreshToken, Instant expiresAt) {}

    private Mono<Void> releaseRefreshLock(String token) {
        return redis.delete(REFRESH_LOCK_PREFIX + token).then().onErrorResume(e -> Mono.empty());
    }
}
