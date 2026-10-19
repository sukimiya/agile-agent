package io.github.sukimiya.medical.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

class SafetyPatternStoreTest {

    private static final Clock CLOCK =
            Clock.fixed(java.time.Instant.parse("2026-10-16T00:00:00Z"), ZoneOffset.UTC);
    private static final MedicalRuleCodec CODEC = new MedicalRuleCodec();

    private static final String CONTENT = """
            {"schemaVersion":1,"kind":"SAFETY_PATTERN",
             "condition":{"patterns":["免费义诊","[[bad"],"fieldLabel":"规则数据"},"action":{}}
            """;

    private static MedicalRuleRow rule(String id, MedicalRuleRow.ScopeType scope,
            String content, LocalDateTime validUntil) {
        return new MedicalRuleRow(id, "safety-" + id, "SAFETY_PATTERN", scope,
                scope == MedicalRuleRow.ScopeType.GLOBAL ? null : "t1",
                scope == MedicalRuleRow.ScopeType.GLOBAL ? null : "租户", null, null, null,
                content, "manual", LocalDateTime.now(CLOCK).minusDays(1), validUntil,
                MedicalRuleRow.Status.APPROVED, "admin", null);
    }

    private static SafetyPatternStore storeWith(Flux<MedicalRuleRow> rows) {
        var repo = mock(MedicalRuleRepository.class);
        when(repo.findApproved()).thenReturn(rows);
        return new SafetyPatternStore(repo, CODEC, CLOCK);
    }

    @Test
    void loadsApprovedGlobalInWindowPatternsAndSkipsBadRegex() {
        var store = storeWith(Flux.just(
                rule("g1", MedicalRuleRow.ScopeType.GLOBAL, CONTENT,
                        LocalDateTime.now(CLOCK).plusDays(1))));

        store.reload().block(Duration.ofSeconds(5));

        // "免费义诊" compiles; "[[bad" is skipped (validator should have caught it; store is
        // defense in depth — one bad pattern never poisons the rest).
        assertThat(store.current()).hasSize(1);
        assertThat(store.current().get(0).ruleCode()).isEqualTo("safety-g1");
        assertThat(store.current().get(0).fieldLabel()).isEqualTo("规则数据");
        assertThat(store.current().get(0).pattern().pattern()).isEqualTo("免费义诊");
    }

    @Test
    void ignoresTenantScopeExpiredAndUnparsableRules() {
        var store = storeWith(Flux.just(
                rule("t1", MedicalRuleRow.ScopeType.TENANT, CONTENT,
                        LocalDateTime.now(CLOCK).plusDays(1)),
                rule("g2", MedicalRuleRow.ScopeType.GLOBAL, CONTENT,
                        LocalDateTime.now(CLOCK).minusDays(1)),
                rule("g3", MedicalRuleRow.ScopeType.GLOBAL, "{ not json",
                        LocalDateTime.now(CLOCK).plusDays(1))));

        store.reload().block(Duration.ofSeconds(5));

        assertThat(store.current()).isEmpty();
    }

    @Test
    void loadFailureDegradesToBaselineOnly() {
        // Fail-safe (spec §6): a broken data source clears the increment; the code baseline
        // in TextSafetyScreen is untouched and never fails.
        var store = storeWith(Flux.error(new IllegalStateException("db down")));
        var broken = storeWith(Flux.just(
                rule("g1", MedicalRuleRow.ScopeType.GLOBAL, CONTENT,
                        LocalDateTime.now(CLOCK).plusDays(1))));
        broken.reload().block(Duration.ofSeconds(5));
        assertThat(broken.current()).hasSize(1);

        store.reload().block(Duration.ofSeconds(5));
        assertThat(store.current()).isEmpty();

        var repo = mock(MedicalRuleRepository.class);
        when(repo.findApproved()).thenThrow(new IllegalStateException("assembly blew up"));
        var throwing = new SafetyPatternStore(repo, CODEC, CLOCK);
        throwing.reload().block(Duration.ofSeconds(5));
        assertThat(throwing.current()).isEmpty();
    }

    @Test
    void reloadReplacesTheWholeSnapshot() {
        var repo = mock(MedicalRuleRepository.class);
        when(repo.findApproved()).thenReturn(Flux.just(
                rule("g1", MedicalRuleRow.ScopeType.GLOBAL, CONTENT,
                        LocalDateTime.now(CLOCK).plusDays(1))));
        var store = new SafetyPatternStore(repo, CODEC, CLOCK);
        store.reload().block(Duration.ofSeconds(5));
        assertThat(store.current()).hasSize(1);

        when(repo.findApproved()).thenReturn(Flux.empty());
        store.reload().block(Duration.ofSeconds(5));
        assertThat(store.current()).isEmpty(); // revoked rules disappear on reload
    }
}
