package io.github.sukimiya.agileagent.memory.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryException;
import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.memory.SourceType;
import io.github.sukimiya.agileagent.memory.judgement.ValidatedAction.SourceRef;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService;
import io.github.sukimiya.agileagent.util.NonBlockingIds;

/**
 * The user memory API's service layer (M2b Task 5, D15=A, D16=A). H2 here; {@link MemoryItemMySqlIntegrationTest}
 * on MySQL.
 */
class MemoryItemServiceTest {

    static final Duration WAIT = Duration.ofSeconds(15);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;
    static final MemoryScope BOB = new MemoryScope(ALICE.tenantId(), "bob");

    MemoryTestFixture fixture;
    MemoryItemRepository items;
    MemoryItemSourceRepository sources;
    MemorySettingsService settings;
    ProfileCache cache;
    ProfileService profiles;
    EntityGraphRepository graph;
    MemoryItemService service;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        fixture = newFixture();
        var props = new MemoryProperties();
        var db = fixture.databaseClient();
        items = new MemoryItemRepository(db);
        sources = new MemoryItemSourceRepository(db);
        cache = new ProfileCache(null, props);
        settings = new MemorySettingsService(db, cache);
        graph = new EntityGraphRepository(db);
        profiles = new ProfileService(db, items, settings, cache, props);
        service = new MemoryItemService(db, fixture.connectionFactory(), items, sources, graph, profiles, props);
    }

    MemoryItem save(MemoryScope scope, String content) {
        var item = MemoryItem.newItem(scope, null, "PREFERENCE", content, ItemStatus.ACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.8, 0.9, 1, "m", "r");
        items.save(item).block(WAIT);
        return item;
    }

    static ErrorCode code(Throwable e) {
        return ((MemoryException) e).code();
    }

    @Test
    void listAndDetailIncludeSourcesAndEvidence() {
        var a = save(ALICE, "喜欢绿茶");
        var b = save(ALICE, "喜欢中文");
        var conv = NonBlockingIds.uuid();
        sources.saveAll(a.id(), List.of(new SourceRef("msg-1", conv, SourceType.USER_INPUT, "我喜欢绿茶")))
                .block(WAIT);
        save(BOB, "鲍勃的条目");

        var page = service.list(ALICE, null, 1, null).block(WAIT);
        assertThat(page.items()).hasSize(1);
        assertThat(page.nextBefore()).isNotNull();
        var rest = service.list(ALICE, null, 10, page.nextBefore()).block(WAIT);
        assertThat(rest.items()).hasSize(1);
        assertThat(rest.nextBefore()).isNull();
        assertThat(List.of(page.items().getFirst().id(), rest.items().getFirst().id()))
                .containsExactlyInAnyOrder(a.id(), b.id());

        assertThat(service.list(ALICE, ItemStatus.SUPERSEDED, 10, null).block(WAIT).items()).isEmpty();

        var detail = service.detail(ALICE, a.id()).block(WAIT);
        assertThat(detail.item().content()).isEqualTo("喜欢绿茶");
        assertThat(detail.sources()).singleElement().satisfies(s -> {
            assertThat(s.messageId()).isEqualTo("msg-1");
            assertThat(s.evidenceExcerpt()).isEqualTo("我喜欢绿茶");
            assertThat(s.sourceType()).isEqualTo(SourceType.USER_INPUT);
        });
    }

    @Test
    void otherUsersItemsNotFound() {
        var a = save(ALICE, "爱丽丝的条目");
        assertThatThrownBy(() -> service.detail(BOB, a.id()).block(WAIT))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.MEMORY_ITEM_NOT_FOUND));
        assertThatThrownBy(() -> service.correct(BOB, a.id(), "改", null).block(WAIT))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.MEMORY_ITEM_NOT_FOUND));
        assertThatThrownBy(() -> service.delete(BOB, a.id()).block(WAIT))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.MEMORY_ITEM_NOT_FOUND));
        assertThat(service.detail(ALICE, a.id()).block(WAIT).item().status()).isEqualTo(ItemStatus.ACTIVE);
    }

    @Test
    void correctionCreatesConfirmedItemAndSupersedes() {
        var old = save(ALICE, "住在上海");
        var created = service.correct(ALICE, old.id(), "住在杭州", old.revision()).block(WAIT);

        assertThat(created.id()).isNotEqualTo(old.id());
        assertThat(created.assertion()).isEqualTo(MemoryItem.Assertion.USER_CONFIRMED);
        assertThat(created.status()).isEqualTo(ItemStatus.ACTIVE);
        assertThat(created.content()).isEqualTo("住在杭州");
        var oldNow = service.detail(ALICE, old.id()).block(WAIT).item();
        assertThat(oldNow.status()).isEqualTo(ItemStatus.SUPERSEDED);
        assertThat(oldNow.supersededBy()).isEqualTo(created.id());
        assertThat(oldNow.content()).isEqualTo("住在上海");

        // Superseded items cannot be corrected again; a stale revision loses.
        assertThatThrownBy(() -> service.correct(ALICE, old.id(), "住在北京", null).block(WAIT))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.MEMORY_ITEM_CONFLICT));
        assertThatThrownBy(() -> service.correct(ALICE, created.id(), "住在北京", 99L).block(WAIT))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.MEMORY_ITEM_CONFLICT));
        // F3 applies to user corrections too.
        assertThatThrownBy(() -> service.correct(ALICE, created.id(), "手机 13812345678", null).block(WAIT))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.correct(ALICE, created.id(), " ", null).block(WAIT))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void deletedItemNeverRecalledOrRebuilt() {
        settings.update(ALICE, true).block(WAIT);
        var keep = save(ALICE, "喜欢中文");
        var gone = save(ALICE, "喜欢绿茶");
        var e1 = graph.upsertEntity(ALICE, "用户", "PERSON").block(WAIT);
        var e2 = graph.upsertEntity(ALICE, "绿茶", "OTHER").block(WAIT);
        graph.saveRelation(ALICE, e1.id(), "LIKES", e2.id(), gone.id(), "ACTIVE").block(WAIT);
        profiles.rebuild(ALICE).block(WAIT);
        assertThat(profiles.current(ALICE).block(WAIT).orElseThrow().content()).contains("喜欢绿茶");

        service.delete(ALICE, gone.id()).block(WAIT);

        assertThatThrownBy(() -> service.detail(ALICE, gone.id()).block(WAIT))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.MEMORY_ITEM_NOT_FOUND));
        assertThat(items.searchActive(ALICE, null, List.of("绿茶"), 10).collectList().block(WAIT)).isEmpty();
        assertThat(graph.oneHop(ALICE, List.of("用户"), 10).collectList().block(WAIT)).isEmpty();
        assertThat(profiles.current(ALICE).block(WAIT).orElseThrow().content()).contains("喜欢中文")
                .doesNotContain("喜欢绿茶");
        assertThat(profiles.rebuild(ALICE).block(WAIT).content()).doesNotContain("喜欢绿茶");
        // Soft delete: the row and its text stay in the database, invalid.
        assertThat(fixture.jdbc().queryForMap("SELECT content, status, is_valid FROM memory_items WHERE id = ?",
                gone.id())).containsEntry("CONTENT", "喜欢绿茶").containsEntry("STATUS", "DELETED")
                .containsEntry("IS_VALID", 0);
        assertThat(keep.id()).isNotBlank();

        // With the switch off, a kept snapshot is still purged of the deleted content (no new aggregation).
        var gone2 = save(ALICE, "喜欢咖啡");
        profiles.rebuild(ALICE).block(WAIT);
        settings.update(ALICE, false).block(WAIT);
        service.delete(ALICE, gone2.id()).block(WAIT);
        assertThat(fixture.jdbc().queryForObject(
                "SELECT content FROM user_memory_profiles WHERE tenant_id = ? AND subject = ?", String.class,
                ALICE.tenantId(), ALICE.subject())).doesNotContain("喜欢咖啡").contains("喜欢中文");
    }

    @Test
    void deleteProfileRemovesSnapshotAndGraph() {
        settings.update(ALICE, true).block(WAIT);
        var item = save(ALICE, "喜欢中文");
        var e1 = graph.upsertEntity(ALICE, "用户", "PERSON").block(WAIT);
        var e2 = graph.upsertEntity(ALICE, "中文", "OTHER").block(WAIT);
        graph.saveRelation(ALICE, e1.id(), "PREFERS", e2.id(), null, "ACTIVE").block(WAIT);
        var bobEntity = graph.upsertEntity(BOB, "鲍勃", "PERSON").block(WAIT);
        var built = profiles.rebuild(ALICE).block(WAIT);
        assertThat(cache.get(ALICE, built.version()).block(WAIT)).isNotNull();

        service.deleteProfile(ALICE).block(WAIT);

        // Soft delete: rows stay, invalid.
        for (var table : java.util.List.of("user_memory_profiles", "memory_entities", "memory_relations")) {
            assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM " + table
                    + " WHERE tenant_id = ? AND subject = ? AND is_valid = 1", Long.class, ALICE.tenantId(),
                    ALICE.subject())).as(table).isZero();
            assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM " + table
                    + " WHERE tenant_id = ? AND subject = ?", Long.class, ALICE.tenantId(), ALICE.subject()))
                    .as(table).isPositive();
        }
        assertThat(graph.findById(ALICE, e1.id()).blockOptional()).isEmpty();
        assertThat(graph.oneHop(ALICE, List.of("用户"), 10).collectList().block(WAIT)).isEmpty();
        assertThat(cache.get(ALICE, built.version()).block(WAIT)).isNull();
        assertThat(profiles.current(ALICE).block(WAIT)).isEmpty();
        // Items and other users' graphs stay.
        assertThat(service.detail(ALICE, item.id()).block(WAIT).item().status()).isEqualTo(ItemStatus.ACTIVE);
        assertThat(graph.findById(BOB, bobEntity.id()).block(WAIT)).isNotNull();

        // A later rebuild and graph write bring back fresh, valid rows.
        var rebuilt = profiles.rebuild(ALICE).block(WAIT);
        assertThat(rebuilt.version()).isGreaterThan(built.version());
        assertThat(profiles.current(ALICE).block(WAIT).orElseThrow().content()).contains("喜欢中文");
        var again = graph.upsertEntity(ALICE, "用户", "PERSON").block(WAIT);
        assertThat(graph.findById(ALICE, again.id()).block(WAIT)).isNotNull();
    }

    void references(MemoryScope scope, String itemId, int n) {
        for (int i = 0; i < n; i++) {
            fixture.jdbc().update("INSERT INTO memory_recall_events (id, tenant_id, subject, conversation_id, turn_id,"
                    + " kind, ref_id, reason, score, `rank`, source_types, created_at) VALUES (?, ?, ?, 'c', ?, 'ITEM',"
                    + " ?, 'ITEM', 1, 1, '', CURRENT_TIMESTAMP(6))", NonBlockingIds.uuid(), scope.tenantId(),
                    scope.subject(), NonBlockingIds.uuid(), itemId);
        }
    }

    @Test
    void decrementRecallCountFloorsAtZeroAndIsAudited() {
        var item = save(ALICE, "喜欢绿茶");
        references(ALICE, item.id(), 3);
        assertThat(service.recallCount(ALICE, item.id()).block(WAIT).count()).isEqualTo(3);

        var once = service.decrementRecallCount(ALICE, item.id(), 1, "alice").block(WAIT);
        assertThat(once.count()).isEqualTo(2);
        assertThat(once.retention()).isEqualTo(Duration.ofDays(7));
        assertThat(once.purgeEligibleAt()).as("source not deleted: not purge-eligible").isNull();

        var floored = service.decrementRecallCount(ALICE, item.id(), 10, "alice").block(WAIT);
        assertThat(floored.count()).isZero();
        assertThat(floored.retention()).isEqualTo(Duration.ofDays(1));
        // New recalls count again from the floor.
        references(ALICE, item.id(), 2);
        assertThat(service.recallCount(ALICE, item.id()).block(WAIT).count()).isEqualTo(2);

        var audit = fixture.jdbc().queryForList("SELECT actor, action, target_id, amount, count_before, count_after"
                + " FROM memory_audit_log WHERE tenant_id = ? AND subject = ? ORDER BY created_at, id",
                ALICE.tenantId(), ALICE.subject());
        assertThat(audit).hasSize(2);
        assertThat(audit.get(0)).containsEntry("ACTOR", "alice").containsEntry("ACTION", "RECALL_COUNT_DECREMENT")
                .containsEntry("TARGET_ID", item.id());
        assertThat(((Number) audit.get(0).get("COUNT_BEFORE")).longValue()).isEqualTo(3);
        assertThat(((Number) audit.get(0).get("COUNT_AFTER")).longValue()).isEqualTo(2);
        assertThat(((Number) audit.get(1).get("AMOUNT")).longValue()).isEqualTo(10);
        assertThat(((Number) audit.get(1).get("COUNT_AFTER")).longValue()).isZero();

        assertThatThrownBy(() -> service.decrementRecallCount(ALICE, item.id(), 0, "alice").block(WAIT))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.decrementRecallCount(BOB, item.id(), 1, "bob").block(WAIT))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.MEMORY_ITEM_NOT_FOUND));
        assertThatThrownBy(() -> service.decrementRecallCount(ALICE, "missing", 1, "alice").block(WAIT))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.MEMORY_ITEM_NOT_FOUND));
    }
}
