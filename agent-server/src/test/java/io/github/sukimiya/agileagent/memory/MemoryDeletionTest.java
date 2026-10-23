package io.github.sukimiya.agileagent.memory;

import static io.github.sukimiya.agileagent.memory.TurnTestSupport.ALICE;
import static io.github.sukimiya.agileagent.memory.TurnTestSupport.ALICE_OTHER_TENANT;
import static io.github.sukimiya.agileagent.memory.TurnTestSupport.newConversationId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import io.github.sukimiya.agileagent.LocalRedisTestServer;
import io.github.sukimiya.agileagent.config.UploadProperties;
import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryModel.HistoryWindow;
import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnCommand;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.repository.SessionRepository;
import io.github.sukimiya.agileagent.service.FileStorageService;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.SessionService;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Conversation deletion: an immediate tombstone that revokes execution rights, then a durable, retryable cleanup
 * job. H2 here; {@code MemoryDeletionMySqlIntegrationTest} runs the same cases on MySQL.
 */
class MemoryDeletionTest {

    static final Duration WAIT = Duration.ofSeconds(10);
    static final MemoryScope BOB = new MemoryScope("tenant-1", "bob");

    @TempDir Path uploadDir;

    MemoryTestFixture fixture;
    TurnTestSupport support;
    LettuceConnectionFactory redisFactory;
    ReactiveStringRedisTemplate redis;
    HotMemoryCache cache;
    FileStorageService files;
    ConversationHistory history;
    SessionService sessions;
    MemoryMaintenance maintenance;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        fixture = newFixture();
        support = TurnTestSupport.create(fixture, p -> p.setLeaseRenewInterval(Duration.ofMillis(100)), t -> t);
        redisFactory = new LettuceConnectionFactory(LocalRedisTestServer.configuration(MemoryTestStack.REDIS_DB));
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
        cache = new HotMemoryCache(redis, support.mapper, support.props);
        var uploads = new UploadProperties();
        uploads.setDir(uploadDir.toString());
        files = spy(new FileStorageService(uploads));
        var sessionRepository = new SessionRepository(fixture.databaseClient());
        history = new ConversationHistory(fixture.databaseClient(), sessionRepository, support.conversations, cache,
                support.props, support.metrics);
        sessions = new SessionService(sessionRepository);
        maintenance = new MemoryMaintenance(fixture.connectionFactory(), fixture.databaseClient(), cache, files,
                support.props, support.metrics);
    }

    @AfterEach
    void tearDown() {
        support.close();
        redisFactory.destroy();
    }

    // --- helpers -------------------------------------------------------------------------------------------

    private TurnTicket accept(MemoryScope scope, String conversationId, String text) {
        return support.lifecycle.accept(new TurnCommand(scope, conversationId, NonBlockingIds.uuid(), text))
                .block(WAIT).ticket();
    }

    private TurnTicket completedTurn(MemoryScope scope, String conversationId, String question, String answer) {
        var ticket = accept(scope, conversationId, question);
        support.lifecycle.execute(ticket,
                r -> r.completed(answer, TokenUsage.notObserved()).thenMany(Flux.<String>empty())).blockLast(WAIT);
        return ticket;
    }

    private void delete(MemoryScope scope, String conversationId) {
        maintenance.deleteConversation(scope, conversationId).block(WAIT);
    }

    private int runPending() {
        return maintenance.runPending(10).block(WAIT);
    }

    private int count(String sql, Object... args) {
        return fixture.jdbc().queryForObject(sql, Integer.class, args);
    }

    private int messages(String conversationId) {
        return count("SELECT COUNT(*) FROM chat_messages WHERE conversation_id = ?", conversationId);
    }

    private int toolEvents(String conversationId) {
        return count("SELECT COUNT(*) FROM chat_tool_events WHERE conversation_id = ?", conversationId);
    }

    private int jobs(String conversationId, String status) {
        return count("SELECT COUNT(*) FROM memory_jobs WHERE conversation_id = ? AND status = ?", conversationId,
                status);
    }

    private Path attachment(String conversationId, String name) throws Exception {
        var dir = Files.createDirectories(uploadDir.resolve(conversationId));
        return Files.writeString(dir.resolve(name), "attachment of " + conversationId);
    }

    private static void assertNotFound(Mono<?> call) {
        assertThatThrownBy(() -> call.block(WAIT)).isInstanceOfSatisfying(MemoryException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    // --- tests ---------------------------------------------------------------------------------------------

    @Test
    void deleteHidesConversationImmediately() {
        var conversation = newConversationId();
        completedTurn(ALICE, conversation, "我的项目代号是蓝鲸", "记住了");
        assertThat(history.page(ALICE, conversation, null, 50).block(WAIT).items()).hasSize(2);

        delete(ALICE, conversation);

        // Before any cleanup runs, nothing of the conversation is reachable any more.
        assertNotFound(history.page(ALICE, conversation, null, 50));
        assertThat(sessions.findOwned(conversation, ALICE).blockOptional()).isEmpty();
        assertThat(sessions.listForUser(ALICE).collectList().block(WAIT)).extracting(r -> r.id())
                .doesNotContain(conversation);
        assertNotFound(sessions.verifyAccess(conversation, ALICE));
        assertNotFound(support.lifecycle.accept(new TurnCommand(ALICE, conversation, "k-new", "还在吗")));
        assertThat(fixture.jdbc().queryForObject("SELECT title FROM sessions WHERE id = ?", String.class, conversation))
                .as("the tombstone keeps no content").doesNotContain("蓝鲸");
        assertThat(jobs(conversation, "PENDING")).isEqualTo(1);

        assertThat(runPending()).isEqualTo(1);

        assertThat(messages(conversation)).isZero();
        assertThat(jobs(conversation, "DONE")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM sessions WHERE id = ? AND deleted_at IS NOT NULL", conversation))
                .as("the deletion marker is kept permanently").isEqualTo(1);
        // The id stays taken: neither chat nor upload can bring the conversation back.
        assertNotFound(support.lifecycle.accept(new TurnCommand(ALICE, conversation, "k-again", "复活")));
        assertNotFound(sessions.createOwned(conversation, ALICE, "复活"));
    }

    @Test
    void deletionInterruptCountsAsInterruptedTerminalOnce() {
        var conversation = newConversationId();
        var ticket = accept(ALICE, conversation, "还没开始");

        delete(ALICE, conversation);
        delete(ALICE, conversation);

        assertThat(support.status(ticket.turnId())).isEqualTo("INTERRUPTED");
        var interrupted = support.meters.find("memory.turn.terminal").tag("status", "INTERRUPTED").counter();
        assertThat(interrupted).isNotNull();
        assertThat(interrupted.count()).isEqualTo(1.0);
    }

    @Test
    void deleteInterruptsActiveTurnAndInvalidatesFence() throws Exception {
        var conversation = newConversationId();
        var ticket = accept(ALICE, conversation, "长任务");
        var started = new CountDownLatch(1);
        var errors = new ArrayList<Throwable>();
        Function<TurnRecorder, Flux<String>> streaming = recorder -> Flux.concat(
                recorder.delta("partial").thenReturn("partial"),
                Mono.<String>fromRunnable(started::countDown).then(Mono.never()));
        var subscription = support.lifecycle.execute(ticket, streaming)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(x -> { }, errors::add);
        try {
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            var toolRecorder = new TurnToolRecorder(new ToolEventRepository(fixture.databaseClient()), support.props);
            toolRecorder.started(ticket, "TOOLS", new LlmClient.ToolCall("c1", "lookup", "{}")).block(WAIT);

            delete(ALICE, conversation);

            assertThat(support.status(ticket.turnId())).isEqualTo("INTERRUPTED");
            assertThat(fixture.jdbc().queryForObject("SELECT error_code FROM chat_turns WHERE id = ?", String.class,
                    ticket.turnId())).isEqualTo("CONVERSATION_DELETED");
            assertThat(support.fence(ticket.turnId())).isGreaterThan(ticket.fence());
            assertThat(support.activeTurn(conversation)).isNull();
            assertThat(fixture.jdbc().queryForObject("SELECT status FROM chat_tool_events WHERE turn_id = ?",
                    String.class, ticket.turnId())).isEqualTo("UNCERTAIN");
            assertThat(support.turns.renew(ticket).block(WAIT)).isFalse();

            await().atMost(WAIT).until(() -> !errors.isEmpty());
            assertThat(errors.get(0)).isInstanceOf(TurnLifecycle.ExecutionRightLostException.class);

            runPending();
            // The stale executor can neither checkpoint nor finish: nothing reappears after cleanup.
            assertThat(support.finalizer.finish(ticket, MemoryModel.TurnStatus.COMPLETED, "late answer").block(WAIT))
                    .isFalse();
            assertThat(messages(conversation)).isZero();
            assertThat(toolEvents(conversation)).isZero();
            assertThat(support.status(ticket.turnId())).isEqualTo("INTERRUPTED");
        } finally {
            subscription.dispose();
        }
    }

    @Test
    void deleteWinsOverCacheRefill() {
        var conversation = newConversationId();
        completedTurn(ALICE, conversation, "q", "a");
        var page = history.page(ALICE, conversation, null, 50).block(WAIT);
        var revision = support.revision(conversation);
        var window = new HistoryWindow(page.items(), false, revision);
        assertThat(cache.get(ALICE, conversation, revision).blockOptional()).as("cache filled").isPresent();

        delete(ALICE, conversation);
        // A reader that resolved the session just before the delete now writes its (stale) window back.
        cache.put(ALICE, conversation, revision, window).block(WAIT);
        cache.put(ALICE, conversation, revision + 10, window).block(WAIT);

        assertThat(cache.get(ALICE, conversation, revision).blockOptional()).isEmpty();
        assertThat(cache.get(ALICE, conversation, revision + 10).blockOptional()).isEmpty();
        assertNotFound(history.page(ALICE, conversation, null, 50));

        runPending();
        cache.put(ALICE, conversation, revision + 20, window).block(WAIT);

        var key = HotMemoryCache.key(support.props.getNamespace(), ALICE, conversation);
        assertThat(redis.<String, String>opsForHash().get(key, "data").blockOptional())
                .as("no message content is cached for a deleted conversation").isEmpty();
        assertNotFound(history.page(ALICE, conversation, null, 50));
    }

    @Test
    void deleteRetryIsIdempotent() {
        var conversation = newConversationId();
        completedTurn(ALICE, conversation, "q", "a");

        delete(ALICE, conversation);
        delete(ALICE, conversation);

        assertThat(count("SELECT COUNT(*) FROM memory_jobs WHERE conversation_id = ?", conversation)).isEqualTo(1);
        assertThat(runPending()).isEqualTo(1);
        assertThat(runPending()).isZero();
        delete(ALICE, conversation);
        assertThat(runPending()).isZero();
        assertThat(messages(conversation)).isZero();
    }

    @Test
    void failedCleanupIsRetriedUntilDone() throws Exception {
        var conversation = newConversationId();
        completedTurn(ALICE, conversation, "q", "a");
        attachment(conversation, "a.md");
        doReturn(Mono.error(new java.io.IOException("disk /secret/path busy")))
                .doCallRealMethod()
                .when(files).deleteConversationFiles(anyString());

        delete(ALICE, conversation);

        assertThat(runPending()).isZero();
        assertThat(jobs(conversation, "PENDING")).isEqualTo(1);
        var job = fixture.jdbc().queryForMap("SELECT attempts, last_error FROM memory_jobs WHERE conversation_id = ?",
                conversation);
        assertThat(((Number) job.get("ATTEMPTS")).intValue()).isEqualTo(1);
        assertThat((String) job.get("LAST_ERROR")).isEqualTo("IOException").doesNotContain("/secret");
        assertThat(maintenance.runPending(10).block(WAIT)).as("backoff: not yet due").isZero();

        fixture.jdbc().update("UPDATE memory_jobs SET available_at = CURRENT_TIMESTAMP(6) WHERE conversation_id = ?",
                conversation);
        assertThat(runPending()).isEqualTo(1);
        assertThat(uploadDir.resolve(conversation)).doesNotExist();
        assertThat(messages(conversation)).isZero();
    }

    @Test
    void abandonedRunningJobIsReclaimed() {
        var conversation = newConversationId();
        completedTurn(ALICE, conversation, "q", "a");
        delete(ALICE, conversation);
        // A cleanup that started on a process which then died: RUNNING with an expired lease.
        fixture.jdbc().update("UPDATE memory_jobs SET status = 'RUNNING', lease_owner = 'dead',"
                + " lease_expires_at = TIMESTAMPADD(SECOND, -60, CURRENT_TIMESTAMP(6)) WHERE conversation_id = ?",
                conversation);

        assertThat(runPending()).isEqualTo(1);
        assertThat(jobs(conversation, "DONE")).isEqualTo(1);
        assertThat(messages(conversation)).isZero();
    }

    @Test
    void deleteForeignConversationReturnsNotFound() {
        var bobs = newConversationId();
        completedTurn(BOB, bobs, "bob q", "bob a");
        var alices = newConversationId();
        completedTurn(ALICE, alices, "q", "a");

        assertNotFound(maintenance.deleteConversation(ALICE, bobs));
        assertNotFound(maintenance.deleteConversation(ALICE_OTHER_TENANT, alices));
        assertNotFound(maintenance.deleteConversation(ALICE, newConversationId()));
        assertThatThrownBy(() -> maintenance.deleteConversation(ALICE, "a_b").block(WAIT))
                .isInstanceOfSatisfying(MemoryException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_CONVERSATION_ID));

        assertThat(runPending()).isZero();
        assertThat(history.page(BOB, bobs, null, 50).block(WAIT).items()).hasSize(2);
        assertThat(history.page(ALICE, alices, null, 50).block(WAIT).items()).hasSize(2);
    }

    @Test
    void deletedConversationAttachmentsAreNotDownloadable() throws Exception {
        var conversation = newConversationId();
        completedTurn(ALICE, conversation, "q", "a");
        attachment(conversation, "plan.md");

        delete(ALICE, conversation);

        // Download is gated on findOwned, which no longer sees the conversation.
        assertThat(sessions.findOwned(conversation, ALICE).blockOptional()).isEmpty();
        runPending();
        assertThat(uploadDir.resolve(conversation)).doesNotExist();
    }

    @Test
    void deletingOneConversationKeepsOtherUsersFiles() throws Exception {
        var mine = newConversationId();
        var myOther = newConversationId();
        var bobs = newConversationId();
        completedTurn(ALICE, mine, "q", "a");
        completedTurn(ALICE, myOther, "q2", "a2");
        completedTurn(BOB, bobs, "bq", "ba");
        attachment(mine, "a.md");
        var keptMine = attachment(myOther, "a.md");
        var keptBobs = attachment(bobs, "a.md");

        delete(ALICE, mine);
        runPending();

        assertThat(uploadDir.resolve(mine)).doesNotExist();
        assertThat(keptMine).exists();
        assertThat(keptBobs).exists();
        assertThat(messages(myOther)).isEqualTo(2);
        assertThat(messages(bobs)).isEqualTo(2);
        try (var dirs = Files.list(uploadDir)) {
            assertThat(dirs.map(p -> p.getFileName().toString()).toList()).containsExactlyInAnyOrder(myOther, bobs);
        }
    }

    @Test
    void concurrentAcceptAndDeleteNeverLeaveALiveTurn() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 15; i++) {
                var conversation = newConversationId();
                completedTurn(ALICE, conversation, "q", "a");
                var barrier = new CyclicBarrier(2);
                var accepted = pool.submit(() -> {
                    barrier.await();
                    try {
                        return (Object) support.lifecycle.accept(
                                new TurnCommand(ALICE, conversation, NonBlockingIds.uuid(), "race")).block(WAIT);
                    } catch (Exception e) {
                        return e;
                    }
                });
                var deleted = pool.submit(() -> {
                    barrier.await();
                    maintenance.deleteConversation(ALICE, conversation).block(WAIT);
                    return null;
                });
                deleted.get(20, TimeUnit.SECONDS);
                var outcome = accepted.get(20, TimeUnit.SECONDS);

                if (outcome instanceof MemoryException e) {
                    assertThat(e.code()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND);
                } else {
                    assertThat(outcome).as("accept either lost or was interrupted by the delete")
                            .isInstanceOf(MemoryModel.AcceptedTurn.class);
                }
                assertThat(support.activeTurn(conversation)).as("iteration " + i).isNull();
                assertThat(count("SELECT COUNT(*) FROM chat_turns WHERE conversation_id = ? AND status IN"
                        + " ('ACCEPTED', 'RUNNING')", conversation)).isZero();
                runPending();
                assertThat(messages(conversation)).isZero();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** Unused helper kept for symmetry with other suites. */
    @SuppressWarnings("unused")
    private static List<String> ids(List<MemoryModel.StoredMessage> items) {
        return items.stream().map(MemoryModel.StoredMessage::id).toList();
    }
}
