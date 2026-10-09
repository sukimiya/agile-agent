package com.jowo.agent.controller;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jowo.agent.config.OAuth2Properties;
import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.OAuth2Client;
import com.jowo.agent.service.OAuth2StateStore;
import com.jowo.agent.service.Pkce;
import com.jowo.agent.web.CurrentUser;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** Binds the OAuth2 {@code state} to the browser that started the flow (login-CSRF defense). */
    private static final String STATE_BINDING_COOKIE = "oauth2_state_binding";
    private static final Duration STATE_BINDING_TTL = Duration.ofMinutes(10);

    private final OAuth2Client oauth2;
    private final OAuth2StateStore states;
    private final AuthSessionStore sessions;
    private final OAuth2Properties props;

    AuthController(OAuth2Client oauth2, OAuth2StateStore states, AuthSessionStore sessions, OAuth2Properties props) {
        this.oauth2 = oauth2;
        this.states = states;
        this.sessions = sessions;
        this.props = props;
    }

    @GetMapping("/oauth2/authorize")
    Mono<ResponseEntity<Void>> authorize(ServerHttpResponse response) {
        String codeVerifier = Pkce.newCodeVerifier();
        String codeChallenge = Pkce.challenge(codeVerifier);
        return states.save(codeVerifier)
                .map(state -> {
                    // Binds this state to the browser that started the flow; checked again in callback().
                    response.addCookie(stateBindingCookie(state));
                    return ResponseEntity.status(HttpStatus.FOUND)
                            .location(URI.create(oauth2.buildAuthorizationUrl(state, codeChallenge)))
                            .<Void>build();
                });
    }

    // code/state/error are all optional: on "Deny" (or login_required/consent_required) the provider
    // redirects here with ?error=... and no code, and required-param binding would fail the request
    // with a raw 400 before any of the designed #error= redirect handling could run.
    @GetMapping("/oauth2/callback")
    Mono<ResponseEntity<Void>> callback(@RequestParam(required = false) String code,
                                        @RequestParam(required = false) String state,
                                        @RequestParam(required = false) String error,
                                        ServerHttpRequest request) {
        if (error != null) {
            log.warn("OAuth2 callback: provider returned error={}", error);
            return Mono.just(redirectWithError("auth_failed"));
        }
        if (code == null || state == null) {
            log.warn("OAuth2 callback: missing code or state parameter");
            return Mono.just(redirectWithError("auth_failed"));
        }
        // The state must have been issued to THIS browser, otherwise an attacker can lure a victim
        // into completing the attacker's own authorization flow and end up logged in as the attacker.
        var binding = request.getCookies().getFirst(STATE_BINDING_COOKIE);
        if (binding == null || !binding.getValue().equals(state)) {
            log.warn("OAuth2 callback: state binding cookie {}", binding == null ? "missing" : "mismatched");
            return Mono.just(redirectWithError("auth_failed"));
        }
        return states.consume(state)
                .switchIfEmpty(Mono.error(new OAuth2Client.OAuth2AuthException("unknown or expired state")))
                .flatMap(codeVerifier -> oauth2.exchangeCode(code, codeVerifier))
                .flatMap(tokens -> oauth2.fetchUserInfo(tokens.accessToken())
                        .flatMap(info -> sessions.create(info.subject(), info.name(),
                                tokens.accessToken(), tokens.refreshToken(),
                                Instant.now().plusSeconds(tokens.expiresInSeconds()), info.userType())))
                .map(this::redirectWithToken)
                .onErrorResume(OAuth2Client.OAuth2AuthException.class, e -> {
                    log.warn("OAuth2 callback failed during state/code exchange or userinfo lookup", e);
                    return Mono.just(redirectWithError("auth_failed"));
                })
                .onErrorResume(OAuth2Client.OAuth2UnavailableException.class, e -> {
                    log.warn("OAuth2 callback failed: identity provider unavailable", e);
                    return Mono.just(redirectWithError("provider_unavailable"));
                });
    }

    private static ResponseCookie stateBindingCookie(String state) {
        return ResponseCookie.from(STATE_BINDING_COOKIE, state)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(STATE_BINDING_TTL)
                .build();
    }

    private ResponseEntity<Void> redirectWithToken(String appToken) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(props.frontendRedirectUri() + "#token=" + appToken))
                .build();
    }

    private ResponseEntity<Void> redirectWithError(String errorCode) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(props.frontendRedirectUri() + "#error=" + errorCode))
                .build();
    }

    @GetMapping("/me")
    Map<String, String> me(@CurrentUser AuthSessionStore.AuthUser user) {
        return Map.of("userId", user.userId(), "name", user.name());
    }

    @PostMapping("/logout")
    Mono<ResponseEntity<Void>> logout(@RequestHeader("Authorization") String authorization) {
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        // Best-effort revocation at the provider: the refresh token outlives the app session by weeks,
        // but a provider that can't revoke must not stop the local session from being destroyed.
        return sessions.resolve(token)
                .flatMap(user -> oauth2.revoke(user.refreshToken()))
                .onErrorResume(e -> {
                    log.warn("Logout: token revocation at identity provider failed, continuing", e);
                    return Mono.empty();
                })
                .then(sessions.delete(token))
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }
}
