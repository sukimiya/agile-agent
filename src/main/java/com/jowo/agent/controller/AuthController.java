package com.jowo.agent.controller;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    Mono<ResponseEntity<Void>> authorize() {
        String codeVerifier = Pkce.newCodeVerifier();
        String codeChallenge = Pkce.challenge(codeVerifier);
        return states.save(codeVerifier)
                .map(state -> ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(oauth2.buildAuthorizationUrl(state, codeChallenge)))
                        .build());
    }

    @GetMapping("/oauth2/callback")
    Mono<ResponseEntity<Void>> callback(@RequestParam("code") String code, @RequestParam("state") String state) {
        return states.consume(state)
                .switchIfEmpty(Mono.error(new OAuth2Client.OAuth2AuthException("unknown or expired state")))
                .flatMap(codeVerifier -> oauth2.exchangeCode(code, codeVerifier))
                .flatMap(tokens -> oauth2.fetchUserInfo(tokens.accessToken())
                        .flatMap(info -> sessions.create(info.subject(), info.name(),
                                tokens.accessToken(), tokens.refreshToken(),
                                Instant.now().plusSeconds(tokens.expiresInSeconds()), info.userType())))
                .map(this::redirectWithToken)
                .onErrorResume(OAuth2Client.OAuth2AuthException.class, e -> Mono.just(redirectWithError("auth_failed")))
                .onErrorResume(OAuth2Client.OAuth2UnavailableException.class, e -> Mono.just(redirectWithError("provider_unavailable")));
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
        return sessions.delete(token).thenReturn(ResponseEntity.noContent().build());
    }
}
