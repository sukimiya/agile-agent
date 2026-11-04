package io.github.sukimiya.agileagent.memory.recall;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.LocalRedisTestServer;
import io.github.sukimiya.agileagent.memory.ConversationRepository;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.memory.SourceType;
import io.github.sukimiya.agileagent.memory.items.EntityGraphRepository;
import io.github.sukimiya.agileagent.memory.items.ItemStatus;
import io.github.sukimiya.agileagent.memory.items.MemoryItem;
import io.github.sukimiya.agileagent.memory.items.MemoryItemRepository;
import io.github.sukimiya.agileagent.memory.items.MemoryItemService;
import io.github.sukimiya.agileagent.memory.items.MemoryItemSourceRepository;
import io.github.sukimiya.agileagent.memory.items.ProfileCache;
import io.github.sukimiya.agileagent.memory.items.ProfileService;
import io.github.sukimiya.agileagent.memory.judgement.ValidatedAction.SourceRef;
import io.github.sukimiya.agileagent.memory.storage.MessageCompressor;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * M2b Task 9 recall warm-up (user decision 2026-10-30 point 8): the plain text of recalled compressed originals is
 * kept in Redis for {@code recall-cache-ttl}; deleting the conversation, forgetting or deleting an item evicts it,
 * and a cached turn is still served only while the database says it is live and completed.
 */
class RecallTextCacheTest {

    private static final MemoryScope ALICE = new MemoryScope("t1", "alice");

    private MemoryTestFixture fixture;
    private JdbcTemplate jdbc;
    private MemoryProperties props;
    private ConversationRepository conversations;
    private MessageCompressor compressor;
    private LettuceConnectionFactory redisFactory;
    private ReactiveStringRedisTemplate redis;
    private RecallTextCache cache;
    private ChunkRepository chunks;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        fixture = newFixture();
        jdbc = fixture.jdbc();
        props = new MemoryProperties();
        props.setNamespace("recall-cache-test-" + System.nanoTime());
        props.getStorage().setEnabled(true);
        conversations = new ConversationRepository(fixture.databaseClient(), new ObjectMapper());
        compressor = new MessageCompressor(fixture.databaseClient(), props, new SimpleMeterRegistry());
        redisFactory = new LettuceConnectionFactory(LocalRedisTestServer.configuration(MemoryTestStack.REDIS_DB));
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
        cache = new RecallTextCache(redis, props);
        chunks = new ChunkRepository(fixture.databaseClient(), conversations, cache, props);
    }

    @AfterEach
    void tearDown() {
        redisFactory.destroy();
    }

    private void turn(String c, String turnId, int ageDays, String user, String answer) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sessions WHERE id = ?", Integer.class, c) == 0) {
            jdbc.update("INSERT INTO sessions (id, user_id, tenant_id, title, created_at, updated_at) VALUES"
                    + " (?, 'alice', 't1', 'title', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))", c);
        }
        jdbc.update("INSERT INTO chat_turns (id, tenant_id, subject, conversation_id, request_hash, status,"
                + " created_at, updated_at, finished_at) VALUES (?, 't1', 'alice', ?, 'h', 'COMPLETED',"
                + " TIMESTAMPADD(DAY, -?, CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
                turnId, c, ageDays);
        long seq = jdbc.queryForObject("SELECT COALESCE(MAX(sequence), 0) FROM chat_messages WHERE conversation_id = ?",
                Long.class, c);
        int i = 0;
        for (var m : List.of(LlmClient.Message.user(user), LlmClient.Message.assistantText(answer))) {
            jdbc.update("INSERT INTO chat_messages (id, tenant_id, subject, conversation_id, turn_id, sequence, role,"
                    + " source_type, payload, created_at, updated_at) VALUES (?, 't1', 'alice', ?, ?, ?, ?, ?, ?,"
                    + " TIMESTAMPADD(DAY, -?, CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6))",
                    turnId + "-" + m.role(), c, turnId, seq + ++i, m.role(),
                    "user".equals(m.role()) ? "USER_INPUT" : "ASSISTANT", conversations.serialize(m), ageDays);
        }
    }

    private List<ChunkRepository.TurnText> recall(List<String> ids, List<String> turns) {
        return chunks.recallTexts(ALICE, ids, turns).collectList().block();
    }

    private void breakStoredBlobs() {
        jdbc.update("UPDATE chat_messages SET payload_blob = ? WHERE payload_codec IS NOT NULL",
                (Object) new byte[] {1, 2, 3});
    }

    @Test
    void recalledOriginalWarmedIntoCache() {
        turn("c1", "turn-1", 10, "旧的问题 TK-7", "旧的回答");
        turn("c1", "turn-2", 1, "新的问题", "新的回答");
        compressor.compressAll().block();

        var first = recall(List.of("c1"), List.of("turn-1", "turn-2"));
        assertThat(first).extracting(ChunkRepository.TurnText::user).containsExactlyInAnyOrder("旧的问题 TK-7", "新的问题");
        assertThat(cache.get(ALICE, List.of("c1"), List.of("turn-1", "turn-2")).block())
                .as("only compressed originals are cached").containsOnlyKeys("turn-1");
        var key = RecallTextCache.key(props.getNamespace(), ALICE, "c1");
        var ttl = redis.getExpire(key).block();
        assertThat(ttl).isPositive().isLessThanOrEqualTo(Duration.ofHours(1));

        breakStoredBlobs();
        var again = recall(List.of("c1"), List.of("turn-1"));
        assertThat(again).singleElement().satisfies(t -> {
            assertThat(t.user()).isEqualTo("旧的问题 TK-7");
            assertThat(t.answer()).isEqualTo("旧的回答");
            assertThat(t.content()).isEqualTo(first.stream().filter(x -> x.turnId().equals("turn-1")).findFirst()
                    .orElseThrow().content());
        });
    }

    @Test
    void cachedTurnIsServedOnlyWhileLiveAndInTheRequestedConversations() {
        turn("c1", "turn-1", 10, "问题", "回答");
        compressor.compressAll().block();
        recall(List.of("c1"), List.of("turn-1"));

        assertThat(recall(List.of("c2"), List.of("turn-1"))).isEmpty();
        jdbc.update("UPDATE sessions SET is_valid = 0 WHERE id = 'c1'");
        assertThat(recall(List.of("c1"), List.of("turn-1"))).as("deleted meanwhile").isEmpty();
    }

    @Test
    void cacheEntriesExpireAfterTheTtl() {
        var clock = new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-11-01T00:00:00Z"));
        var local = new RecallTextCache(null, props, new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return clock.get(); }
        });
        var turnText = new ChunkRepository.TurnText("turn-1", "c1", 1, 2, 2, "问", "答", null);
        local.put(ALICE, List.of(turnText)).block();
        assertThat(local.get(ALICE, List.of("c1"), List.of("turn-1")).block()).containsKey("turn-1");

        clock.set(clock.get().plus(Duration.ofHours(1)).plusSeconds(1));
        assertThat(local.get(ALICE, List.of("c1"), List.of("turn-1")).block()).isEmpty();
    }

    @Test
    void conversationDeleteEvicts() {
        var c = io.github.sukimiya.agileagent.util.NonBlockingIds.uuid();
        turn(c, "turn-1", 10, "问题", "回答");
        compressor.compressAll().block();
        recall(List.of(c), List.of("turn-1"));
        var hot = new io.github.sukimiya.agileagent.memory.HotMemoryCache(redis, new ObjectMapper(), props);
        var maintenance = new io.github.sukimiya.agileagent.memory.MemoryMaintenance(fixture.connectionFactory(),
                fixture.databaseClient(), hot, null, props,
                new io.github.sukimiya.agileagent.memory.MemoryMetrics(new SimpleMeterRegistry()));
        maintenance.setRecallTextCache(cache);

        maintenance.deleteConversation(ALICE, c).block();

        assertThat(cache.get(ALICE, List.of(c), List.of("turn-1")).block()).isEmpty();
    }

    @Test
    void forgettingOrDeletingAnItemEvictsItsSourceConversations() {
        turn("c1", "turn-1", 10, "我喜欢绿茶", "记住了");
        turn("c2", "turn-2", 10, "我住在杭州", "记住了");
        compressor.compressAll().block();
        recall(List.of("c1", "c2"), List.of("turn-1", "turn-2"));
        var db = fixture.databaseClient();
        var items = new MemoryItemRepository(db);
        var sources = new MemoryItemSourceRepository(db);
        var profileCache = new ProfileCache(null, props);
        var settings = new MemorySettingsService(db, profileCache);
        var service = new MemoryItemService(db, fixture.connectionFactory(), items, sources,
                new EntityGraphRepository(db, props), new ProfileService(db, items, settings, profileCache, props), props);
        service.setRecallTextCache(cache);
        var tea = MemoryItem.newItem(ALICE, null, "PREFERENCE", "喜欢绿茶", ItemStatus.ACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.8, 0.9, 1, "m", "r");
        var city = MemoryItem.newItem(ALICE, null, "FACT", "住在杭州", ItemStatus.ACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.8, 0.9, 1, "m", "r");
        items.save(tea).block();
        items.save(city).block();
        sources.saveAll(tea.id(), List.of(new SourceRef("turn-1-user", "c1", SourceType.USER_INPUT, "我喜欢绿茶")))
                .block();
        sources.saveAll(city.id(), List.of(new SourceRef("turn-2-user", "c2", SourceType.USER_INPUT, "我住在杭州")))
                .block();

        service.forget(ALICE, List.of(tea.id()), "alice", "FORGET").block();
        assertThat(cache.get(ALICE, List.of("c1", "c2"), List.of("turn-1", "turn-2")).block())
                .containsOnlyKeys("turn-2");

        service.delete(ALICE, city.id()).block();
        assertThat(cache.get(ALICE, List.of("c1", "c2"), List.of("turn-1", "turn-2")).block()).isEmpty();
    }

    @Test
    void disabledStorageReadsStraightFromTheDatabase() {
        turn("c1", "turn-1", 10, "问题", "回答");
        compressor.compressAll().block();
        props.getStorage().setEnabled(false);

        assertThat(recall(List.of("c1"), List.of("turn-1"))).singleElement()
                .satisfies(t -> assertThat(t.answer()).isEqualTo("回答"));
        assertThat(cache.get(ALICE, List.of("c1"), List.of("turn-1")).block()).isEmpty();
    }
}
