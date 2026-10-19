package io.github.sukimiya.medical.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.hospital.HospitalPackageCodec;
import io.github.sukimiya.medical.hospital.HospitalPackageContent;
import io.github.sukimiya.medical.hospital.HospitalPackageRow;
import io.github.sukimiya.medical.hospital.HospitalToolSpec;
import io.github.sukimiya.medical.hospital.TransportBinding;
import io.github.sukimiya.medical.hospital.UnifiedToolCatalog;
import reactor.core.publisher.Mono;

class GatePipelineTest {

    private static final HospitalPackageCodec CODEC = new HospitalPackageCodec();
    private static final List<String> SIX =
            List.of("SCHEMA", "SEMANTIC", "PROVENANCE", "CAPABILITY", "SAFETY", "SIMULATION");

    /** Records evaluation order and emits a fixed violation list — pipeline mechanics only. */
    private static final class FakeGate implements PackageGate {
        private final String name;
        private final List<String> violations;
        private final List<String> orderLog;

        FakeGate(String name, List<String> violations, List<String> orderLog) {
            this.name = name;
            this.violations = violations;
            this.orderLog = orderLog;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Mono<List<String>> evaluate(GateContext context) {
            orderLog.add(name);
            return Mono.just(violations);
        }
    }

    private static GatePipeline pipeline(List<String> orderLog, Set<String> redGates) {
        List<PackageGate> gates = SIX.stream()
                .<PackageGate>map(name -> new FakeGate(name,
                        redGates.contains(name) ? List.of(name + " violation") : List.of(),
                        orderLog))
                .toList();
        return new GatePipeline(gates);
    }

    private static HospitalPackageRow row(String content) {
        return new HospitalPackageRow("pkg-1", "demo", "演示医院", "路由描述",
                HospitalPackageRow.Status.PENDING, 1, content, "manual",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                HospitalPackageRow.ScopeType.GLOBAL, null, null, null, null, "admin-1", null);
    }

    private static String validContent() {
        ObjectMapper json = new ObjectMapper();
        ObjectNode schema = json.createObjectNode().put("type", "object");
        schema.set("properties", json.createObjectNode());
        return CODEC.write(new HospitalPackageContent(1, UnifiedToolCatalog.SCHEMA_V1_TOOLS.stream()
                .map(name -> new HospitalToolSpec(name, "描述-" + name, schema,
                        TransportBinding.api("https://api.mock.example/unified")))
                .toList()));
    }

    private static GateContext context(String content) {
        var identity = new IdentityContext("admin-1", "管理员一", null, null, null, null,
                Set.of("PLATFORM_ADMIN"), Set.of(), Set.of(), true, false);
        return new GateContext(row(content), CODEC.parse(content).orElse(null), identity);
    }

    @Test
    void runsAllSixInOrderAndCollectsViolations() {
        List<String> orderLog = new ArrayList<>();
        var pipeline = pipeline(orderLog, Set.of("SEMANTIC", "SAFETY"));

        var report = pipeline.run(context(validContent()), 7L).block(Duration.ofSeconds(5));

        assertThat(orderLog).containsExactlyElementsOf(SIX);
        assertThat(report.hospitalCode()).isEqualTo("demo");
        assertThat(report.approvalVersion()).isEqualTo(7L);
        assertThat(report.passed()).isFalse();
        assertThat(report.violations()).containsOnlyKeys(
                "SCHEMA", "SEMANTIC", "PROVENANCE", "CAPABILITY", "SAFETY", "SIMULATION");
        assertThat(report.violations().get("SEMANTIC")).containsExactly("SEMANTIC violation");
        assertThat(report.violations().get("SAFETY")).containsExactly("SAFETY violation");
        assertThat(report.violations().get("SCHEMA")).isEmpty();
        assertThat(report.skippedGates()).isEmpty();
    }

    @Test
    void unparsableContentSkipsEverythingAfterSchema() {
        List<String> orderLog = new ArrayList<>();
        var pipeline = pipeline(orderLog, Set.of("SCHEMA"));

        var report = pipeline.run(context("{ not json"), 3L).block(Duration.ofSeconds(5));

        assertThat(orderLog).containsExactly("SCHEMA");
        assertThat(report.skippedGates()).containsExactly(
                "SEMANTIC", "PROVENANCE", "CAPABILITY", "SAFETY", "SIMULATION");
        assertThat(report.violations().get("SCHEMA")).containsExactly("SCHEMA violation");
        assertThat(report.passed()).isFalse();
    }

    @Test
    void capabilityViolationsSkipSimulationOnly() {
        List<String> orderLog = new ArrayList<>();
        var pipeline = pipeline(orderLog, Set.of("CAPABILITY"));

        var report = pipeline.run(context(validContent()), 0L).block(Duration.ofSeconds(5));

        assertThat(orderLog).containsExactly(
                "SCHEMA", "SEMANTIC", "PROVENANCE", "CAPABILITY", "SAFETY");
        assertThat(report.skippedGates()).containsExactly("SIMULATION");
        assertThat(report.violations().get("SIMULATION")).isEmpty();
        assertThat(report.violations().get("CAPABILITY")).containsExactly("CAPABILITY violation");
        assertThat(report.passed()).isFalse();
    }

    @Test
    void greenRunProducesPassedReportWithVersionAndJsonRoundTrip() {
        List<String> orderLog = new ArrayList<>();
        var pipeline = pipeline(orderLog, Set.of());

        var report = pipeline.run(context(validContent()), 12L).block(Duration.ofSeconds(5));

        assertThat(orderLog).containsExactlyElementsOf(SIX);
        assertThat(report.passed()).isTrue();
        assertThat(report.skippedGates()).isEmpty();
        assertThat(report.approvalVersion()).isEqualTo(12L);

        var restored = GateReport.fromJson(report.toJson()).orElseThrow();
        assertThat(restored.hospitalCode()).isEqualTo("demo");
        assertThat(restored.approvalVersion()).isEqualTo(12L);
        assertThat(restored.passed()).isTrue();
        assertThat(restored.violations()).containsOnlyKeys(
                "SCHEMA", "SEMANTIC", "PROVENANCE", "CAPABILITY", "SAFETY", "SIMULATION");
        assertThat(restored.skippedGates()).isEmpty();
    }

    @Test
    void runIsResubscriptionSafe() {
        // Plan 3 final-review absorption: run() must be a cold pipeline — blocking the same
        // Mono twice yields two identical, independent reports instead of accumulating into
        // assembly-time shared state (skippedGates would grow to 10 entries pre-fix).
        List<String> orderLog = new ArrayList<>();
        var pipeline = pipeline(orderLog, Set.of("SCHEMA"));
        var run = pipeline.run(context("{ not json"), 3L);

        var first = run.block(Duration.ofSeconds(5));
        var second = run.block(Duration.ofSeconds(5));

        assertThat(orderLog).containsExactly("SCHEMA", "SCHEMA");
        assertThat(first.skippedGates()).containsExactly(
                "SEMANTIC", "PROVENANCE", "CAPABILITY", "SAFETY", "SIMULATION");
        assertThat(second.skippedGates()).containsExactly(
                "SEMANTIC", "PROVENANCE", "CAPABILITY", "SAFETY", "SIMULATION");
        assertThat(first.violations().get("SCHEMA")).containsExactly("SCHEMA violation");
        assertThat(second.violations().get("SCHEMA")).containsExactly("SCHEMA violation");
    }
}
