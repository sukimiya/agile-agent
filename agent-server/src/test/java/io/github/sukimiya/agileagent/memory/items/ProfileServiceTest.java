package io.github.sukimiya.agileagent.memory.items;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.memory.context.TokenEstimator;
import io.github.sukimiya.agileagent.memory.judgement.ValidatedAction.SourceRef;
import io.github.sukimiya.agileagent.memory.jobs.JobType;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobQueue;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.memory.jobs.ConsolidationQuota;
import io.github.sukimiya.agileagent.memory.MemoryMetrics;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.github.sukimiya.agileagent.util.NonBlockingIds;

class ProfileServiceTest {

    static final Duration WAIT = Duration.ofSeconds(15);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;

    MemoryTestFixture fixture;
    MemoryProperties props;
    MemoryItemRepository items;
    MemoryItemSourceRepository sources;
    MemorySettingsService settings;
    ProfileCache cache;
    ProfileService profiles;
    FixedClockHolder clockHolder;

    /** Mutable fixed clock for cache TTL tests. */
    static final class FixedClockHolder {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-27T00:00:00Z"));
        Clock clock() {
            return new Clock() {
                @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(java.time.ZoneId zone) { return this; }
                @Override public Instant instant() { return now.get(); }
            };
        }
        void advance(Duration d) { now.updateAndGet(i -> i.plus(d)); }
    }

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        fixture = newFixture();
        props = new MemoryProperties();
        props.getProfile().setMaxTokens(800);
        props.getProfile().setCacheTtl(Duration.ofHours(1));
        items = new MemoryItemRepository(fixture.databaseClient());
        sources = new MemoryItemSourceRepository(fixture.databaseClient());
        clockHolder = new FixedClockHolder();
        cache = new ProfileCache(null, props, clockHolder.clock());
        settings = new MemorySettingsService(fixture.databaseClient(), cache);
        profiles = new ProfileService(fixture.databaseClient(), items, settings, cache, props);
    }

    private void enableCross() {
        settings.update(ALICE, true).block(WAIT);
    }

    private MemoryItem saveUserItem(String type, String content) {
        var item = MemoryItem.newItem(ALICE, null, type, content, ItemStatus.ACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.8, 0.9, 1, "m", "r");
        items.save(item).block(WAIT);
        return item;
    }

    @Test
    void profileOnlyBuiltWhenCrossConversationEnabled() {
        saveUserItem("PREFERENCE", "喜欢中文");
        var off = profiles.rebuild(ALICE).block(WAIT);
        assertThat(off.content()).isEmpty();
        assertThat(profiles.current(ALICE).block(WAIT)).isEmpty();

        enableCross();
        var on = profiles.rebuild(ALICE).block(WAIT);
        assertThat(on.content()).contains("喜欢中文");
        assertThat(profiles.current(ALICE).block(WAIT)).isPresent();
    }

    @Test
    void enablingDoesNotScanPastConversations() {
        // Session-scoped items from before enable must not enter the user profile.
        var sessionItem = MemoryItem.newItem(ALICE, NonBlockingIds.uuid(), "FACT", "仅会话事实", ItemStatus.ACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.8, 0.9, 1, "m", "r");
        items.save(sessionItem).block(WAIT);
        enableCross();
        var profile = profiles.rebuild(ALICE).block(WAIT);
        assertThat(profile.content()).doesNotContain("仅会话事实");
        assertThat(profile.builtThrough()).isZero();
    }

    @Test
    void disablingStopsUserLevelReadAndWriteButKeepsData() {
        enableCross();
        saveUserItem("PREFERENCE", "偏好绿茶");
        profiles.rebuild(ALICE).block(WAIT);
        assertThat(profiles.current(ALICE).block(WAIT)).isPresent();

        settings.update(ALICE, false).block(WAIT);
        assertThat(profiles.current(ALICE).block(WAIT)).isEmpty();
        var kept = fixture.jdbc().queryForObject(
                "SELECT content FROM user_memory_profiles WHERE tenant_id = ? AND subject = ?", String.class,
                ALICE.tenantId(), ALICE.subject());
        assertThat(kept).contains("偏好绿茶");
        var after = profiles.rebuild(ALICE).block(WAIT);
        assertThat(after.content()).isEmpty();
        assertThat(fixture.jdbc().queryForObject(
                "SELECT content FROM user_memory_profiles WHERE tenant_id = ? AND subject = ?", String.class,
                ALICE.tenantId(), ALICE.subject())).contains("偏好绿茶");
    }

    @Test
    void profileRespectsLengthBudget() {
        enableCross();
        props.getProfile().setMaxTokens(40);
        for (int i = 0; i < 30; i++) {
            saveUserItem("FACT", "事实条目编号" + i + "：" + "内容".repeat(20));
        }
        var profile = profiles.rebuild(ALICE).block(WAIT);
        assertThat(TokenEstimator.estimate(profile.content())).isLessThanOrEqualTo(40);
    }

    @Test
    void profileCacheTtlOneHourWithControlledClock() {
        enableCross();
        saveUserItem("PREFERENCE", "缓存偏好");
        var built = profiles.rebuild(ALICE).block(WAIT);
        assertThat(cache.get(ALICE, built.version()).block(WAIT)).isNotNull();
        clockHolder.advance(Duration.ofMinutes(59));
        assertThat(cache.get(ALICE, built.version()).block(WAIT)).isNotNull();
        clockHolder.advance(Duration.ofMinutes(2));
        assertThat(cache.get(ALICE, built.version()).block(WAIT)).isNull();
    }

    @Test
    void profileVersionChangeInvalidatesCacheImmediately() {
        enableCross();
        saveUserItem("PREFERENCE", "v1");
        var v1 = profiles.rebuild(ALICE).block(WAIT);
        assertThat(cache.get(ALICE, v1.version()).block(WAIT).content()).contains("v1");
        saveUserItem("PREFERENCE", "v2");
        var v2 = profiles.rebuild(ALICE).block(WAIT);
        assertThat(v2.version()).isGreaterThan(v1.version());
        assertThat(cache.get(ALICE, v1.version()).block(WAIT)).isNull();
        assertThat(cache.get(ALICE, v2.version()).block(WAIT).content()).contains("v2");
    }

    @Test
    void deleteAndRevokeInvalidateProfileCache() throws Exception {
        enableCross();
        saveUserItem("PREFERENCE", "将被失效");
        var built = profiles.rebuild(ALICE).block(WAIT);
        assertThat(cache.get(ALICE, built.version()).block(WAIT)).isNotNull();

        settings.update(ALICE, false).block(WAIT); // revoke → invalidate
        assertThat(cache.get(ALICE, built.version()).block(WAIT)).isNull();

        settings.update(ALICE, true).block(WAIT);
        cache.put(built).block(WAIT);
        assertThat(cache.get(ALICE, built.version()).block(WAIT)).isNotNull();

        try (var stack = MemoryTestStack.of(fixture)) {
            var uploads = new io.github.sukimiya.agileagent.config.UploadProperties();
            uploads.setDir(java.nio.file.Files.createTempDirectory("prof-del").toString());
            var maintenance = new io.github.sukimiya.agileagent.memory.MemoryMaintenance(
                    fixture.connectionFactory(), fixture.databaseClient(), stack.cache(),
                    new io.github.sukimiya.agileagent.service.FileStorageService(uploads), props,
                    new MemoryMetrics(new SimpleMeterRegistry()),
                    new MemoryJobWorker(fixture.databaseClient(), () -> false), cache);
            var c = NonBlockingIds.uuid();
            fixture.jdbc().update("INSERT INTO sessions (id, tenant_id, user_id, title, revision, created_at, updated_at)"
                    + " VALUES (?, ?, ?, 't', 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
                    c, ALICE.tenantId(), ALICE.subject());
            maintenance.deleteConversation(ALICE, c).block(WAIT);
        }
        assertThat(cache.get(ALICE, built.version()).block(WAIT)).isNull();
    }

    @Test
    void deletedSourcesNotResurrectedByRebuild() {
        enableCross();
        var live = saveUserItem("FACT", "仍然有效");
        var dead = MemoryItem.newItem(ALICE, null, "FACT", "源已删除", ItemStatus.INACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.8, 0.9, 1, "m", "r");
        items.save(dead).block(WAIT);
        var profile = profiles.rebuild(ALICE).block(WAIT);
        assertThat(profile.content()).contains("仍然有效").doesNotContain("源已删除");
        assertThat(live.id()).isNotBlank();
    }

    @Test
    void rebuildProfileJobRuns() {
        enableCross();
        saveUserItem("PREFERENCE", "任务重建");
        var metrics = new MemoryMetrics(new SimpleMeterRegistry());
        var quota = new ConsolidationQuota(fixture.databaseClient(), props, metrics);
        var worker = new MemoryJobWorker(fixture.databaseClient(), () -> false, quota, props, metrics);
        new RebuildProfileJobHandler(profiles, worker, quota, props);
        var queue = new MemoryJobQueue(fixture.databaseClient());
        queue.enqueue(ALICE, null, JobType.REBUILD_PROFILE, RebuildProfileJobHandler.dedupeKey(ALICE), "{}")
                .block(WAIT);
        assertThat(worker.runPending(5).block(WAIT)).isEqualTo(1);
        assertThat(profiles.current(ALICE).block(WAIT).orElseThrow().content()).contains("任务重建");
    }
}
