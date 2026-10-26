package io.github.sukimiya.agileagent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.sukimiya.agileagent.memory.MemoryException;
import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService.MemorySettings;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.web.CurrentUser;
import reactor.core.publisher.Mono;

/**
 * The caller's memory switches (D1=A): {@code GET/PUT /api/memory/settings}. Authenticated, a tenant is required
 * ({@code 403 TENANT_REQUIRED}); the scope is always the caller's own {@code (tenantId, subject)}, never taken from
 * the request. Body of PUT: {@code {"crossConversationEnabled": true|false}}; anything else is
 * {@code 400 INVALID_ARGUMENT}; database failures are {@code 503 STORAGE_UNAVAILABLE}. A change applies to the next question (settings are not cached).
 */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemorySettingsService settings;

    public MemoryController(MemorySettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/settings")
    Mono<MemorySettings> get(@CurrentUser AuthSessionStore.AuthUser user) {
        return Mono.defer(() -> settings.get(MemoryScope.of(user.identity()))).onErrorMap(MemoryController::storage);
    }

    @PutMapping("/settings")
    Mono<MemorySettings> put(@CurrentUser AuthSessionStore.AuthUser user, @RequestBody(required = false) JsonNode body) {
        return Mono.defer(() -> {
            var scope = MemoryScope.of(user.identity());
            var value = body == null ? null : body.get("crossConversationEnabled");
            if (value == null || !value.isBoolean()) {
                return Mono.error(new MemoryException(ErrorCode.INVALID_ARGUMENT));
            }
            return settings.update(scope, value.booleanValue());
        }).onErrorMap(MemoryController::storage);
    }

    /** Database failures are {@code 503 STORAGE_UNAVAILABLE}, never the underlying message. */
    private static Throwable storage(Throwable e) {
        return e instanceof MemoryException ? e : new MemoryException(ErrorCode.STORAGE_UNAVAILABLE, e);
    }
}
