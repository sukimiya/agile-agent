package io.github.sukimiya.agileagent.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.agent.Orchestrator;
import io.github.sukimiya.agileagent.memory.ConversationHistory;
import io.github.sukimiya.agileagent.memory.MemoryException;
import io.github.sukimiya.agileagent.memory.MemoryModel.AcceptedTurn;
import io.github.sukimiya.agileagent.memory.MemoryModel.HistoryPage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnCommand;
import io.github.sukimiya.agileagent.memory.ConversationIds;
import io.github.sukimiya.agileagent.memory.MemoryMaintenance;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.TurnLifecycle;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.SessionService;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.service.TenantLlmConfigRequiredException;
import io.github.sukimiya.agileagent.web.CurrentUser;
import io.github.sukimiya.agileagent.util.NonBlockingIds;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Both chat entry points share one acceptance path: resolve the tenant scope (403), validate the optional
 * {@code Idempotency-Key} (400), check read-only that the conversation is unused or the caller's (404), resolve
 * the tenant model (428, before any write), then durably accept the turn (409/413/503 via
 * {@code MemoryExceptionHandler}). Only after acceptance is the 200 SSE response returned.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*", exposedHeaders = {ChatController.HAS_MORE, ChatController.NEXT_CURSOR})
public class ChatController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String HAS_MORE = "X-Has-More";
    static final String NEXT_CURSOR = "X-Next-Cursor";
    static final int DEFAULT_PAGE = 100;

    private final Orchestrator orchestrator;
    private final SessionService sessionService;
    private final TurnLifecycle lifecycle;
    private final ConversationHistory history;
    private final TenantLlmClientResolver llmResolver;
    private final MemoryMaintenance maintenance;
    private static final ObjectMapper json = new ObjectMapper();

    public ChatController(Orchestrator orchestrator, SessionService sessionService, TurnLifecycle lifecycle,
                          ConversationHistory history, TenantLlmClientResolver llmResolver,
                          MemoryMaintenance maintenance) {
        this.orchestrator = orchestrator;
        this.sessionService = sessionService;
        this.lifecycle = lifecycle;
        this.history = history;
        this.llmResolver = llmResolver;
        this.maintenance = maintenance;
    }

    // OpenAI-compatible chat completions endpoint
    @PostMapping(value = "/chat/completions")
    Mono<ResponseEntity<?>> chatCompletions(@RequestBody Map<String, Object> body,
                                            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
                                            @CurrentUser AuthSessionStore.AuthUser user) {
        var messages = getMessages(body);
        var userMsg = messages.isEmpty() ? "" :
                messages.get(messages.size() - 1).getOrDefault("content", "");
        var conversationId = Optional.ofNullable(body.get("conversationId")).map(Object::toString)
                .orElseGet(NonBlockingIds::uuid);
        var chunkId = "chatcmpl-" + NonBlockingIds.shortUuid();
        return startTurn(user, conversationId, idempotencyKey, userMsg,
                (accepted, llm) -> openAiStream(accepted, user, chunkId, llm));
    }

    private Flux<String> openAiStream(AcceptedTurn accepted, AuthSessionStore.AuthUser user, String chunkId,
                                      LlmClient llm) {
        return orchestrator.chat(accepted, user.accessToken(), user.identity(), llm)
                .flatMap(line -> toOpenAiChunk(line, chunkId).map(Flux::just).orElseGet(Flux::empty))
                .concatWithValues("[DONE]");
    }

    private Mono<ResponseEntity<?>> startTurn(AuthSessionStore.AuthUser user, String conversationId,
                                              String idempotencyKey, String text,
                                              BiFunction<AcceptedTurn, LlmClient, Flux<String>> stream) {
        return Mono.defer(() -> {
            // A client-supplied id is used exactly as given or rejected (400); generated ids are canonical.
            ConversationIds.validate(conversationId);
            var scope = MemoryScope.of(user.identity());
            var requestId = idempotencyKey != null ? idempotencyKey : NonBlockingIds.uuid();
            if (!TurnLifecycle.isValidRequestId(requestId)) {
                return Mono.error(new MemoryException(MemoryException.ErrorCode.INVALID_IDEMPOTENCY_KEY));
            }
            var command = new TurnCommand(scope, conversationId, requestId, text == null ? "" : text);
            return sessionService.verifyAccess(conversationId, scope)
                    .then(Mono.defer(() -> llmResolver.resolve(scope.tenantId())))
                    .flatMap(llm -> lifecycle.accept(command)
                            .<ResponseEntity<?>>map(accepted -> ResponseEntity.ok()
                                    .contentType(MediaType.TEXT_EVENT_STREAM)
                                    .body(stream.apply(accepted, llm))))
                    .onErrorResume(TenantLlmConfigRequiredException.class, e -> Mono.just(configRequired()));
        });
    }

    private static ResponseEntity<Map<String, String>> configRequired() {
        return ResponseEntity.status(428).body(Map.of("code", "LLM_CONFIG_REQUIRED",
                "message", "Current tenant has no LLM configuration"));
    }

    static Optional<String> toContentChunk(String line) {
        if (!line.startsWith("{\"type\":")) return Optional.empty();
        try {
            JsonNode n = json.readTree(line);
            String type = n.path("type").asText();
            if ("delta".equals(type) || "error".equals(type)) {
                return Optional.of(n.path("content").asText(""));
            }
        } catch (Exception ignored) {
            // not a control line we understand — drop it
        }
        return Optional.empty();
    }

    static Optional<String> toOpenAiChunk(String line, String chunkId) {
        try {
            JsonNode n = json.readTree(line);
            if ("usage".equals(n.path("type").asText())) {
                var chunk = json.createObjectNode();
                chunk.put("id", chunkId);
                chunk.put("object", "chat.completion.chunk");
                chunk.putArray("choices");
                var usage = chunk.putObject("usage");
                usage.put("prompt_tokens", n.path("prompt_tokens").longValue());
                usage.put("completion_tokens", n.path("completion_tokens").longValue());
                usage.put("total_tokens", n.path("total_tokens").longValue());
                return Optional.of(json.writeValueAsString(chunk));
            }
        } catch (Exception ignored) { }
        return toContentChunk(line).map(content -> {
            var chunk = json.createObjectNode();
            chunk.put("id", chunkId);
            chunk.put("object", "chat.completion.chunk");
            var choice = chunk.putArray("choices").addObject();
            choice.put("index", 0);
            choice.putObject("delta").put("content", content);
            try {
                return json.writeValueAsString(chunk);
            } catch (Exception e) {
                return "";
            }
        });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> getMessages(Map<String, Object> body) {
        return (List<Map<String, String>>) body.getOrDefault("messages", List.of());
    }

    // GET /api/models for chatbot-ui compatibility
    @GetMapping("/models")
    Map<String, Object> models() {
        return Map.of("object", "list",
                "data", List.of(Map.of("id", "agile-agent", "object", "model")));
    }

    // Legacy endpoint for custom frontend
    @PostMapping(value = "/chat")
    Mono<ResponseEntity<?>> chat(@RequestBody ChatRequest request,
                                 @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
                                 @CurrentUser AuthSessionStore.AuthUser user) {
        var conversationId = request.conversationId() != null
                ? request.conversationId()
                : NonBlockingIds.uuid();
        return startTurn(user, conversationId, idempotencyKey, request.message(),
                (accepted, llm) -> orchestrator.chat(accepted, user.accessToken(), user.identity(), llm));
    }

    @GetMapping("/conversations")
    Flux<ConversationSummary> listConversations(@CurrentUser AuthSessionStore.AuthUser user) {
        return Flux.defer(() -> sessionService.listForUser(MemoryScope.of(user.identity())))
                .map(s -> new ConversationSummary(s.id(), s.title(), s.updatedAt()));
    }

    @GetMapping("/conversations/{id}")
    Object getConversation(@PathVariable String id) {
        return null;
    }

    /**
     * Original messages in ascending sequence: the newest {@code limit} (default 100, 1–200), or those before
     * {@code beforeSequence}. {@code X-Has-More} says whether older messages exist; {@code X-Next-Cursor} is the
     * {@code beforeSequence} for the next older page (absent on the oldest page).
     */
    @GetMapping("/conversations/{id}/messages")
    Mono<ResponseEntity<List<Map<String, String>>>> getConversationMessages(
            @PathVariable String id,
            @RequestParam(name = "beforeSequence", required = false) Long beforeSequence,
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_PAGE) int limit,
            @CurrentUser AuthSessionStore.AuthUser user) {
        return Mono.defer(() -> history.page(MemoryScope.of(user.identity()), id, beforeSequence, limit))
                .map(ChatController::toResponse);
    }

    private static ResponseEntity<List<Map<String, String>>> toResponse(HistoryPage page) {
        List<Map<String, String>> displayMessages = new ArrayList<>();
        for (var m : page.items()) {
            var payload = m.payload();
            boolean displayable = ("user".equals(payload.role()) || "assistant".equals(payload.role()))
                    && payload.content() != null;
            if (displayable) {
                displayMessages.add(Map.of("id", m.id(), "role", payload.role(), "content", payload.content()));
            }
        }
        var response = ResponseEntity.ok().header(HAS_MORE, Boolean.toString(page.hasMore()));
        if (page.nextBeforeSequence() != null) {
            response = response.header(NEXT_CURSOR, page.nextBeforeSequence().toString());
        }
        return response.body(displayMessages);
    }

    /**
     * Deletes one of the caller's conversations: 204 (also when already deleted), 400 for a non-canonical id,
     * 403 without tenant, 404 for an unknown or foreign one. Content is hidden at once; physical cleanup follows.
     */
    @DeleteMapping("/conversations/{id}")
    Mono<ResponseEntity<Void>> deleteConversation(@PathVariable String id, @CurrentUser AuthSessionStore.AuthUser user) {
        return Mono.defer(() -> {
                    ConversationIds.validate(id);
                    return maintenance.deleteConversation(MemoryScope.of(user.identity()), id);
                })
                .then(Mono.fromSupplier(() -> ResponseEntity.noContent().<Void>build()));
    }

    record ChatRequest(String conversationId, String message) {}

    record ConversationSummary(String id, String title, LocalDateTime updatedAt) {}

}
