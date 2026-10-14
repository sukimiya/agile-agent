package io.github.sukimiya.medical.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.medical.hospital.HospitalPackageContent;
import io.github.sukimiya.medical.hospital.HospitalPackageRow;

class ProvenanceGateTest {

    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 10, 14, 0, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private final ProvenanceGate gate = new ProvenanceGate(CLOCK);

    private static HospitalPackageRow row(String source, LocalDateTime validFrom, LocalDateTime validUntil) {
        return new HospitalPackageRow("pkg-1", "demo", "演示医院", "路由描述",
                HospitalPackageRow.Status.PENDING, 1, "{}", source, validFrom, validUntil,
                HospitalPackageRow.ScopeType.GLOBAL, null, null, null, null, "seeder", null);
    }

    private List<String> evaluate(HospitalPackageRow row) {
        return gate.evaluate(new GateContext(row, new HospitalPackageContent(1, List.of()), null))
                .block(Duration.ofSeconds(5));
    }

    @Test
    void conformingProvenancePasses() {
        assertThat(gate.name()).isEqualTo("PROVENANCE");
        for (String source : List.of("manual", "import", "migration")) {
            assertThat(evaluate(row(source, LocalDateTime.of(2026, 1, 1, 0, 0),
                    LocalDateTime.of(2027, 1, 1, 0, 0)))).as("source %s", source).isEmpty();
        }
    }

    @Test
    void unknownSourceRejected() {
        assertThat(evaluate(row("hospital contract 2026-09",
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 0, 0))))
                .anyMatch(v -> v.contains("source must be one of"));
    }

    @Test
    void expiredValidUntilRejectedWithBoundaryPassing() {
        assertThat(evaluate(row("manual", LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 10, 13, 23, 59))))
                .anyMatch(v -> v.contains("能力包有效期已过"));
        // One minute past the fixed clock: still publishable.
        assertThat(evaluate(row("manual", LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 10, 14, 0, 1)))).isEmpty();
    }

    @Test
    void staleValidFromRejectedWithBoundaryPassing() {
        assertThat(evaluate(row("manual", LocalDateTime.of(2025, 10, 13, 0, 0),
                LocalDateTime.of(2027, 1, 1, 0, 0))))
                .anyMatch(v -> v.contains("365"));
        // Exactly 365 days old: not stale.
        assertThat(evaluate(row("manual", LocalDateTime.of(2025, 10, 14, 0, 0),
                LocalDateTime.of(2027, 1, 1, 0, 0)))).isEmpty();
    }

    @Test
    void nullValidUntilRejected() {
        assertThat(evaluate(row("manual", LocalDateTime.of(2026, 1, 1, 0, 0), null)))
                .anyMatch(v -> v.contains("能力包有效期已过"));
    }
}
