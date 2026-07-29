package com.jowo.agent.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.SmartLinkClient;
import com.jowo.agent.web.CurrentUser;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
class AuthController {

    private final SmartLinkClient smartLink;
    private final AuthSessionStore sessions;

    AuthController(SmartLinkClient smartLink, AuthSessionStore sessions) {
        this.smartLink = smartLink;
        this.sessions = sessions;
    }

    @PostMapping("/login")
    Mono<ResponseEntity<Map<String, String>>> login(@RequestBody LoginRequest request) {
        return smartLink.login(request.username(), request.password())
                .flatMap(result -> smartLink.fetchDisplayName(result.smartLinkToken())
                        .flatMap(name -> sessions.create(result.userId(), name, result.smartLinkToken())
                                .map(token -> ResponseEntity.ok(
                                        Map.of("token", token, "userId", result.userId(), "name", name)))))
                .onErrorResume(SmartLinkClient.SmartLinkAuthException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()))))
                .onErrorResume(SmartLinkClient.SmartLinkUnavailableException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", "登录服务暂时不可用"))));
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

    record LoginRequest(String username, String password) {}
}
