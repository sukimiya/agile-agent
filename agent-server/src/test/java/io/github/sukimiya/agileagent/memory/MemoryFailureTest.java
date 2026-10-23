package io.github.sukimiya.agileagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;

import io.github.sukimiya.agileagent.agent.Orchestrator;
import io.github.sukimiya.agileagent.agent.TaskDispatcher;
import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.config.UploadProperties;
import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.service.FileStorageService;
import io.github.sukimiya.agileagent.service.LlmClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * M1 failure acceptance (plan Task 10): Redis loss, process exit after a checkpoint, finalizer failure, stale
 * fences, database write failure before and after acceptance, failed terminal commit and deletion retries. Each
 * test uses its own isolated database (an in-memory H2 here; in {@link MemoryFailureIntegrationTest} the configured
 * loopback {@code *_it} MySQL schema, cleaned and re-migrated); nothing flushes Redis or deletes directories outside
 * a temp dir.
 */
class MemoryFailureTest {

    private static final Duration WAIT = Duration.ofSeconds(15);
    /** Nothing listens on port 1: every Redis command fails with a connection error. */
    private static final RedisStandaloneConfiguration REDIS_DOWN = new RedisStandaloneConfiguration("127.0.0.1", 1);

    @TempDir Path uploads;
    private final List<MemoryTestStack> stacks = new ArrayList<>();
    private final AtomicInteger modelCalls = new AtomicInteger();

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @AfterEach
    void tearDown() {
        stacks.forEach(MemoryTestStack::close);
    }

    private MemoryTestStack stack(MemoryTestFixture fixture, java.util.function.Consumer<MemoryProperties> tune,
            java.util.function.UnaryOperator<TurnRepository> decorate, RedisStandaloneConfiguration redis) {
        var stack = MemoryTestStack.create(fixture, tune, decorate, redis);
        stacks.add(stack);
        return stack;
    }

    private MemoryTestStack stack(MemoryTestFixture fixture) {
        return stack(fixture, p -> { }, t -> t, MemoryTestStack.testRedis());
    }

    @Test
    void redisUnavailableStillReadsAndWritesDatabaseHistory() {
        var stack = stack(newFixture(), p -> { }, t -> t, REDIS_DOWN);
        var conversation = MemoryTestStack.newConversationId();

        var first = chat(stack, conversation, "第一问");
        var second = chat(stack, conversation, "第二问");

        assertThat(stack.status(first.turnId())).isEqualTo("COMPLETED");
        assertThat(stack.status(second.turnId())).isEqualTo("COMPLETED");
        assertThat(second.output()).noneMatch(line -> line.contains("\"error\""));
        // The second answer saw the first exchange, read from the database.
        assertThat(lastAnswerPrompt).extracting(LlmClient.Message::content)
                .containsSubsequence("第一问", "回答：第一问", "第二问");
        var page = stack.history().page(MemoryTestStack.ALICE, conversation, null, 10).block(WAIT);
        assertThat(page.items()).extracting(m -> m.payload().content())
                .containsExactly("第一问", "回答：第一问", "第二问", "回答：第二问");
        assertThat(stack.meters().find("memory.cache.requests").tag("result", "hit").counter().count()).isZero();
    }

    @Test
    void processExitAfterCheckpointRecoversLastCheckpointAndInterrupts() throws Exception {
        var fixture = newFixture();
        // "Process A": checkpoints every 9 bytes, never renews, and its terminal writes never complete (it dies).
        var a = stack(fixture, p -> {
            p.setCheckpointBytes(9);
            p.setLeaseRenewInterval(Duration.ofHours(1));
            p.setFinalizerTimeout(Duration.ofMillis(200));
        }, turns -> {
            var dying = spy(turns);
            doReturn(Mono.never()).when(dying).markTerminal(any(TurnTicket.class), any(TurnStatus.class),
                    any(TokenUsage.class), any());
            return dying;
        }, MemoryTestStack.testRedis());
        var conversation = MemoryTestStack.newConversationId();
        var accepted = a.accept(conversation, "写一段");
        var turnId = accepted.ticket().turnId();
        var streamed = new CountDownLatch(1);
        var subscription = a.lifecycle().execute(accepted.ticket(), recorder -> Flux.concat(
                recorder.delta("第一段").thenReturn("第一段"),   // 9 bytes: checkpointed
                recorder.delta("尾").thenReturn("尾"),           // 3 bytes: not yet checkpointed
                Mono.<String>fromRunnable(streamed::countDown),
                Mono.<String>never())).subscribe();
        assertThat(streamed.await(10, TimeUnit.SECONDS)).isTrue();
        subscription.dispose();
        a.close();
        stacks.remove(a);
        assertThat(a.status(turnId)).isEqualTo("RUNNING");

        // "Process B" starts on the same database; the lease of A's turn runs out.
        var b = stack(fixture);
        b.expireLease(turnId);
        assertThat(b.recoverExpired()).isEqualTo(1);

        assertThat(b.status(turnId)).isEqualTo("INTERRUPTED");
        assertThat(b.assistantText(turnId)).isEqualTo("第一段");
        assertThat(b.activeTurn(conversation)).isNull();
        var next = b.accept(conversation, "继续");
        assertThat(b.history().contextBefore(next.ticket()).block(WAIT)).isEmpty();
        assertThat(b.history().page(MemoryTestStack.ALICE, conversation, null, 10).block(WAIT).items())
                .extracting(m -> m.payload().content()).containsExactly("写一段", "第一段", "继续");
    }

    @Test
    void failedTerminalCommitIsNotReportedAsSuccessAndRecoveryBacksItUp() {
        var fixture = newFixture();
        var a = stack(fixture, p -> p.setFinalizerTimeout(Duration.ofMillis(500)), turns -> {
            var failing = spy(turns);
            doReturn(Mono.error(new TransientDataAccessResourceException("database unavailable"))).when(failing)
                    .markTerminal(any(TurnTicket.class), any(TurnStatus.class), any(TokenUsage.class), any());
            return failing;
        }, MemoryTestStack.testRedis());
        var conversation = MemoryTestStack.newConversationId();

        var turn = chat(a, conversation, "问题");

        assertThat(turn.output()).anyMatch(line -> line.contains("\"type\":\"error\"")
                && line.contains("\"code\":\"STORAGE_UNAVAILABLE\""));
        assertThat(turn.output().getLast()).contains("\"done\"");
        assertThat(a.status(turn.turnId())).isEqualTo("RUNNING");
        assertThat(a.meters().find("memory.finalizer.failures").counter().count()).isPositive();
        assertThat(a.meters().find("memory.turn.terminal").tag("status", "COMPLETED").counter().count()).isZero();

        var b = stack(fixture);
        b.expireLease(turn.turnId());
        assertThat(b.recoverExpired()).isEqualTo(1);
        assertThat(b.status(turn.turnId())).isEqualTo("INTERRUPTED");
        var next = b.accept(conversation, "下一个");
        assertThat(b.history().contextBefore(next.ticket()).block(WAIT)).isEmpty();
    }

    @Test
    void staleFenceCannotOverwriteAfterTheTurnWasTakenOver() {
        var stack = stack(newFixture(), p -> {
            p.setCheckpointBytes(1);
            p.setLeaseRenewInterval(Duration.ofHours(1));
        }, t -> t, MemoryTestStack.testRedis());
        var conversation = MemoryTestStack.newConversationId();
        var old = stack.accept(conversation, "旧问题");
        var deltas = Sinks.many().unicast().<String>onBackpressureBuffer();
        var errors = new ArrayList<Throwable>();
        stack.lifecycle().execute(old.ticket(), recorder -> deltas.asFlux()
                        .concatMap(text -> recorder.delta(text).thenReturn(text)))
                .subscribe(x -> { }, errors::add);
        deltas.tryEmitNext("旧-1");
        await().atMost(WAIT).until(() -> "旧-1".equals(stack.assistantText(old.ticket().turnId())));

        stack.expireLease(old.ticket().turnId());
        var fresh = stack.accept(conversation, "新问题");
        assertThat(stack.status(old.ticket().turnId())).isEqualTo("INTERRUPTED");

        deltas.tryEmitNext("旧-2");
        await().atMost(WAIT).until(() -> !errors.isEmpty());
        assertThat(errors.getFirst()).isInstanceOf(TurnLifecycle.ExecutionRightLostException.class);
        assertThat(stack.assistantText(old.ticket().turnId())).isEqualTo("旧-1");
        assertThat(stack.finalizer().finish(old.ticket(), TurnStatus.COMPLETED, "迟到的回答").block(WAIT)).isFalse();
        assertThat(stack.status(old.ticket().turnId())).isEqualTo("INTERRUPTED");
        assertThat(stack.activeTurn(conversation)).isEqualTo(fresh.ticket().turnId());

        stack.lifecycle().execute(fresh.ticket(), recorder -> recorder.completed("新回答", TokenUsage.notObserved())
                .thenMany(Flux.<String>empty())).collectList().block(WAIT);
        assertThat(stack.status(fresh.ticket().turnId())).isEqualTo("COMPLETED");
        assertThat(stack.assistantText(fresh.ticket().turnId())).isEqualTo("新回答");
        assertThat(stack.assistantText(old.ticket().turnId())).isEqualTo("旧-1");
    }

    @Test
    void databaseWriteFailureBeforeAcceptanceNeverStartsTheModel() {
        var stack = stack(newFixture());
        var conversation = MemoryTestStack.newConversationId();
        stack.breakTurnStorage();

        assertThatThrownBy(() -> stack.accept(conversation, "问题")).isInstanceOfSatisfying(MemoryException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.STORAGE_UNAVAILABLE));

        assertThat(modelCalls).hasValue(0);
        assertThat(stack.rows("SELECT id FROM sessions WHERE id = ?", conversation)).isEmpty();
        assertThat(stack.rows("SELECT id FROM chat_messages WHERE conversation_id = ?", conversation)).isEmpty();
    }

    @Test
    void databaseWriteFailureAfterAcceptanceNeverStartsTheModel() {
        var stack = stack(newFixture());
        var conversation = MemoryTestStack.newConversationId();
        var accepted = stack.accept(conversation, "问题");
        stack.breakTurnStorage();

        var output = orchestrator(stack).chat(accepted, "token", null, fakeModel()).collectList().block(WAIT);

        assertThat(output).anyMatch(line -> line.contains("\"code\":\"STORAGE_UNAVAILABLE\""));
        assertThat(output.getLast()).contains("\"done\"");
        assertThat(modelCalls).hasValue(0);
    }

    @Test
    void deletionRetriesNeverResurrectTheConversation() {
        var fixture = newFixture();
        var stack = stack(fixture);
        var conversation = MemoryTestStack.newConversationId();
        chat(stack, conversation, "要删除的内容");
        stack.history().page(MemoryTestStack.ALICE, conversation, null, 10).block(WAIT); // fills the cache
        var properties = new UploadProperties();
        properties.setDir(uploads.toString());
        var files = spy(new FileStorageService(properties));
        doReturn(Mono.error(new java.io.UncheckedIOException(new java.io.IOException("disk unavailable"))))
                .doCallRealMethod().when(files).deleteConversationFiles(conversation);
        var first = maintenance(stack, files);

        first.deleteConversation(MemoryTestStack.ALICE, conversation).block(WAIT);
        assertThat(first.runPending(10).block(WAIT)).isZero(); // cleanup fails, job goes back to PENDING
        assertThat(job(stack, conversation)).containsEntry("STATUS", "PENDING");

        assertGone(stack, conversation);
        // Messages go first; the file step failed, so the job records one attempt and only the error type.
        assertThat(stack.rows("SELECT attempts, last_error FROM memory_jobs WHERE conversation_id = ?", conversation))
                .singleElement().satisfies(row -> {
                    assertThat(((Number) row.get("ATTEMPTS")).intValue()).isEqualTo(1);
                    assertThat(String.valueOf(row.get("LAST_ERROR"))).doesNotContain("disk unavailable");
                });

        // A restarted process retries once the backoff has passed.
        stack.sql("UPDATE memory_jobs SET available_at = TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(6))"
                + " WHERE conversation_id = ?", conversation);
        var restarted = maintenance(stack, files);
        assertThat(restarted.runPending(10).block(WAIT)).isEqualTo(1);

        assertThat(job(stack, conversation)).containsEntry("STATUS", "DONE");
        assertThat(stack.rows("SELECT id FROM chat_messages WHERE conversation_id = ?", conversation)).isEmpty();
        assertGone(stack, conversation);
        assertThat(stack.rows("SELECT deleted_at FROM sessions WHERE id = ?", conversation)).singleElement()
                .satisfies(row -> assertThat(row.get("DELETED_AT")).isNotNull());
        assertThat(restarted.runPending(10).block(WAIT)).isZero();
        assertGone(stack, conversation);
    }

    // ------------------------------------------------------------------ helpers

    private record Turn(String turnId, List<String> output) {}

    private volatile List<LlmClient.Message> lastAnswerPrompt = List.of();

    private Turn chat(MemoryTestStack stack, String conversation, String text) {
        var accepted = stack.accept(conversation, text);
        var output = orchestrator(stack).chat(accepted, "token", null, fakeModel()).collectList().block(WAIT);
        return new Turn(accepted.ticket().turnId(), output);
    }

    private static Orchestrator orchestrator(MemoryTestStack stack) {
        return new Orchestrator(mock(TaskDispatcher.class), new CapabilityRegistry(List.of()), stack.lifecycle(),
                stack.history());
    }

    /** Routing answers "[]"; the answer is "回答：" + the question. Counts every model call. */
    private LlmClient fakeModel() {
        return (messages, tools) -> Flux.defer(() -> {
            modelCalls.incrementAndGet();
            if (messages.getFirst().content().contains("路由模块")) {
                return Flux.just(new LlmClient.LlmEvent.Completed("[]"));
            }
            lastAnswerPrompt = List.copyOf(messages);
            var text = "回答：" + messages.getLast().content();
            return Flux.just(new LlmClient.LlmEvent.ContentDelta(text), new LlmClient.LlmEvent.Completed(text));
        });
    }

    private static MemoryMaintenance maintenance(MemoryTestStack stack, FileStorageService files) {
        var fixture = stack.fixture();
        return new MemoryMaintenance(fixture.connectionFactory(), fixture.databaseClient(), stack.cache(), files,
                stack.props(), stack.metrics());
    }

    private static java.util.Map<String, Object> job(MemoryTestStack stack, String conversation) {
        return stack.rows("SELECT status FROM memory_jobs WHERE conversation_id = ?", conversation).getFirst();
    }

    /** History, cache-backed first page and a new turn on the same id are all refused: nothing came back. */
    private static void assertGone(MemoryTestStack stack, String conversation) {
        assertThatThrownBy(() -> stack.history().page(MemoryTestStack.ALICE, conversation, null, 10).block(WAIT))
                .isInstanceOfSatisfying(MemoryException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
        assertThatThrownBy(() -> stack.accept(conversation, "复活？")).isInstanceOfSatisfying(MemoryException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
        assertThat(stack.rows("SELECT id FROM sessions WHERE id = ? AND deleted_at IS NULL", conversation)).isEmpty();
    }
}
