package io.github.sukimiya.medical.lifecycle;

import java.util.Optional;
import java.util.UUID;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.hospital.HospitalPackageApprovalState;
import io.github.sukimiya.medical.hospital.HospitalPackageCodec;
import io.github.sukimiya.medical.hospital.HospitalPackageContent;
import io.github.sukimiya.medical.hospital.HospitalPackageRepository;
import io.github.sukimiya.medical.hospital.HospitalPackageRow;
import io.github.sukimiya.medical.hospital.HospitalToolSpec;
import io.github.sukimiya.medical.hospital.TransportBinding;
import io.github.sukimiya.medical.validation.GateContext;
import io.github.sukimiya.medical.validation.GatePipeline;
import io.github.sukimiya.medical.validation.GateReport;
import io.github.sukimiya.medical.validation.PackageSubmissionAuthority;
import reactor.core.publisher.Mono;

/**
 * The package lifecycle (spec §14 resolutions 1, 2, 7): submit is replacement semantics
 * (upsert by hospital_code bumps approval_version and clears the audit columns), review
 * runs the six-gate pipeline and stores the report, and approve/reject/revoke are the only
 * status transitions — each fenced by an optimistic CAS on approval_version so a package
 * replaced mid-review can never be approved on its stale report. Pure service layer: no
 * REST, no Spring annotations; registered as a bean by MedicalAutoConfiguration.
 */
public final class HospitalPackageLifecycleService {

    private final HospitalPackageRepository packages;
    private final GatePipeline pipeline;
    private final HospitalPackageCodec codec;

    public HospitalPackageLifecycleService(HospitalPackageRepository packages, GatePipeline pipeline,
            HospitalPackageCodec codec) {
        this.packages = packages;
        this.pipeline = pipeline;
        this.codec = codec;
    }

    /** Normalizes the draft (forced PENDING, submitter as createdBy, trimmed binding URLs) and upserts it. */
    public Mono<SubmissionResult> submit(HospitalPackageRow draft, IdentityContext submitter) {
        if (!PackageSubmissionAuthority.maySubmit(submitter, draft)) {
            return Mono.just(new SubmissionResult(SubmissionStatus.NOT_AUTHORIZED, null));
        }
        return packages.upsertPending(normalize(draft, submitter))
                .map(state -> new SubmissionResult(SubmissionStatus.SUBMITTED, state));
    }

    /** Runs all six gates against the stored row and persists the report; never bumps the version. */
    public Mono<GateReport> review(String id, IdentityContext reviewer) {
        return packages.findById(id)
                .zipWith(packages.findApprovalState(id))
                .flatMap(tuple -> {
                    HospitalPackageRow row = tuple.getT1();
                    HospitalPackageApprovalState state = tuple.getT2();
                    GateContext context = new GateContext(row,
                            codec.parse(row.content()).orElse(null), reviewer);
                    return pipeline.run(context, state.approvalVersion());
                })
                .flatMap(report -> packages.saveGateReport(id, report.toJson()).thenReturn(report));
    }

    public Mono<LifecycleOutcome> approve(String id, long expectedVersion, IdentityContext approver) {
        if (!PackageSubmissionAuthority.mayApprove(approver)) {
            return Mono.just(LifecycleOutcome.NOT_AUTHORIZED);
        }
        return packages.findApprovalState(id)
                .flatMap(state -> approveState(id, expectedVersion, approver, state))
                .switchIfEmpty(Mono.just(LifecycleOutcome.MISSING));
    }

    /** PENDING → REJECTED with a mandatory reason; fenced like every other transition. */
    public Mono<LifecycleOutcome> reject(String id, long expectedVersion, String reason,
            IdentityContext approver) {
        requireReason(reason);
        if (!PackageSubmissionAuthority.mayApprove(approver)) {
            return Mono.just(LifecycleOutcome.NOT_AUTHORIZED);
        }
        return packages.findApprovalState(id)
                .flatMap(state -> transition(id, expectedVersion, state,
                        HospitalPackageRow.Status.PENDING, HospitalPackageRow.Status.REJECTED,
                        approver, reason))
                .switchIfEmpty(Mono.just(LifecycleOutcome.MISSING));
    }

    /** APPROVED → REVOKED with a mandatory reason; the only way down from APPROVED. */
    public Mono<LifecycleOutcome> revoke(String id, long expectedVersion, String reason,
            IdentityContext approver) {
        requireReason(reason);
        if (!PackageSubmissionAuthority.mayApprove(approver)) {
            return Mono.just(LifecycleOutcome.NOT_AUTHORIZED);
        }
        return packages.findApprovalState(id)
                .flatMap(state -> transition(id, expectedVersion, state,
                        HospitalPackageRow.Status.APPROVED, HospitalPackageRow.Status.REVOKED,
                        approver, reason))
                .switchIfEmpty(Mono.just(LifecycleOutcome.MISSING));
    }

    private Mono<LifecycleOutcome> approveState(String id, long expectedVersion,
            IdentityContext approver, HospitalPackageApprovalState state) {
        if (state.approvalVersion() != expectedVersion) {
            return Mono.just(LifecycleOutcome.STALE_VERSION);
        }
        if (state.status() != HospitalPackageRow.Status.PENDING) {
            return Mono.just(LifecycleOutcome.INVALID_TRANSITION);
        }
        if (!gatesPassedAtCurrentVersion(state)) {
            return Mono.just(LifecycleOutcome.GATES_NOT_PASSED);
        }
        return packages.compareAndSetStatus(id, expectedVersion,
                        HospitalPackageRow.Status.APPROVED, approver.subject(), null)
                .map(applied -> applied ? LifecycleOutcome.APPLIED : LifecycleOutcome.STALE_VERSION);
    }

    private Mono<LifecycleOutcome> transition(String id, long expectedVersion,
            HospitalPackageApprovalState state, HospitalPackageRow.Status required,
            HospitalPackageRow.Status target, IdentityContext approver, String reason) {
        if (state.approvalVersion() != expectedVersion) {
            return Mono.just(LifecycleOutcome.STALE_VERSION);
        }
        if (state.status() != required) {
            return Mono.just(LifecycleOutcome.INVALID_TRANSITION);
        }
        return packages.compareAndSetStatus(id, expectedVersion, target, approver.subject(), reason)
                .map(applied -> applied ? LifecycleOutcome.APPLIED : LifecycleOutcome.STALE_VERSION);
    }

    private static boolean gatesPassedAtCurrentVersion(HospitalPackageApprovalState state) {
        Optional<GateReport> report = state.lastGateReport() == null
                ? Optional.empty()
                : GateReport.fromJson(state.lastGateReport());
        return report.isPresent() && report.get().passed()
                && report.get().approvalVersion() == state.approvalVersion();
    }

    private HospitalPackageRow normalize(HospitalPackageRow draft, IdentityContext submitter) {
        String id = draft.id() == null || draft.id().isBlank()
                ? UUID.randomUUID().toString() : draft.id();
        return new HospitalPackageRow(id, draft.hospitalCode(), draft.hospitalName(),
                draft.routingDescription(), HospitalPackageRow.Status.PENDING,
                draft.schemaVersion(), normalizedContent(draft.content()), draft.source(),
                draft.validFrom(), draft.validUntil(), draft.scopeType(), draft.tenantId(),
                draft.tenantName(), draft.orgId(), draft.orgName(), submitter.subject(),
                draft.createdAt());
    }

    /** M-1 store alignment: rewrite parsable content through the codec with trimmed binding URLs. */
    private String normalizedContent(String content) {
        return codec.parse(content)
                .map(parsed -> codec.write(new HospitalPackageContent(parsed.schemaVersion(),
                        parsed.tools().stream()
                                .map(spec -> new HospitalToolSpec(spec.name(), spec.description(),
                                        spec.parametersSchema(), trimmed(spec.transport())))
                                .toList())))
                .orElse(content);
    }

    private static TransportBinding trimmed(TransportBinding binding) {
        if (binding == null || binding.url() == null) {
            return binding;
        }
        return new TransportBinding(binding.type(), binding.url().trim(), binding.channel(),
                binding.address());
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("rejection reason is required");
        }
    }
}
