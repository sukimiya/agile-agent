package io.github.sukimiya.agileagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;

import io.github.sukimiya.agileagent.agent.Orchestrator;
import io.github.sukimiya.agileagent.agent.TaskDispatcher;
import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.controller.ChatController;
import io.github.sukimiya.agileagent.service.AuthSessionStore.AuthUser;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver.ResolvedModel;
import io.github.sukimiya.agileagent.service.TenantLlmConfigRequiredException;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.AuthExceptionHandler;
import io.github.sukimiya.agileagent.web.CurrentUser;
import io.github.sukimiya.agileagent.web.MemoryExceptionHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Both chat entry points end to end over the real ChatController, Orchestrator and memory stack (H2 + Redis
 * cache): acceptance before the 200 SSE response, replay, 409 with turnId, disconnect-as-cancel, failure events,
 * and history paging.
 */
class TurnStreamingIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final String ROUTING_MARKER = "路由模块";

    private MemoryTestStack stack;
    private TenantLlmClientResolver resolver;
    private WebTestClient client;
    private IdentityContext identity = identity(TurnTestSupport.ALICE);
    private Function<List<LlmClient.Message>, Flux<LlmClient.LlmEvent>> answer;
    private final AtomicInteger answerCalls = new AtomicInteger();
    private final LlmClient llm = (messages, tools) -> {
        if (messages.getFirst().content().contains(ROUTING_MARKER)) {
            return Flux.just(new LlmClient.LlmEvent.Completed("[]"));
        }
        answerCalls.incrementAndGet();
        return answer.apply(messages);
    };

    protected MemoryTestStack newStack() {
        return MemoryTestStack.h2();
    }

    @BeforeEach
    void setUp() {
        stack = newStack();
        resolver = mock(TenantLlmClientResolver.class);
        when(resolver.resolveModel("tenant-1")).thenReturn(Mono.just(new ResolvedModel(llm, null, null)));
        answer = messages -> Flux.just(new LlmClient.LlmEvent.ContentDelta("你好"),
                new LlmClient.LlmEvent.Completed("你好"), new LlmClient.LlmEvent.Usage(2, 3, 5));
        var orchestrator = new Orchestrator(mock(TaskDispatcher.class), new CapabilityRegistry(List.of()),
                stack.lifecycle(), stack.history());
        var controller = new ChatController(orchestrator, stack.sessionService(), stack.lifecycle(), stack.history(),
                resolver, mock(MemoryMaintenance.class));
        client = WebTestClient.bindToController(controller)
                .controllerAdvice(new MemoryExceptionHandler(), new AuthExceptionHandler())
                .argumentResolvers(c -> c.addCustomResolver(new FixedUser()))
                .configureClient().responseTimeout(WAIT)
                .build();
    }

    @AfterEach
    void tearDown() {
        stack.close();
    }

    private static IdentityContext identity(MemoryScope scope) {
        return new IdentityContext(scope.subject(), "Alice", scope.tenantId(), "Tenant", null, null,
                Set.of(), Set.of(), Set.of(), false, false);
    }

    private final class FixedUser implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class);
        }

        @Override
        public Mono<Object> resolveArgument(MethodParameter parameter, BindingContext context, ServerWebExchange exchange) {
            return Mono.just(new AuthUser(identity.subject(), "Alice", "access-token", "refresh",
                    Instant.now().plusSeconds(3600), identity, Instant.now()));
        }
    }

    private WebTestClient.RequestHeadersSpec<?> legacy(String conversationId, String key, String text) {
        var body = conversationId == null ? "{\"message\":\"" + text + "\"}"
                : "{\"conversationId\":\"" + conversationId + "\",\"message\":\"" + text + "\"}";
        var spec = client.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            spec = spec.header("Idempotency-Key", key);
        }
        return spec.bodyValue(body);
    }

    private WebTestClient.RequestHeadersSpec<?> completions(String conversationId, String key, String text) {
        var body = "{\"messages\":[{\"role\":\"user\",\"content\":\"" + text + "\"}]"
                + (conversationId == null ? "" : ",\"conversationId\":\"" + conversationId + "\"") + "}";
        var spec = client.post().uri("/api/chat/completions").contentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            spec = spec.header("Idempotency-Key", key);
        }
        return spec.bodyValue(body);
    }

    private static List<String> stream(WebTestClient.RequestHeadersSpec<?> request) {
        return request.exchange().expectStatus().isOk()
                .returnResult(String.class).getResponseBody().collectList().block(WAIT);
    }

    @Test
    void userPersistedBeforeSseAccepted() {
        var conversation = MemoryTestStack.newConversationId();
        answer = messages -> Flux.never();

        var result = legacy(conversation, "k1", "问题").exchange().expectStatus().isOk()
                .returnResult(String.class);

        // The 200 response exists before any body was consumed: the user message and the turn are durable.
        assertThat(stack.messageCount(conversation, "user")).isEqualTo(1);
        assertThat(stack.turnCount(conversation)).isEqualTo(1);
        result.getResponseBody().take(1).blockLast(WAIT);
    }

    @Test
    void missingModelReturnsExact428WithoutTurn() {
        var conversation = MemoryTestStack.newConversationId();
        when(resolver.resolveModel("tenant-1")).thenReturn(Mono.error(new TenantLlmConfigRequiredException()));

        legacy(conversation, null, "hi").exchange().expectStatus().isEqualTo(428)
                .expectBody().json("{\"code\":\"LLM_CONFIG_REQUIRED\",\"message\":\"Current tenant has no LLM configuration\"}", true);
        completions(conversation, null, "hi").exchange().expectStatus().isEqualTo(428)
                .expectBody().json("{\"code\":\"LLM_CONFIG_REQUIRED\",\"message\":\"Current tenant has no LLM configuration\"}", true);

        assertThat(stack.turnCount(conversation)).isZero();
        assertThat(stack.sessionService().findOwned(conversation, TurnTestSupport.ALICE).blockOptional()).isEmpty();
    }

    @Test
    void bothEndpointsReplayCompletedTurn() {
        var conversation = MemoryTestStack.newConversationId();
        var first = stream(legacy(conversation, "same-key", "问题"));
        assertThat(first).anySatisfy(l -> assertThat(l).contains("\"type\":\"done\""));

        var legacyReplay = stream(legacy(conversation, "same-key", "问题"));
        // The endpoint is part of the fingerprint: the same key on the other endpoint is a different request.
        expectError(completions(conversation, "same-key", "问题"), 409, "IDEMPOTENCY_CONFLICT");
        stream(completions(conversation, "openai-key", "问题"));
        var openAiReplay = stream(completions(conversation, "openai-key", "问题"));

        assertThat(answerCalls).hasValue(2);
        assertThat(stack.turnCount(conversation)).isEqualTo(2);
        assertThat(stack.messageCount(conversation, "user")).isEqualTo(2);
        assertThat(legacyReplay).containsExactly(
                "{\"type\":\"delta\",\"content\":\"你好\"}",
                "{\"type\":\"usage\",\"prompt_tokens\":2,\"completion_tokens\":3,\"total_tokens\":5}",
                "{\"type\":\"done\"}");
        assertThat(openAiReplay).anySatisfy(l -> assertThat(l).contains("\"content\":\"你好\""));
        assertThat(openAiReplay).anySatisfy(l -> assertThat(l).contains("\"total_tokens\":5"));
        assertThat(openAiReplay.getLast()).isEqualTo("[DONE]");
    }

    @Test
    void activeTurnReturns409WithTurnId() {
        var conversation = MemoryTestStack.newConversationId();
        answer = messages -> Flux.never();
        var running = legacy(conversation, "first", "一").exchange().expectStatus().isOk().returnResult(String.class);
        var activeTurn = stack.activeTurn(conversation);
        assertThat(activeTurn).isNotNull();

        legacy(conversation, "second", "二").exchange().expectStatus().isEqualTo(409)
                .expectBody().json("{\"code\":\"TURN_IN_PROGRESS\",\"turnId\":\"" + activeTurn
                        + "\",\"conversationId\":\"" + conversation + "\"}");
        legacy(conversation, "first", "一").exchange().expectStatus().isEqualTo(409)
                .expectBody().json("{\"code\":\"TURN_IN_PROGRESS\",\"turnId\":\"" + activeTurn
                        + "\",\"conversationId\":\"" + conversation + "\"}");
        completions(conversation, "other", "一").exchange().expectStatus().isEqualTo(409)
                .expectBody().json("{\"code\":\"TURN_IN_PROGRESS\",\"turnId\":\"" + activeTurn
                        + "\",\"conversationId\":\"" + conversation + "\"}");
        legacy(conversation, "first", "不同的文本").exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("IDEMPOTENCY_CONFLICT");
        running.getResponseBody().take(1).blockLast(WAIT);
    }

    @Test
    void foreignConversationAndBadKeyAreRejectedBeforeAnyWrite() {
        var conversation = MemoryTestStack.newConversationId();
        stack.insertForeignSession(conversation, TurnTestSupport.ALICE_OTHER_TENANT);

        legacy(conversation, null, "hi").exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("CONVERSATION_NOT_FOUND");
        completions(MemoryTestStack.newConversationId(), "bad key!", "hi").exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_IDEMPOTENCY_KEY");

        assertThat(stack.turnCount(conversation)).isZero();
        verify(resolver, never()).resolveModel(any());
    }

    @Test
    void clientDisconnectStopsModelAndMarksCancelled() {
        var conversation = MemoryTestStack.newConversationId();
        var cancelled = new AtomicBoolean();
        answer = messages -> Flux.interval(Duration.ofMillis(20))
                .map(i -> (LlmClient.LlmEvent) new LlmClient.LlmEvent.ContentDelta("片段" + i))
                .doOnCancel(() -> cancelled.set(true));

        var received = legacy(conversation, "k", "长回答").exchange().expectStatus().isOk()
                .returnResult(String.class).getResponseBody()
                .takeUntil(line -> line.contains("片段2"))
                .collectList().block(WAIT);

        assertThat(received).noneSatisfy(l -> assertThat(l).contains("\"type\":\"done\""));
        await().atMost(WAIT).untilAsserted(() -> assertThat(cancelled).isTrue());
        var turnId = stack.turnIds(conversation).getFirst();
        await().atMost(WAIT).untilAsserted(() -> assertThat(stack.status(turnId)).isEqualTo("CANCELLED"));
        assertThat(stack.activeTurn(conversation)).isNull();
        assertThat(answerCalls).hasValue(1);
    }

    @Test
    void timeoutAndDatabaseFailureNeverEmitSuccessfulDone() {
        var timedOut = MemoryTestStack.newConversationId();
        answer = messages -> Flux.error(new TimeoutException("model timed out"));

        var lines = stream(legacy(timedOut, null, "q"));
        var openAi = stream(completions(MemoryTestStack.newConversationId(), null, "q"));

        assertThat(lines).containsSubsequence(
                "{\"type\":\"error\",\"content\":\"处理出错\",\"code\":\"TURN_FAILED\"}", "{\"type\":\"done\"}");
        assertThat(lines).noneSatisfy(l -> assertThat(l).contains("timed out"));
        assertThat(stack.status(stack.turnIds(timedOut).getFirst())).isEqualTo("FAILED");
        assertThat(openAi).anySatisfy(l -> assertThat(l).contains("处理出错"));
        assertThat(openAi.getLast()).isEqualTo("[DONE]");

        // The terminal COMPLETED write fails: the stream reports STORAGE_UNAVAILABLE instead of success.
        var broken = MemoryTestStack.newConversationId();
        answer = messages -> Flux.concat(
                Mono.fromRunnable(stack::breakTurnStorage),
                Flux.just(new LlmClient.LlmEvent.Completed("答案")));

        var failed = stream(legacy(broken, null, "q"));

        assertThat(failed).containsSubsequence("答案",
                "{\"type\":\"error\",\"content\":\"处理出错\",\"code\":\"STORAGE_UNAVAILABLE\"}",
                "{\"type\":\"done\"}");
    }

    // --- Idempotency-Key scoped to (tenant, subject, key): the duplicate-billing release gate (spec 13) ------

    private List<java.util.Map<String, Object>> keyedTurns(String key) {
        return stack.rows("SELECT id, conversation_id, status FROM chat_turns WHERE tenant_id = ? AND subject = ?"
                + " AND idempotency_key = ?", identity.tenantId(), identity.subject(), key);
    }

    private int sessionsOf(MemoryScope scope) {
        return stack.rows("SELECT id FROM sessions WHERE tenant_id = ? AND user_id = ?", scope.tenantId(),
                scope.subject()).size();
    }

    private static void expectError(WebTestClient.RequestHeadersSpec<?> request, int status, String code) {
        request.exchange().expectStatus().isEqualTo(status).expectBody().jsonPath("$.code").isEqualTo(code);
    }

    @Test
    void retryWithoutConversationIdReplaysInsteadOfReExecutingOnBothEndpoints() {
        var firstLegacy = stream(legacy(null, "retry-legacy", "第一句"));
        var retryLegacy = stream(legacy(null, "retry-legacy", "第一句"));
        var firstOpenAi = stream(completions(null, "retry-openai", "第一句"));
        var retryOpenAi = stream(completions(null, "retry-openai", "第一句"));

        assertThat(answerCalls).as("each first request runs the model once; retries replay").hasValue(2);
        assertThat(firstLegacy).contains("{\"type\":\"delta\",\"content\":\"你好\"}");
        assertThat(retryLegacy).anyMatch(line -> line.contains("你好")).last().isEqualTo("{\"type\":\"done\"}");
        assertThat(firstOpenAi).anyMatch(line -> line.contains("你好"));
        assertThat(retryOpenAi).anyMatch(line -> line.contains("你好")).last().isEqualTo("[DONE]");
        assertThat(sessionsOf(TurnTestSupport.ALICE)).as("no duplicate conversations").isEqualTo(2);
        assertThat(keyedTurns("retry-legacy")).singleElement()
                .satisfies(row -> assertThat(row.get("STATUS")).isEqualTo("COMPLETED"));
        assertThat(keyedTurns("retry-openai")).hasSize(1);
        assertThat(stack.rows("SELECT id FROM chat_turns")).hasSize(2);
    }

    @Test
    void sameKeyWithADifferentRequestIsAConflict() {
        stream(legacy(null, "k-1", "一"));

        expectError(legacy(null, "k-1", "二"), 409, "IDEMPOTENCY_CONFLICT");
        expectError(completions(null, "k-1", "一"), 409, "IDEMPOTENCY_CONFLICT");
        var conversation = keyedTurns("k-1").getFirst().get("CONVERSATION_ID").toString();
        expectError(legacy(conversation, "k-1", "一"), 409, "IDEMPOTENCY_CONFLICT");
        expectError(legacy(MemoryTestStack.newConversationId(), "k-1", "一"), 409, "IDEMPOTENCY_CONFLICT");

        assertThat(answerCalls).hasValue(1);
        assertThat(sessionsOf(TurnTestSupport.ALICE)).isEqualTo(1);
    }

    @Test
    void retryWhileTheFirstRequestIsStillRunningIs409WithItsTurnId() {
        // The first response is accepted but its stream is not consumed yet, so the turn stays in progress.
        var first = legacy(null, "k-busy", "长问题").exchange().expectStatus().isOk().returnResult(String.class);
        var turnId = keyedTurns("k-busy").getFirst().get("ID").toString();
        // The client never saw the first response, so the 409 tells it which conversation the key belongs to.
        var conversation = keyedTurns("k-busy").getFirst().get("CONVERSATION_ID").toString();
        var expected = "{\"code\":\"TURN_IN_PROGRESS\",\"turnId\":\"" + turnId + "\",\"conversationId\":\""
                + conversation + "\"}";

        legacy(null, "k-busy", "长问题").exchange().expectStatus().isEqualTo(409).expectBody().json(expected);
        completions(null, "k-busy", "长问题").exchange().expectStatus().isEqualTo(409).expectBody()
                .jsonPath("$.code").isEqualTo("IDEMPOTENCY_CONFLICT");

        first.getResponseBody().collectList().block(WAIT);
        assertThat(answerCalls).hasValue(1);
        assertThat(sessionsOf(TurnTestSupport.ALICE)).isEqualTo(1);
    }

    @Test
    void concurrentFirstRequestsWithTheSameKeyExecuteOnceOnBothEndpoints() throws Exception {
        for (var endpoint : List.of("legacy", "openai")) {
            var key = "k-race-" + endpoint;
            Function<String, WebTestClient.RequestHeadersSpec<?>> request = text -> endpoint.equals("legacy")
                    ? legacy(null, key, text) : completions(null, key, text);
            int racers = 4;
            var barrier = new java.util.concurrent.CyclicBarrier(racers);
            var pool = java.util.concurrent.Executors.newFixedThreadPool(racers);
            try {
                var futures = new java.util.ArrayList<java.util.concurrent.Future<
                        org.springframework.test.web.reactive.server.FluxExchangeResult<String>>>();
                for (int i = 0; i < racers; i++) {
                    futures.add(pool.submit(() -> {
                        barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                        return request.apply("同时发出").exchange().returnResult(String.class);
                    }));
                }
                var results = new java.util.ArrayList<org.springframework.test.web.reactive.server.FluxExchangeResult<String>>();
                for (var f : futures) {
                    results.add(f.get(30, java.util.concurrent.TimeUnit.SECONDS));
                }
                var winners = results.stream().filter(r -> r.getStatus().value() == 200).toList();
                assertThat(winners).as(endpoint + ": exactly one request is accepted").hasSize(1);
                var turnId = keyedTurns(key).getFirst().get("ID").toString();
                var conversation = keyedTurns(key).getFirst().get("CONVERSATION_ID").toString();
                for (var loser : results) {
                    if (loser.getStatus().value() != 200) {
                        assertThat(loser.getStatus().value()).isEqualTo(409);
                        assertThat(loser.getResponseBody().collectList().block(WAIT)).singleElement().asString()
                                .contains("\"TURN_IN_PROGRESS\"").contains(turnId)
                                .contains("\"conversationId\":\"" + conversation + "\"");
                    }
                }
                winners.getFirst().getResponseBody().collectList().block(WAIT);
            } finally {
                pool.shutdownNow();
            }
            assertThat(keyedTurns(key)).hasSize(1);
        }
        assertThat(answerCalls).hasValue(2);
        assertThat(sessionsOf(TurnTestSupport.ALICE)).as("losers leave no conversation behind").isEqualTo(2);
        // A retry after completion replays.
        stream(legacy(null, "k-race-legacy", "同时发出"));
        assertThat(answerCalls).hasValue(2);
    }

    @Test
    void keysAreScopedPerTenantAndSubject() {
        when(resolver.resolveModel(any())).thenReturn(Mono.just(new ResolvedModel(llm, null, null)));
        stream(legacy(null, "shared-key", "一"));
        identity = identity(new MemoryScope("tenant-1", "bob"));
        stream(legacy(null, "shared-key", "一"));
        identity = identity(TurnTestSupport.ALICE_OTHER_TENANT);
        stream(legacy(null, "shared-key", "一"));

        assertThat(answerCalls).hasValue(3);
        assertThat(stack.rows("SELECT id FROM chat_turns WHERE idempotency_key = ?", "shared-key")).hasSize(3);
    }

    @Test
    void retryForADeletedConversationIsNotFoundNotReplayed() {
        stream(legacy(null, "k-deleted", "一"));
        var conversation = keyedTurns("k-deleted").getFirst().get("CONVERSATION_ID").toString();
        stack.sql("UPDATE sessions SET deleted_at = CURRENT_TIMESTAMP(6), is_valid = 0 WHERE id = ?", conversation);

        expectError(legacy(null, "k-deleted", "一"), 404, "CONVERSATION_NOT_FOUND");
        assertThat(answerCalls).hasValue(1);
    }

    @Test
    void aKeyOlderThanTheRetentionStartsANewTurn() {
        stream(legacy(null, "k-old", "一"));
        stack.sql("UPDATE chat_turns SET created_at = TIMESTAMPADD(HOUR, -25, created_at) WHERE idempotency_key = ?",
                "k-old");

        stream(legacy(null, "k-old", "一"));

        assertThat(answerCalls).hasValue(2);
        assertThat(keyedTurns("k-old")).as("the expired key moved to the new turn").hasSize(1);
        assertThat(sessionsOf(TurnTestSupport.ALICE)).isEqualTo(2);
    }

    @Test
    void retentionCleanupReleasesOnlyExpiredKeysOfFinishedTurns(@org.junit.jupiter.api.io.TempDir java.nio.file.Path uploads) {
        stream(legacy(null, "k-expired", "一"));
        stream(legacy(null, "k-recent", "二"));
        var running = legacy(null, "k-running", "三").exchange().expectStatus().isOk().returnResult(String.class);
        stack.sql("UPDATE chat_turns SET created_at = TIMESTAMPADD(HOUR, -25, created_at)"
                + " WHERE idempotency_key IN ('k-expired', 'k-running')");
        var expiredTurn = keyedTurns("k-expired").getFirst().get("ID").toString();
        var uploadProps = new io.github.sukimiya.agileagent.config.UploadProperties();
        uploadProps.setDir(uploads.toString());
        var maintenance = new MemoryMaintenance(stack.fixture().connectionFactory(), stack.fixture().databaseClient(),
                stack.cache(), new io.github.sukimiya.agileagent.service.FileStorageService(uploadProps), stack.props(),
                stack.metrics());

        assertThat(maintenance.releaseExpiredIdempotencyKeys().block(WAIT)).isEqualTo(1L);

        assertThat(keyedTurns("k-expired")).isEmpty();
        assertThat(keyedTurns("k-recent")).hasSize(1);
        assertThat(keyedTurns("k-running")).as("an active turn keeps its key").hasSize(1);
        assertThat(stack.status(expiredTurn)).as("the turn itself is kept").isEqualTo("COMPLETED");
        running.getResponseBody().collectList().block(WAIT);
    }

    @Test
    void withoutAKeyIdenticalTextIsNeverDeduplicated() {
        stream(legacy(null, null, "同一句"));
        stream(legacy(null, null, "同一句"));
        var conversation = MemoryTestStack.newConversationId();
        stream(legacy(conversation, null, "同一句"));
        stream(legacy(conversation, null, "同一句"));

        assertThat(answerCalls).hasValue(4);
    }

    @Test
    void historyEndpointPagesWithCursorHeadersAndValidatesLimit() {
        var conversation = MemoryTestStack.newConversationId();
        stream(legacy(conversation, null, "一"));
        stream(legacy(conversation, null, "二"));

        var page = client.get().uri("/api/conversations/" + conversation + "/messages?limit=3").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Has-More", "true")
                .expectHeader().valueEquals("X-Next-Cursor", "2")
                .expectBody().jsonPath("$.length()").isEqualTo(3)
                .jsonPath("$[0].role").isEqualTo("assistant")
                .jsonPath("$[1].content").isEqualTo("二")
                .jsonPath("$[2].content").isEqualTo("你好");
        client.get().uri("/api/conversations/" + conversation + "/messages?beforeSequence=2&limit=3").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Has-More", "false")
                .expectHeader().doesNotExist("X-Next-Cursor")
                .expectBody().jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].content").isEqualTo("一");
        for (var invalid : List.of("0", "201", "-5")) {
            client.get().uri("/api/conversations/" + conversation + "/messages?limit=" + invalid).exchange()
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo("INVALID_ARGUMENT");
        }

        identity = identity(TurnTestSupport.ALICE_OTHER_TENANT);
        client.get().uri("/api/conversations/" + conversation + "/messages").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("CONVERSATION_NOT_FOUND");
        client.get().uri("/api/conversations").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.length()").isEqualTo(0);
    }
}
