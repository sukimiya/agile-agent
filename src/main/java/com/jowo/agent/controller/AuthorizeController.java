package com.jowo.agent.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.SmartLinkClient;
import com.jowo.agent.web.CurrentUser;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/authorize")
@CrossOrigin(origins = "*")
class AuthorizeController {

    private final SmartLinkClient smartLink;

    AuthorizeController(SmartLinkClient smartLink) {
        this.smartLink = smartLink;
    }

    @GetMapping("/me")
    Mono<ResponseEntity<Map<String, Object>>> me(@CurrentUser AuthSessionStore.AuthUser user) {
        return smartLink.fetchUserType(user.smartLinkToken())
                .map(userType -> ResponseEntity.ok(Map.<String, Object>of(
                        "result", Map.of("user", Map.of("userType", userType)))))
                .onErrorResume(SmartLinkClient.SmartLinkUnavailableException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of())));
    }
}
