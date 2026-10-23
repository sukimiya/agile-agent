package io.github.sukimiya.agileagent.memory;

import static io.github.sukimiya.agileagent.memory.TurnTestSupport.ALICE;
import static io.github.sukimiya.agileagent.memory.TurnTestSupport.ALICE_OTHER_TENANT;
import static io.github.sukimiya.agileagent.memory.TurnTestSupport.command;
import static io.github.sukimiya.agileagent.memory.TurnTestSupport.newConversationId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import io.github.sukimiya.agileagent.LocalRedisTestServer;
import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryModel.HistoryPage;
import io.github.sukimiya.agileagent.memory.MemoryModel.StoredMessage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.repository.SessionRepository;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.SessionService;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import reactor.core.publisher.Flux;

class ConversationHistoryTest {

    static final Duration WAIT = Duration.ofSeconds(10);

    TurnTestSupport support;
    LettuceConnectionFactory redisFactory;
    ReactiveStringRedisTemplate redis;
    HotMemoryCache cache;
    ConversationHistory history;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        support = TurnTestSupport.create(newFixture());
        redisFactory = new LettuceConnectionFactory(LocalRedisTestServer.configuration(HotMemoryCacheTest.REDIS_DB));
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
        cache = new HotMemoryCache(redis, support.mapper, support.props);
        history = historyWith(cache);
    }

    @AfterEach
    void tearDown() {
        support.close();
        redisFactory.destroy();
    }

    ConversationHistory historyWith(HotMemoryCache c) {
        return new ConversationHistory(support.fixture.databaseClient(),
                new SessionRepository(support.fixture.databaseClient()), support.conversations, c, support.props,
                support.metrics);
    }

    /** Creates an owned session with {@code count} messages, each in its own COMPLETED turn. */
    void seed(String conversation, int count) {
        new SessionService(new SessionRepository(support.fixture.databaseClient()))
                .createOwned(conversation, ALICE, "seed").block(WAIT);
        for (int i = 1; i <= count; i++) {
            var turnId = NonBlockingIds.uuid();
            support.fixture.jdbc().update("INSERT INTO chat_turns (id, tenant_id, subject, conversation_id, request_id,"
                    + " request_hash, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 'COMPLETED', ?, ?)",
                    turnId, ALICE.tenantId(), ALICE.subject(), conversation, "seed-" + i, "0".repeat(64),
                    LocalDateTime.now(), LocalDateTime.now());
            var message = i % 2 == 1 ? LlmClient.Message.user("q" + i) : LlmClient.Message.assistantText("a" + i);
            support.conversations.appendMessage(ALICE, conversation, turnId, message.role(),
                    i % 2 == 1 ? SourceType.USER_INPUT : SourceType.ASSISTANT,
                    support.conversations.serialize(message)).block(WAIT);
        }
        support.conversations.bumpRevision(conversation).block(WAIT);
    }

    HistoryPage page(String conversation, Long before, int limit) {
        return history.page(ALICE, conversation, before, limit).block(WAIT);
    }

    String cacheKey(String conversation) {
        return HotMemoryCache.key(support.props.getNamespace(), ALICE, conversation);
    }

    static List<Long> sequences(HistoryPage page) {
        return page.items().stream().map(StoredMessage::sequence).toList();
    }

    @Test
    void paginationKeepsStableIdsAndOrdering() {
        var conversation = newConversationId();
        seed(conversation, 205);

        var newest = page(conversation, null, 100);
        assertThat(newest.items()).hasSize(100);
        assertThat(sequences(newest).get(0)).isEqualTo(106);
        assertThat(sequences(newest)).isSorted();
        assertThat(newest.hasMore()).isTrue();
        assertThat(newest.nextBeforeSequence()).isEqualTo(106);

        var middle = page(conversation, newest.nextBeforeSequence(), 100);
        assertThat(sequences(middle)).first().isEqualTo(6L);
        assertThat(sequences(middle)).last().isEqualTo(105L);
        assertThat(middle.nextBeforeSequence()).isEqualTo(6);

        var oldest = page(conversation, middle.nextBeforeSequence(), 100);
        assertThat(sequences(oldest)).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(oldest.hasMore()).isFalse();
        assertThat(oldest.nextBeforeSequence()).isNull();

        var ids = new HashSet<String>();
        for (var p : List.of(newest, middle, oldest)) {
            p.items().forEach(m -> ids.add(m.id()));
        }
        assertThat(ids).hasSize(205);
        assertThat(page(conversation, null, 100).items()).extracting(StoredMessage::id)
                .containsExactlyElementsOf(newest.items().stream().map(StoredMessage::id).toList());
        assertThat(page(conversation, 106L, 100).items()).extracting(StoredMessage::id)
                .containsExactlyElementsOf(middle.items().stream().map(StoredMessage::id).toList());
        assertThat(newest.items().get(99).payload().content()).isEqualTo("q205");
        assertThat(newest.items().get(98).source()).isEqualTo(SourceType.ASSISTANT);
    }

    @Test
    void smallerLimitsAreServedFromTheSameOrder() {
        var conversation = newConversationId();
        seed(conversation, 12);

        var first = page(conversation, null, 5);
        assertThat(sequences(first)).containsExactly(8L, 9L, 10L, 11L, 12L);
        assertThat(first.hasMore()).isTrue();
        assertThat(sequences(page(conversation, first.nextBeforeSequence(), 5))).containsExactly(3L, 4L, 5L, 6L, 7L);
    }

    @Test
    void limitMustBeWithinOneAndTwoHundred() {
        var conversation = newConversationId();
        seed(conversation, 1);

        for (int invalid : new int[] {0, -1, 201}) {
            assertThatThrownBy(() -> page(conversation, null, invalid))
                    .isInstanceOfSatisfying(MemoryException.class,
                            e -> assertThat(e.code()).isEqualTo(MemoryException.ErrorCode.INVALID_ARGUMENT));
        }
        assertThat(page(conversation, null, 200).items()).hasSize(1);
    }

    @Test
    void foreignLegacyAndUnknownConversationsAreNotFound() {
        var conversation = newConversationId();
        seed(conversation, 2);
        new SessionRepository(support.fixture.databaseClient()).insert("legacy-id", "alice", "旧").block(WAIT);

        for (var call : List.<Runnable>of(
                () -> history.page(ALICE_OTHER_TENANT, conversation, null, 10).block(WAIT),
                () -> page("legacy-id", null, 10),
                () -> page(newConversationId(), null, 10))) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(MemoryException.class,
                    e -> assertThat(e.code()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
        }
    }

    @Test
    void redisMissErrorOrCorruptJsonFallsBackToDatabase() {
        var conversation = newConversationId();
        seed(conversation, 3);

        assertThat(sequences(page(conversation, null, 10))).containsExactly(1L, 2L, 3L); // miss
        assertThat(redis.hasKey(cacheKey(conversation)).block(WAIT)).isTrue();

        redis.<String, String>opsForHash().put(cacheKey(conversation), "data", "{broken").block(WAIT);
        assertThat(sequences(page(conversation, null, 10))).containsExactly(1L, 2L, 3L); // corrupt

        var down = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1));
        down.afterPropertiesSet();
        try {
            var noRedis = historyWith(new HotMemoryCache(new ReactiveStringRedisTemplate(down), support.mapper,
                    support.props));
            assertThat(sequences(noRedis.page(ALICE, conversation, null, 10).block(Duration.ofSeconds(30))))
                    .containsExactly(1L, 2L, 3L); // error
        } finally {
            down.destroy();
        }
    }

    @Test
    void cacheHitServesTheFirstPageUntilTheRevisionChanges() {
        var conversation = newConversationId();
        seed(conversation, 2);
        page(conversation, null, 10);
        support.fixture.jdbc().update("UPDATE chat_messages SET payload = ? WHERE conversation_id = ? AND sequence = 2",
                support.conversations.serialize(LlmClient.Message.assistantText("changed")), conversation);

        assertThat(page(conversation, null, 10).items().get(1).payload().content()).isEqualTo("a2");

        support.conversations.bumpRevision(conversation).block(WAIT);
        assertThat(page(conversation, null, 10).items().get(1).payload().content()).isEqualTo("changed");
    }

    @Test
    void staleRevisionIsIgnored() {
        var conversation = newConversationId();
        seed(conversation, 2);
        page(conversation, null, 10);

        var accepted = support.lifecycle.accept(command(conversation, "k-new", "new question")).block(WAIT);
        support.lifecycle.execute(accepted.ticket(),
                r -> r.completed("new answer", TokenUsage.notObserved()).thenMany(Flux.<String>empty())).blockLast(WAIT);

        assertThat(page(conversation, null, 10).items()).extracting(m -> m.payload().content())
                .containsExactly("q1", "a2", "new question", "new answer");
    }

    @Test
    void cacheHasSevenDayIdleTtlAndSizeLimit() {
        var conversation = newConversationId();
        seed(conversation, 205);

        page(conversation, null, 20);

        var cached = cache.get(ALICE, conversation, support.revision(conversation)).block(WAIT);
        var raw = redis.<String, String>opsForHash().get(cacheKey(conversation), "data").block(WAIT);
        assertThat(cached.items()).hasSize(100);
        assertThat(cached.hasMore()).isTrue();
        assertThat(raw.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(262_144);
        assertThat(redis.getExpire(cacheKey(conversation)).block(WAIT))
                .isBetween(Duration.ofDays(7).minusMinutes(1), Duration.ofDays(7));
        assertThat(support.fixture.jdbc().queryForObject(
                "SELECT COUNT(*) FROM chat_messages WHERE conversation_id = ?", Integer.class, conversation))
                .isEqualTo(205);
    }

    @Test
    void windowsWithActiveTurnsAreNotCached() {
        var conversation = newConversationId();
        seed(conversation, 2);
        support.lifecycle.accept(command(conversation, "k-active", "pending")).block(WAIT);

        var page = page(conversation, null, 10);

        assertThat(page.items()).extracting(m -> m.status().name()).containsExactly("COMPLETED", "COMPLETED", "ACCEPTED");
        assertThat(redis.hasKey(cacheKey(conversation)).block(WAIT)).isFalse();
    }

    @Test
    void currentAndIncompleteTurnsExcludedFromPrompt() {
        var conversation = newConversationId();
        seed(conversation, 2);
        var failed = support.lifecycle.accept(command(conversation, "k-fail", "will fail")).block(WAIT);
        assertThatThrownBy(() -> support.lifecycle.execute(failed.ticket(),
                r -> r.delta("partial").thenMany(Flux.<String>error(new IllegalStateException("boom")))).blockLast(WAIT))
                .hasMessageContaining("boom");
        var current = support.lifecycle.accept(command(conversation, "k-now", "current question")).block(WAIT);

        var context = history.contextBefore(current.ticket()).block(WAIT);

        assertThat(context).extracting(LlmClient.Message::role, LlmClient.Message::content)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("user", "q1"),
                        org.assertj.core.groups.Tuple.tuple("assistant", "a2"));
    }

    @Test
    void contextBeforeRequiresTheOwnedLiveSession() {
        var conversation = newConversationId();
        seed(conversation, 2);
        var current = support.lifecycle.accept(command(conversation, "k-now", "q")).block(WAIT);
        var foreign = new MemoryModel.TurnTicket(ALICE_OTHER_TENANT, conversation, current.ticket().turnId(), 1);

        assertThatThrownBy(() -> history.contextBefore(foreign).block(WAIT)).isInstanceOfSatisfying(
                MemoryException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    @Test
    void deletedSessionCannotRefillCache() {
        var conversation = newConversationId();
        seed(conversation, 2);
        page(conversation, null, 10);
        support.fixture.jdbc().update("UPDATE sessions SET deleted_at = ? WHERE id = ?", LocalDateTime.now(), conversation);
        var revisionAtDeletion = support.revision(conversation);

        assertThatThrownBy(() -> page(conversation, null, 10)).isInstanceOfSatisfying(MemoryException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));

        cache.evict(ALICE, conversation).block(WAIT);
        assertThatThrownBy(() -> page(conversation, null, 10)).isInstanceOf(MemoryException.class);
        assertThat(redis.hasKey(cacheKey(conversation)).block(WAIT)).isFalse();
        assertThat(cache.get(ALICE, conversation, revisionAtDeletion).blockOptional(WAIT)).isEmpty();
    }

    @Test
    void legacyChatmemoryKeyIsIgnored() {
        var conversation = newConversationId();
        seed(conversation, 1);
        var legacy = new ArrayList<LlmClient.Message>(List.of(LlmClient.Message.user("old redis-only history")));
        redis.opsForValue().set("chatmemory:" + conversation, support.conversations.serialize(legacy.get(0))).block(WAIT);
        try {
            assertThat(page(conversation, null, 10).items()).extracting(m -> m.payload().content())
                    .containsExactly("q1");
        } finally {
            redis.delete("chatmemory:" + conversation).block(WAIT);
        }
    }
}
