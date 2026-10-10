package io.github.sukimiya.agileagent.controller;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
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

import io.github.sukimiya.agileagent.config.OAuth2Properties;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.OAuth2Client;
import io.github.sukimiya.agileagent.service.OAuth2StateStore;
import io.github.sukimiya.agileagent.service.Pkce;
import io.github.sukimiya.agileagent.web.CurrentUser;

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
            log.warn("OAuth2 callback: provider returned an authorization error");
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
                .flatMap(tokens -> oauth2.fetchIdentity(tokens.accessToken())
                        .flatMap(identity -> sessions.create(tokens.accessToken(), tokens.refreshToken(),
                                Instant.now().plusSeconds(tokens.expiresInSeconds()), identity, Instant.now())))
                .map(this::redirectWithToken)
                .onErrorResume(OAuth2Client.OAuth2AuthException.class, e -> {
                    log.warn("OAuth2 callback failed during state/code exchange or userinfo lookup");
                    return Mono.just(redirectWithError("auth_failed"));
                })
                .onErrorResume(OAuth2Client.OAuth2UnavailableException.class, e -> {
                    log.warn("OAuth2 callback failed: identity provider unavailable");
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
    Map<String, Object> me(@CurrentUser AuthSessionStore.AuthUser user) {
        var identity = user.identity();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", user.subject());
        response.put("name", user.displayName());
        response.put("subject", identity.subject());
        response.put("displayName", identity.displayName());
        response.put("tenantId", identity.tenantId());
        response.put("tenantName", identity.tenantName());
        response.put("primaryOrgId", identity.primaryOrgId());
        response.put("primaryOrgName", identity.primaryOrgName());
        response.put("roles", identity.roles());
        response.put("manageableOrgIds", identity.manageableOrgIds());
        response.put("readableOrgIds", identity.readableOrgIds());
        response.put("platformAdmin", identity.platformAdmin());
        response.put("tenantAdmin", identity.tenantAdmin());
        return response;
    }

    @PostMapping("/logout")
    Mono<ResponseEntity<Void>> logout(@RequestHeader("Authorization") String authorization) {
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        // Tombstone locally before best-effort provider revocation. This prevents an in-flight
        // resolver refresh from recreating the app session after logout returns.
        return sessions.resolve(token)
                .flatMap(user -> sessions.logout(token).thenReturn(user))
                .switchIfEmpty(Mono.defer(() -> sessions.logout(token).then(Mono.<AuthSessionStore.AuthUser>empty())))
                .flatMap(user -> oauth2.revoke(user.refreshToken())
                        .onErrorResume(e -> {
                            log.warn("Logout: token revocation at identity provider failed, continuing");
                            return Mono.empty();
                        }))
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }
}
