package io.github.sukimiya.agileagent.controller;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.sukimiya.agileagent.memory.ConversationIds;
import io.github.sukimiya.agileagent.memory.MemoryException;
import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.items.ItemStatus;
import io.github.sukimiya.agileagent.memory.items.MemoryItem;
import io.github.sukimiya.agileagent.memory.items.MemoryItemService;
import io.github.sukimiya.agileagent.memory.judgement.ValidatedAction.SourceRef;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService.MemorySettings;
import io.github.sukimiya.agileagent.memory.recall.RecallEventRecorder;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.SessionService;
import io.github.sukimiya.agileagent.web.CurrentUser;
import reactor.core.publisher.Mono;

/**
 * The caller's own memory (D1=A, D15=A). Authenticated; a tenant is required ({@code 403 TENANT_REQUIRED}); the
 * scope is always the caller's {@code (tenantId, subject)}, never taken from the request.
 * <ul>
 * <li>{@code GET/PUT /api/memory/settings}: {@code {"crossConversationEnabled": true|false}}.</li>
 * <li>{@code GET /api/memory/items?status=&limit=&before=}, {@code GET /api/memory/items/{id}} (with sources and
 * evidence), {@code POST /api/memory/items/{id}/corrections} ({@code {"content", "expectedRevision"?}} → 201 new
 * item), {@code DELETE /api/memory/items/{id}} (204), {@code DELETE /api/memory/profile} (204, with the graph).</li>
 * <li>{@code GET /api/conversations/{id}/turns/{turnId}/memory}: what the turn's answer prompt took from
 * memory.</li>
 * </ul>
 * Invalid input is {@code 400 INVALID_ARGUMENT}; another owner's item {@code 404 MEMORY_ITEM_NOT_FOUND}; another
 * owner's or a deleted conversation {@code 404 CONVERSATION_NOT_FOUND}; database failures are
 * {@code 503 STORAGE_UNAVAILABLE}.
 */
@RestController
public class MemoryController {

    /** An item as shown to its owner (no scope). */
    public record ItemView(String id, String conversationId, String itemType, String content, ItemStatus status,
            MemoryItem.Assertion assertion, Double importance, Double confidence, LocalDateTime validUntil,
            String supersededBy, long revision, LocalDateTime createdAt, LocalDateTime updatedAt) {

        static ItemView of(MemoryItem item) {
            return new ItemView(item.id(), item.conversationId(), item.itemType(), item.content(), item.status(),
                    item.assertion(), item.importance(), item.confidence(), item.validUntil(), item.supersededBy(),
                    item.revision(), item.createdAt(), item.updatedAt());
        }
    }

    public record ItemPageView(List<ItemView> items, String nextBefore) {}

    public record ItemDetailView(ItemView item, List<SourceRef> sources) {}

    public record EventView(String kind, String refId, String reason, Double score, int rank,
            List<String> sourceTypes) {}

    public record TurnMemoryView(String conversationId, String turnId, List<EventView> events) {}

    /** {@code retention} as ISO-8601 (e.g. {@code PT168H}); {@code purgeEligibleAt} null when not a candidate. */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record RecallCountView(String itemId, long count, String retention, LocalDateTime purgeEligibleAt) {

        static RecallCountView of(MemoryItemService.RecallCount c) {
            return new RecallCountView(c.itemId(), c.count(), c.retention().toString(), c.purgeEligibleAt());
        }
    }

    private final MemorySettingsService settings;
    private final MemoryItemService items;
    private final RecallEventRecorder recallEvents;
    private final SessionService sessions;

    public MemoryController(MemorySettingsService settings, MemoryItemService items,
            RecallEventRecorder recallEvents, SessionService sessions) {
        this.settings = settings;
        this.items = items;
        this.recallEvents = recallEvents;
        this.sessions = sessions;
    }

    @GetMapping("/api/memory/settings")
    Mono<MemorySettings> get(@CurrentUser AuthSessionStore.AuthUser user) {
        return Mono.defer(() -> settings.get(MemoryScope.of(user.identity()))).onErrorMap(MemoryController::storage);
    }

    @PutMapping("/api/memory/settings")
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

    @GetMapping("/api/memory/items")
    Mono<ItemPageView> list(@CurrentUser AuthSessionStore.AuthUser user,
            @RequestParam(required = false) String status, @RequestParam(required = false) String limit,
            @RequestParam(required = false) String before) {
        return Mono.defer(() -> {
            var scope = MemoryScope.of(user.identity());
            return items.list(scope, parseStatus(status), parseLimit(limit), before)
                    .map(p -> new ItemPageView(p.items().stream().map(ItemView::of).toList(), p.nextBefore()));
        }).onErrorMap(MemoryController::storage);
    }

    @GetMapping("/api/memory/items/{id}")
    Mono<ItemDetailView> detail(@CurrentUser AuthSessionStore.AuthUser user, @PathVariable String id) {
        return Mono.defer(() -> items.detail(MemoryScope.of(user.identity()), id))
                .map(d -> new ItemDetailView(ItemView.of(d.item()), d.sources()))
                .onErrorMap(MemoryController::storage);
    }

    @PostMapping("/api/memory/items/{id}/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    Mono<ItemView> correct(@CurrentUser AuthSessionStore.AuthUser user, @PathVariable String id,
            @RequestBody(required = false) JsonNode body) {
        return Mono.defer(() -> {
            var scope = MemoryScope.of(user.identity());
            var content = body == null ? null : body.get("content");
            var revision = body == null ? null : body.get("expectedRevision");
            if (content == null || !content.isTextual() || content.asText().isBlank()
                    || (revision != null && !revision.isNull() && !revision.canConvertToLong())
                    || (revision != null && !revision.isNull() && !revision.isIntegralNumber())) {
                return Mono.error(new MemoryException(ErrorCode.INVALID_ARGUMENT));
            }
            Long expected = revision == null || revision.isNull() ? null : revision.longValue();
            return items.correct(scope, id, content.asText(), expected).map(ItemView::of);
        }).onErrorMap(MemoryController::storage);
    }

    /**
     * Manually lowers the item's recall reference count (body optional: {@code {"amount": n}}, n ≥ 1, default 1);
     * floored at 0, audited with the caller as actor.
     */
    @PostMapping("/api/memory/items/{id}/recall-count/decrement")
    Mono<RecallCountView> decrementRecallCount(@CurrentUser AuthSessionStore.AuthUser user, @PathVariable String id,
            @RequestBody(required = false) JsonNode body) {
        return Mono.defer(() -> {
            var scope = MemoryScope.of(user.identity());
            var amount = body == null ? null : body.get("amount");
            long n = 1;
            if (amount != null && !amount.isNull()) {
                if (!amount.isIntegralNumber() || !amount.canConvertToLong() || amount.longValue() < 1) {
                    return Mono.error(new MemoryException(ErrorCode.INVALID_ARGUMENT));
                }
                n = amount.longValue();
            }
            return items.decrementRecallCount(scope, id, n, scope.subject()).map(RecallCountView::of);
        }).onErrorMap(MemoryController::storage);
    }

    @DeleteMapping("/api/memory/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@CurrentUser AuthSessionStore.AuthUser user, @PathVariable String id) {
        return Mono.defer(() -> items.delete(MemoryScope.of(user.identity()), id))
                .onErrorMap(MemoryController::storage);
    }

    @DeleteMapping("/api/memory/profile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> deleteProfile(@CurrentUser AuthSessionStore.AuthUser user) {
        return Mono.defer(() -> items.deleteProfile(MemoryScope.of(user.identity())))
                .onErrorMap(MemoryController::storage);
    }

    @GetMapping("/api/conversations/{id}/turns/{turnId}/memory")
    Mono<TurnMemoryView> explain(@CurrentUser AuthSessionStore.AuthUser user, @PathVariable String id,
            @PathVariable String turnId) {
        return Mono.defer(() -> {
            var scope = MemoryScope.of(user.identity());
            var conversationId = ConversationIds.validate(id);
            if (turnId == null || turnId.isBlank() || turnId.length() > 64) {
                return Mono.error(new MemoryException(ErrorCode.INVALID_ARGUMENT));
            }
            return sessions.findOwned(conversationId, scope)
                    .switchIfEmpty(Mono.error(new MemoryException(ErrorCode.CONVERSATION_NOT_FOUND)))
                    .flatMap(row -> recallEvents.forTurn(scope, conversationId, turnId)
                            .map(e -> new EventView(e.kind(), e.refId(), e.reason(), e.score(), e.rank(),
                                    splitSources(e.sourceTypes())))
                            .collectList())
                    .map(events -> new TurnMemoryView(conversationId, turnId, events));
        }).onErrorMap(MemoryController::storage);
    }

    private static List<String> splitSources(String joined) {
        return joined == null || joined.isBlank() ? List.of()
                : Arrays.stream(joined.split(",")).filter(s -> !s.isBlank()).toList();
    }

    private static ItemStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ItemStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new MemoryException(ErrorCode.INVALID_ARGUMENT);
        }
    }

    private static int parseLimit(String limit) {
        if (limit == null || limit.isBlank()) {
            return MemoryItemService.DEFAULT_LIMIT;
        }
        try {
            int n = Integer.parseInt(limit);
            if (n < 1 || n > MemoryItemService.MAX_LIMIT) {
                throw new MemoryException(ErrorCode.INVALID_ARGUMENT);
            }
            return n;
        } catch (NumberFormatException e) {
            throw new MemoryException(ErrorCode.INVALID_ARGUMENT);
        }
    }

    /** Database failures are {@code 503 STORAGE_UNAVAILABLE}, never the underlying message. */
    private static Throwable storage(Throwable e) {
        return e instanceof MemoryException ? e : new MemoryException(ErrorCode.STORAGE_UNAVAILABLE, e);
    }
}
