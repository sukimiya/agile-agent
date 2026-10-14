package io.github.sukimiya.medical.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.hospital.HospitalPackageApprovalState;
import io.github.sukimiya.medical.hospital.HospitalPackageCodec;
import io.github.sukimiya.medical.hospital.HospitalPackageContent;
import io.github.sukimiya.medical.hospital.HospitalPackageRepository;
import io.github.sukimiya.medical.hospital.HospitalPackageRow;
import io.github.sukimiya.medical.hospital.HospitalToolSpec;
import io.github.sukimiya.medical.hospital.TransportBinding;
import io.github.sukimiya.medical.hospital.UnifiedToolCatalog;
import io.github.sukimiya.medical.validation.GateContext;
import io.github.sukimiya.medical.validation.GatePipeline;
import io.github.sukimiya.medical.validation.GateReport;
import io.github.sukimiya.medical.validation.PackageGate;
import reactor.core.publisher.Mono;

class HospitalPackageLifecycleServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HospitalPackageCodec CODEC = new HospitalPackageCodec();
    private static final List<String> SIX =
            List.of("SCHEMA", "SEMANTIC", "PROVENANCE", "CAPABILITY", "SAFETY", "SIMULATION");

    private static final IdentityContext ADMIN = new IdentityContext("admin-1", "管理员一",
            null, null, null, null, Set.of("PLATFORM_ADMIN"), Set.of(), Set.of(), true, false);
    private static final IdentityContext TENANT_ADMIN = new IdentityContext("tenant-admin-1",
            "租户管理员", "tenant-1", "租户一", null, null, Set.of("TENANT_ADMIN"),
            Set.of(), Set.of(), false, true);
    private static final IdentityContext USER = new IdentityContext("user-1", "用户一",
            "tenant-1", "租户一", null, null, Set.of("USER"), Set.of(), Set.of(), false, false);

    private final HospitalPackageRepository packages = mock(HospitalPackageRepository.class);

    private static GatePipeline greenPipeline() {
        return pipeline(Set.of());
    }

    private static GatePipeline pipeline(Set<String> redGates) {
        return new GatePipeline(SIX.stream()
                .map(name -> (PackageGate) new PackageGate() {
                    @Override
                    public String name() {
                        return name;
                    }

                    @Override
                    public Mono<List<String>> evaluate(GateContext context) {
                        return Mono.just(redGates.contains(name) ? List.of(name + " violation")
                                : List.of());
                    }
                })
                .toList());
    }

    private HospitalPackageLifecycleService serviceWith(GatePipeline pipeline) {
        return new HospitalPackageLifecycleService(packages, pipeline, CODEC);
    }

    private static ObjectNode objectSchema() {
        ObjectNode schema = JSON.createObjectNode().put("type", "object");
        schema.set("properties", JSON.createObjectNode());
        return schema;
    }

    private static String content(String url) {
        return CODEC.write(new HospitalPackageContent(1, UnifiedToolCatalog.SCHEMA_V1_TOOLS.stream()
                .map(name -> new HospitalToolSpec(name, "描述-" + name, objectSchema(),
                        TransportBinding.api(url)))
                .toList()));
    }

    private static HospitalPackageRow draft(String id, String code, String url,
            HospitalPackageRow.ScopeType scope, String tenantId, String tenantName) {
        return new HospitalPackageRow(id, code, "演示医院", "路由描述",
                HospitalPackageRow.Status.APPROVED, 1, content(url), "manual",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                scope, tenantId, tenantName, null, null, "someone-else", null);
    }

    private static HospitalPackageRow storedRow(String id, String content) {
        return new HospitalPackageRow(id, "demo", "演示医院", "路由描述",
                HospitalPackageRow.Status.PENDING, 1, content, "manual",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                HospitalPackageRow.ScopeType.GLOBAL, null, null, null, null, "admin-1",
                LocalDateTime.now());
    }

    private static HospitalPackageApprovalState state(long version,
            HospitalPackageRow.Status status, String lastGateReport) {
        return new HospitalPackageApprovalState("pkg-1", status, version, null, null, null,
                lastGateReport);
    }

    private static HospitalPackageApprovalState reviewedState(long version,
            HospitalPackageRow.Status status) {
        return new HospitalPackageApprovalState("pkg-1", status, version, "admin-1",
                LocalDateTime.now(), null, greenReportJson(version));
    }

    private static String greenReportJson(long version) {
        return new GateReport("demo", version, true,
                Map.of("SCHEMA", List.of(), "SEMANTIC", List.of(), "PROVENANCE", List.of(),
                        "CAPABILITY", List.of(), "SAFETY", List.of(), "SIMULATION", List.of()),
                List.of()).toJson();
    }

    private static String redReportJson(long version) {
        return new GateReport("demo", version, false,
                Map.of("SCHEMA", List.of("schema violation"), "SEMANTIC", List.of(),
                        "PROVENANCE", List.of(), "CAPABILITY", List.of(), "SAFETY", List.of(),
                        "SIMULATION", List.of()),
                List.of()).toJson();
    }

    @Test
    void submitWithoutAuthorityIsRefused() {
        var globalDraft = draft("pkg-1", "demo", "https://api.mock.example/unified",
                HospitalPackageRow.ScopeType.GLOBAL, null, null);

        var result = serviceWith(greenPipeline()).submit(globalDraft, USER)
                .block(Duration.ofSeconds(5));

        assertThat(result.status()).isEqualTo(SubmissionStatus.NOT_AUTHORIZED);
        assertThat(result.state()).isNull();
        verify(packages, never()).upsertPending(any());
    }

    @Test
    void submitNormalizesIdentityStatusAndTrimsUrls() {
        // Draft claims APPROVED, carries a forged createdBy, a null id, and padded URLs —
        // submit must normalize all of it before the replacement upsert (M-1 store alignment).
        var tenantDraft = new HospitalPackageRow(null, "demo", "演示医院", "路由描述",
                HospitalPackageRow.Status.APPROVED, 1, content(" https://api.mock.example/unified "),
                "manual", LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                HospitalPackageRow.ScopeType.TENANT, "tenant-1", "租户一", null, null,
                "someone-else", null);
        var stored = state(0L, HospitalPackageRow.Status.PENDING, null);
        when(packages.upsertPending(any())).thenReturn(Mono.just(stored));

        var result = serviceWith(greenPipeline()).submit(tenantDraft, TENANT_ADMIN)
                .block(Duration.ofSeconds(5));

        assertThat(result.status()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(result.state()).isSameAs(stored);
        var captor = ArgumentCaptor.forClass(HospitalPackageRow.class);
        verify(packages).upsertPending(captor.capture());
        var normalized = captor.getValue();
        assertThat(normalized.id()).isNotBlank();
        assertThat(normalized.status()).isEqualTo(HospitalPackageRow.Status.PENDING);
        assertThat(normalized.createdBy()).isEqualTo("tenant-admin-1");
        var parsed = CODEC.parse(normalized.content()).orElseThrow();
        assertThat(parsed.tools()).allSatisfy(spec ->
                assertThat(spec.transport().url()).isEqualTo("https://api.mock.example/unified"));
    }

    @Test
    void reviewRunsAllGatesAndSavesReportAtCurrentVersion() {
        var row = storedRow("pkg-1", content("https://api.mock.example/unified"));
        when(packages.findById("pkg-1")).thenReturn(Mono.just(row));
        when(packages.findApprovalState("pkg-1")).thenReturn(
                Mono.just(state(5L, HospitalPackageRow.Status.PENDING, null)));
        when(packages.saveGateReport(eq("pkg-1"), anyString())).thenReturn(Mono.empty());

        var report = serviceWith(greenPipeline()).review("pkg-1", ADMIN)
                .block(Duration.ofSeconds(5));

        assertThat(report.passed()).isTrue();
        assertThat(report.approvalVersion()).isEqualTo(5L);
        assertThat(report.hospitalCode()).isEqualTo("demo");
        var saved = ArgumentCaptor.forClass(String.class);
        verify(packages).saveGateReport(eq("pkg-1"), saved.capture());
        assertThat(saved.getValue()).isEqualTo(report.toJson());
        assertThat(saved.getValue()).contains("\"passed\":true");
    }

    @Test
    void approveAppliesFencedTransitionWhenReportIsGreen() {
        when(packages.findApprovalState("pkg-1")).thenReturn(
                Mono.just(state(2L, HospitalPackageRow.Status.PENDING, greenReportJson(2L))));
        when(packages.compareAndSetStatus("pkg-1", 2L, HospitalPackageRow.Status.APPROVED,
                "admin-1", null)).thenReturn(Mono.just(true));

        var outcome = serviceWith(greenPipeline()).approve("pkg-1", 2L, ADMIN)
                .block(Duration.ofSeconds(5));

        assertThat(outcome).isEqualTo(LifecycleOutcome.APPLIED);
        verify(packages).compareAndSetStatus("pkg-1", 2L, HospitalPackageRow.Status.APPROVED,
                "admin-1", null);
    }

    @Test
    void approveDetectsStaleVersion() {
        when(packages.findApprovalState("pkg-1")).thenReturn(
                Mono.just(state(3L, HospitalPackageRow.Status.PENDING, greenReportJson(3L))));

        var outcome = serviceWith(greenPipeline()).approve("pkg-1", 2L, ADMIN)
                .block(Duration.ofSeconds(5));

        assertThat(outcome).isEqualTo(LifecycleOutcome.STALE_VERSION);
        verify(packages, never()).compareAndSetStatus(any(), anyLong(), any(), any(), any());
    }

    @Test
    void approveRequiresGreenReportAtCurrentVersion() {
        var service = serviceWith(greenPipeline());
        // Shape 1: no report saved at all.
        when(packages.findApprovalState("pkg-1")).thenReturn(
                Mono.just(state(2L, HospitalPackageRow.Status.PENDING, null)));
        assertThat(service.approve("pkg-1", 2L, ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.GATES_NOT_PASSED);
        // Shape 2: report exists but is red.
        when(packages.findApprovalState("pkg-1")).thenReturn(
                Mono.just(state(2L, HospitalPackageRow.Status.PENDING, redReportJson(2L))));
        assertThat(service.approve("pkg-1", 2L, ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.GATES_NOT_PASSED);
        // Shape 3: green report, but from an older version (package was replaced after review).
        when(packages.findApprovalState("pkg-1")).thenReturn(
                Mono.just(state(2L, HospitalPackageRow.Status.PENDING, greenReportJson(1L))));
        assertThat(service.approve("pkg-1", 2L, ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.GATES_NOT_PASSED);

        verify(packages, never()).compareAndSetStatus(any(), anyLong(), any(), any(), any());
    }

    @Test
    void approveOnlyFromPendingState() {
        when(packages.findApprovalState("pkg-1")).thenReturn(Mono.just(reviewedState(2L,
                HospitalPackageRow.Status.APPROVED)));

        var outcome = serviceWith(greenPipeline()).approve("pkg-1", 2L, ADMIN)
                .block(Duration.ofSeconds(5));

        assertThat(outcome).isEqualTo(LifecycleOutcome.INVALID_TRANSITION);
        verify(packages, never()).compareAndSetStatus(any(), anyLong(), any(), any(), any());
    }

    @Test
    void transitionsRequirePlatformAdmin() {
        var service = serviceWith(greenPipeline());

        assertThat(service.approve("pkg-1", 0L, TENANT_ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.NOT_AUTHORIZED);
        assertThat(service.reject("pkg-1", 0L, "理由", TENANT_ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.NOT_AUTHORIZED);
        assertThat(service.revoke("pkg-1", 0L, "理由", TENANT_ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.NOT_AUTHORIZED);

        verifyNoInteractions(packages);
    }

    @Test
    void rejectRequiresReasonStoresItAndOnlyFromPending() {
        var service = serviceWith(greenPipeline());
        assertThatThrownBy(() -> service.reject("pkg-1", 1L, " ", ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rejection reason is required");

        when(packages.findApprovalState("pkg-1")).thenReturn(
                Mono.just(state(1L, HospitalPackageRow.Status.PENDING, null)));
        when(packages.compareAndSetStatus("pkg-1", 1L, HospitalPackageRow.Status.REJECTED,
                "admin-1", "来源材料与实际不符")).thenReturn(Mono.just(true));
        assertThat(service.reject("pkg-1", 1L, "来源材料与实际不符", ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.APPLIED);
        verify(packages).compareAndSetStatus("pkg-1", 1L, HospitalPackageRow.Status.REJECTED,
                "admin-1", "来源材料与实际不符");

        // An already-APPROVED package cannot be rejected — revoke is the only way down.
        when(packages.findApprovalState("pkg-1")).thenReturn(Mono.just(reviewedState(1L,
                HospitalPackageRow.Status.APPROVED)));
        assertThat(service.reject("pkg-1", 1L, "理由", ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.INVALID_TRANSITION);
    }

    @Test
    void revokeOnlyFromApproved() {
        var service = serviceWith(greenPipeline());
        when(packages.findApprovalState("pkg-1")).thenReturn(
                Mono.just(state(1L, HospitalPackageRow.Status.PENDING, null)));
        assertThat(service.revoke("pkg-1", 1L, "理由", ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.INVALID_TRANSITION);

        when(packages.findApprovalState("pkg-1")).thenReturn(Mono.just(reviewedState(4L,
                HospitalPackageRow.Status.APPROVED)));
        when(packages.compareAndSetStatus("pkg-1", 4L, HospitalPackageRow.Status.REVOKED,
                "admin-1", "合作终止")).thenReturn(Mono.just(true));
        assertThat(service.revoke("pkg-1", 4L, "合作终止", ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.APPLIED);
        verify(packages).compareAndSetStatus("pkg-1", 4L, HospitalPackageRow.Status.REVOKED,
                "admin-1", "合作终止");
    }

    @Test
    void missingStateReturnsMissing() {
        var service = serviceWith(greenPipeline());
        when(packages.findApprovalState("pkg-x")).thenReturn(Mono.empty());

        assertThat(service.approve("pkg-x", 0L, ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.MISSING);
        assertThat(service.reject("pkg-x", 0L, "理由", ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.MISSING);
        assertThat(service.revoke("pkg-x", 0L, "理由", ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.MISSING);
    }
}
