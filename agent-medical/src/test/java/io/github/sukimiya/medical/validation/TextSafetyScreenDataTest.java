package io.github.sukimiya.medical.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.medical.rules.MedicalRuleCodec;
import io.github.sukimiya.medical.rules.MedicalRuleRepository;
import io.github.sukimiya.medical.rules.MedicalRuleRow;
import io.github.sukimiya.medical.rules.SafetyPatternStore;
import reactor.core.publisher.Flux;

class TextSafetyScreenDataTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-16T00:00:00Z"), ZoneOffset.UTC);

    private static TextSafetyScreen compositeScreen(String patternsJsonArray) {
        var repo = mock(MedicalRuleRepository.class);
        var content = "{\"schemaVersion\":1,\"kind\":\"SAFETY_PATTERN\",\"condition\":"
                + "{\"patterns\":" + patternsJsonArray + ",\"fieldLabel\":\"规则数据\"},"
                + "\"action\":{}}";
        var row = new MedicalRuleRow("r-1", "safety-1", "SAFETY_PATTERN",
                MedicalRuleRow.ScopeType.GLOBAL, null, null, null, null, null, content,
                "manual", LocalDateTime.now(CLOCK).minusDays(1),
                LocalDateTime.now(CLOCK).plusDays(1), MedicalRuleRow.Status.APPROVED,
                "admin", null);
        when(repo.findApproved()).thenReturn(Flux.just(row));
        var store = new SafetyPatternStore(repo, new MedicalRuleCodec(), CLOCK);
        store.reload().block(Duration.ofSeconds(5));
        return new TextSafetyScreen(store);
    }

    @Test
    void dataIncrementFlagsWhatTheBaselineMisses() {
        var screen = compositeScreen("[\"免费义诊\"]");

        assertThat(screen.screen("hospital_name", "本院提供免费义诊活动"))
                .containsExactly("hospital_name: 命中数据驱动安全模式（规则数据）");
        assertThat(screen.screen("hospital_name", "正常名称")).isEmpty();
    }

    @Test
    void baselineStillFiresAlongsideDataIncrement() {
        var screen = compositeScreen("[\"免费义诊\"]");

        var violations = screen.screen("description", "包治百病，另有免费义诊");
        assertThat(violations).contains("description: 疑似医疗广告法禁用声明")
                .contains("description: 命中数据驱动安全模式（规则数据）");
    }

    @Test
    void oneViolationPerMatchedDataRule() {
        var screen = compositeScreen("[\"免费义诊\",\"义诊\"]");

        assertThat(screen.screen("description", "免费义诊")).hasSize(2);
    }

    @Test
    void noArgConstructorStaysBaselineOnly() {
        var baseline = new TextSafetyScreen();

        assertThat(baseline.screen("description", "免费义诊")).isEmpty();
        assertThat(baseline.screen("description", "包治百病"))
                .containsExactly("description: 疑似医疗广告法禁用声明");
    }
}
