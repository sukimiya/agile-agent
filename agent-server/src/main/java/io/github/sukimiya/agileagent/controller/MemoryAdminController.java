package io.github.sukimiya.agileagent.controller;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.sukimiya.agileagent.auth.AuthorizationService;
import io.github.sukimiya.agileagent.memory.MemoryException;
import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.judgement.RulesReplayHandler;
import io.github.sukimiya.agileagent.memory.judgement.RulesReplayHandler.Replay;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.web.CurrentUser;
import reactor.core.publisher.Mono;

/**
 * Memory administration (M2b Task 7, user decisions 2026-10-28). Platform admins only ({@code 403 FORBIDDEN} for
 * everyone else, tenant admins included); they may target any tenant.
 * <ul>
 * <li>{@code POST /api/admin/memory/replays} {@code {"tenantId", "fromRulesVersion"?}} → {@code 202} with the
 * replay: re-judges that tenant's old-Rules INFERRED items under the current Rules, one by one, in a resumable
 * job. There is no dry run.</li>
 * <li>{@code GET /api/admin/memory/replays/{id}}: progress ({@code 404 MEMORY_REPLAY_NOT_FOUND}).</li>
 * </ul>
 */
@RestController
public class MemoryAdminController {

    public record ReplayView(String replayId, String tenantId, Integer fromRulesVersion, int toRulesVersion,
            String status, String requestedBy, long processed, long superseded, long inactivated, long skipped,
            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime finishedAt) {

        static ReplayView of(Replay r) {
            return new ReplayView(r.id(), r.tenantId(), r.fromRulesVersion(), r.toRulesVersion(), r.status(),
                    r.requestedBy(), r.processed(), r.superseded(), r.inactivated(), r.skipped(), r.createdAt(),
                    r.updatedAt(), r.finishedAt());
        }
    }

    private final RulesReplayHandler replays;
    private final AuthorizationService authorization;

    public MemoryAdminController(RulesReplayHandler replays, AuthorizationService authorization) {
        this.replays = replays;
        this.authorization = authorization;
    }

    @PostMapping("/api/admin/memory/replays")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Mono<ReplayView> start(@CurrentUser AuthSessionStore.AuthUser user,
            @RequestBody(required = false) JsonNode body) {
        return Mono.defer(() -> {
            authorization.requirePlatformAdmin(user.identity());
            var tenant = body == null ? null : body.get("tenantId");
            if (tenant == null || !tenant.isTextual() || tenant.asText().isBlank()
                    || tenant.asText().length() > MemoryScope.MAX_TENANT_LENGTH) {
                return Mono.error(new MemoryException(ErrorCode.INVALID_ARGUMENT));
            }
            var from = body.get("fromRulesVersion");
            Integer fromVersion = null;
            if (from != null && !from.isNull()) {
                if (!from.isInt() || from.asInt() < 1) {
                    return Mono.error(new MemoryException(ErrorCode.INVALID_ARGUMENT));
                }
                fromVersion = from.asInt();
            }
            return replays.request(tenant.asText(), fromVersion, user.identity().subject()).map(ReplayView::of);
        });
    }

    @GetMapping("/api/admin/memory/replays/{id}")
    Mono<ReplayView> progress(@CurrentUser AuthSessionStore.AuthUser user, @PathVariable String id) {
        return Mono.defer(() -> {
            authorization.requirePlatformAdmin(user.identity());
            return replays.find(id).flatMap(found -> found.isPresent() ? Mono.just(ReplayView.of(found.get()))
                    : Mono.error(new MemoryException(ErrorCode.MEMORY_REPLAY_NOT_FOUND)));
        });
    }
}
