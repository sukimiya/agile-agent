package io.github.sukimiya.agileagent.memory;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import io.github.sukimiya.agileagent.LocalRedisTestServer;
import io.github.sukimiya.agileagent.memory.MemoryModel.AcceptedTurn;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnCommand;
import io.github.sukimiya.agileagent.repository.SessionRepository;
import io.github.sukimiya.agileagent.service.SessionService;
import io.github.sukimiya.agileagent.util.NonBlockingIds;

/**
 * The real memory stack (lifecycle, finalizer, history with Redis cache, session service) on an isolated H2
 * database, for tests outside this package that exercise chat entry points end to end.
 */
public final class MemoryTestStack implements AutoCloseable {

    public static final int REDIS_DB = 5;
    public static final MemoryScope ALICE = TurnTestSupport.ALICE;
    private static final Duration WAIT = Duration.ofSeconds(10);

    private final TurnTestSupport support;
    private final LettuceConnectionFactory redisFactory;
    private final ConversationHistory history;
    private final SessionService sessions;

    private MemoryTestStack(MemoryTestFixture fixture, Consumer<MemoryProperties> tune) {
        this.support = TurnTestSupport.create(fixture, tune, t -> t);
        this.redisFactory = new LettuceConnectionFactory(LocalRedisTestServer.configuration(REDIS_DB));
        redisFactory.afterPropertiesSet();
        var cache = new HotMemoryCache(new ReactiveStringRedisTemplate(redisFactory), support.mapper, support.props);
        var sessionRepository = new SessionRepository(fixture.databaseClient());
        this.history = new ConversationHistory(fixture.databaseClient(), sessionRepository, support.conversations,
                cache, support.props);
        this.sessions = new SessionService(sessionRepository);
    }

    public static MemoryTestStack h2() {
        return new MemoryTestStack(MemoryTestFixture.h2(), p -> { });
    }

    public static MemoryTestStack of(MemoryTestFixture fixture) {
        return new MemoryTestStack(fixture, p -> { });
    }

    public static MemoryTestStack h2(Consumer<MemoryProperties> tune) {
        return new MemoryTestStack(MemoryTestFixture.h2(), tune);
    }

    public TurnLifecycle lifecycle() {
        return support.lifecycle;
    }

    public ConversationHistory history() {
        return history;
    }

    public SessionService sessionService() {
        return sessions;
    }

    public MemoryProperties props() {
        return support.props;
    }

    public static String newConversationId() {
        return NonBlockingIds.uuid();
    }

    public AcceptedTurn accept(String conversationId, String text) {
        return accept(ALICE, conversationId, NonBlockingIds.uuid(), text);
    }

    public AcceptedTurn accept(MemoryScope scope, String conversationId, String requestId, String text) {
        return support.lifecycle.accept(new TurnCommand(scope, conversationId, requestId, text)).block(WAIT);
    }

    public String status(String turnId) {
        return support.status(turnId);
    }

    public String errorCode(String turnId) {
        return support.fixture.jdbc().queryForObject("SELECT error_code FROM chat_turns WHERE id = ?", String.class,
                turnId);
    }

    /** usage_observed, prompt_tokens, completion_tokens, total_tokens of a turn. */
    public Map<String, Object> usage(String turnId) {
        return support.fixture.jdbc().queryForMap("SELECT usage_observed, prompt_tokens, completion_tokens,"
                + " total_tokens FROM chat_turns WHERE id = ?", turnId);
    }

    public int turnCount(String conversationId) {
        return support.turnCount(conversationId);
    }

    public int messageCount(String conversationId, String role) {
        return support.messageCount(conversationId, role);
    }

    public String activeTurn(String conversationId) {
        return support.activeTurn(conversationId);
    }

    public String assistantText(String turnId) {
        return support.assistantText(turnId);
    }

    public void expireLease(String turnId) {
        support.expireLease(turnId);
    }

    /** Takes the execution right away from a running turn, as recovery or deletion would. */
    public void revokeFence(String turnId) {
        support.fixture.jdbc().update("UPDATE chat_turns SET status = 'INTERRUPTED', active_conversation_id = NULL,"
                + " fence = fence + 1 WHERE id = ?", turnId);
    }

    public java.util.List<String> turnIds(String conversationId) {
        return support.fixture.jdbc().queryForList(
                "SELECT id FROM chat_turns WHERE conversation_id = ? ORDER BY created_at", String.class, conversationId);
    }

    /** Makes every later turn write fail, as if the database became unavailable. */
    public void breakTurnStorage() {
        support.fixture.jdbc().execute("ALTER TABLE chat_turns RENAME TO chat_turns_unavailable");
    }

    public void insertForeignSession(String conversationId, MemoryScope owner) {
        sessions.createOwned(conversationId, owner, "foreign").block(WAIT);
    }

    @Override
    public void close() {
        support.close();
        redisFactory.destroy();
    }
}
