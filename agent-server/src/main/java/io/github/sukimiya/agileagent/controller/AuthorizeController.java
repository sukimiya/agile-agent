package io.github.sukimiya.agileagent.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.web.CurrentUser;

@RestController
@RequestMapping("/api/authorize")
@CrossOrigin(origins = "*")
class AuthorizeController {

    @GetMapping("/me")
    Map<String, Object> me(@CurrentUser AuthSessionStore.AuthUser user) {
        return Map.of("result", Map.of("user", Map.of("userType",
                user.userType() == null ? "" : user.userType())));
    }
}
