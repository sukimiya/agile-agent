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
        when(resolver.resolve("tenant-1")).thenReturn(Mono.just(llm));
        answer = messages -> Flux.just(new LlmClient.LlmEvent.ContentDelta("你好"),
                new LlmClient.LlmEvent.Completed("你好"), new LlmClient.LlmEvent.Usage(2, 3, 5));
        var orchestrator = new Orchestrator(mock(TaskDispatcher.class), new CapabilityRegistry(List.of()),
                stack.lifecycle(), stack.history());
        var controller = new ChatController(orchestrator, stack.sessionService(), stack.lifecycle(), stack.history(),
                resolver);
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
        when(resolver.resolve("tenant-1")).thenReturn(Mono.error(new TenantLlmConfigRequiredException()));

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
        var openAiReplay = stream(completions(conversation, "same-key", "问题"));

        assertThat(answerCalls).hasValue(1);
        assertThat(stack.turnCount(conversation)).isEqualTo(1);
        assertThat(stack.messageCount(conversation, "user")).isEqualTo(1);
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
                .expectBody().json("{\"code\":\"TURN_IN_PROGRESS\",\"turnId\":\"" + activeTurn + "\"}");
        completions(conversation, "first", "一").exchange().expectStatus().isEqualTo(409)
                .expectBody().json("{\"code\":\"TURN_IN_PROGRESS\",\"turnId\":\"" + activeTurn + "\"}");
        completions(conversation, "first", "不同的文本").exchange().expectStatus().isEqualTo(409)
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
        verify(resolver, never()).resolve(any());
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

    @Test
    void retryWithoutConversationIdCreatesNewTurnOnBothEndpoints() {
        // Known gap (spec 13): without a conversationId the key is scoped to a fresh conversation, so a retry
        // of the first request runs the model again. Production gate; flip this assertion once fixed.
        stream(legacy(null, "retry-key", "第一句"));
        stream(legacy(null, "retry-key", "第一句"));
        stream(completions(null, "retry-key", "第一句"));
        stream(completions(null, "retry-key", "第一句"));

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
