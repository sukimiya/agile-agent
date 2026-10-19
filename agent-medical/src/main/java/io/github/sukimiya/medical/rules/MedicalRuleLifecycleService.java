package io.github.sukimiya.medical.rules;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.lifecycle.LifecycleOutcome;
import io.github.sukimiya.medical.lifecycle.SubmissionStatus;
import reactor.core.publisher.Mono;

/**
 * The rule fenced lifecycle (Plan 4 spec §2 ruling 2, §6): submit is replacement semantics
 * (upsert by rule_code within its scope bumps approval_version and clears the audit
 * columns), review runs the MedicalRuleValidator and persists the report, and
 * approve/reject/revoke are the only status transitions — each fenced by an optimistic CAS
 * on approval_version so a rule replaced mid-review can never be approved on its stale
 * report. Every SAFETY_PATTERN lifecycle action chains {@link SafetyPatternStore#reload()}
 * so the runtime data increment always matches the table (reload never errors, so chaining
 * is safe). Pure service layer: no REST, no Spring annotations; registered as a bean by
 * MedicalAutoConfiguration.
 */
public final class MedicalRuleLifecycleService {

    private final MedicalRuleRepository rules;
    private final MedicalRuleValidator validator;
    private final MedicalRuleCodec codec;
    private final SafetyPatternStore safetyPatterns;

    public MedicalRuleLifecycleService(MedicalRuleRepository rules, MedicalRuleValidator validator,
            MedicalRuleCodec codec, SafetyPatternStore safetyPatterns) {
        this.rules = rules;
        this.validator = validator;
        this.codec = codec;
        this.safetyPatterns = safetyPatterns;
    }

    /** Normalizes the draft (generated id, forced PENDING, submitter as createdBy, canonical content) and upserts it. */
    public Mono<RuleSubmissionResult> submit(MedicalRuleRow draft, IdentityContext submitter) {
        if (!RuleSubmissionAuthority.maySubmit(submitter, draft)) {
            return Mono.just(new RuleSubmissionResult(SubmissionStatus.NOT_AUTHORIZED, null));
        }
        Mono<RuleSubmissionResult> submitted = rules.upsertPending(normalize(draft, submitter))
                .map(state -> new RuleSubmissionResult(SubmissionStatus.SUBMITTED, state));
        return isSafetyPattern(draft)
                ? submitted.flatMap(result -> safetyPatterns.reload().thenReturn(result))
                : submitted;
    }

    /**
     * Validates a single-snapshot read of the stored row and its approval state, then
     * persists the report; never bumps the version. The one-read shape keeps a concurrent
     * replacement submit from pairing old content with a new version (mirrors the package
     * review, spec §14 resolution 2).
     */
    public Mono<RuleValidationReport> review(String id) {
        return rules.findForReview(id)
                .flatMap(snapshot -> {
                    List<String> violations = validator.validate(snapshot.row());
                    RuleValidationReport report = new RuleValidationReport(snapshot.row().ruleCode(),
                            snapshot.state().approvalVersion(), violations.isEmpty(), violations);
                    return rules.saveValidationReport(id, report.toJson()).thenReturn(report);
                });
    }

    public Mono<LifecycleOutcome> approve(String id, long expectedVersion, IdentityContext approver) {
        return rules.findForReview(id)
                .flatMap(snapshot -> approveSnapshot(id, expectedVersion, approver, snapshot))
                .switchIfEmpty(Mono.just(LifecycleOutcome.MISSING));
    }

    /** PENDING → REJECTED with a mandatory reason; fenced like every other transition. */
    public Mono<LifecycleOutcome> reject(String id, long expectedVersion, String reason,
            IdentityContext approver) {
        requireReason(reason);
        return rules.findForReview(id)
                .flatMap(snapshot -> transition(id, expectedVersion, snapshot,
                        MedicalRuleRow.Status.PENDING, MedicalRuleRow.Status.REJECTED,
                        approver, reason))
                .switchIfEmpty(Mono.just(LifecycleOutcome.MISSING));
    }

    /** APPROVED → REVOKED with a mandatory reason; the only way down from APPROVED. */
    public Mono<LifecycleOutcome> revoke(String id, long expectedVersion, String reason,
            IdentityContext approver) {
        requireReason(reason);
        return rules.findForReview(id)
                .flatMap(snapshot -> transition(id, expectedVersion, snapshot,
                        MedicalRuleRow.Status.APPROVED, MedicalRuleRow.Status.REVOKED,
                        approver, reason))
                .switchIfEmpty(Mono.just(LifecycleOutcome.MISSING));
    }

    private Mono<LifecycleOutcome> approveSnapshot(String id, long expectedVersion,
            IdentityContext approver, MedicalRuleRepository.RuleReviewSnapshot snapshot) {
        if (!RuleSubmissionAuthority.mayApprove(approver, snapshot.row())) {
            return Mono.just(LifecycleOutcome.NOT_AUTHORIZED);
        }
        MedicalRuleApprovalState state = snapshot.state();
        if (state.approvalVersion() != expectedVersion) {
            return Mono.just(LifecycleOutcome.STALE_VERSION);
        }
        if (state.status() != MedicalRuleRow.Status.PENDING) {
            return Mono.just(LifecycleOutcome.INVALID_TRANSITION);
        }
        if (!validationPassedAtCurrentVersion(state)) {
            return Mono.just(LifecycleOutcome.GATES_NOT_PASSED);
        }
        return rules.compareAndSetStatus(id, expectedVersion, MedicalRuleRow.Status.APPROVED,
                        approver.subject(), null)
                .flatMap(applied -> applied
                        ? reloadIfSafetyPattern(snapshot.row(), LifecycleOutcome.APPLIED)
                        : Mono.just(LifecycleOutcome.STALE_VERSION));
    }

    private Mono<LifecycleOutcome> transition(String id, long expectedVersion,
            MedicalRuleRepository.RuleReviewSnapshot snapshot, MedicalRuleRow.Status required,
            MedicalRuleRow.Status target, IdentityContext approver, String reason) {
        if (!RuleSubmissionAuthority.mayApprove(approver, snapshot.row())) {
            return Mono.just(LifecycleOutcome.NOT_AUTHORIZED);
        }
        MedicalRuleApprovalState state = snapshot.state();
        if (state.approvalVersion() != expectedVersion) {
            return Mono.just(LifecycleOutcome.STALE_VERSION);
        }
        if (state.status() != required) {
            return Mono.just(LifecycleOutcome.INVALID_TRANSITION);
        }
        return rules.compareAndSetStatus(id, expectedVersion, target, approver.subject(), reason)
                .flatMap(applied -> applied
                        ? reloadIfSafetyPattern(snapshot.row(), LifecycleOutcome.APPLIED)
                        : Mono.just(LifecycleOutcome.STALE_VERSION));
    }

    private Mono<LifecycleOutcome> reloadIfSafetyPattern(MedicalRuleRow row,
            LifecycleOutcome outcome) {
        return isSafetyPattern(row)
                ? safetyPatterns.reload().thenReturn(outcome)
                : Mono.just(outcome);
    }

    private static boolean validationPassedAtCurrentVersion(MedicalRuleApprovalState state) {
        Optional<RuleValidationReport> report = state.lastValidationReport() == null
                ? Optional.empty()
                : RuleValidationReport.fromJson(state.lastValidationReport());
        return report.isPresent() && report.get().passed()
                && report.get().approvalVersion() == state.approvalVersion();
    }

    private MedicalRuleRow normalize(MedicalRuleRow draft, IdentityContext submitter) {
        String id = draft.id() == null || draft.id().isBlank()
                ? UUID.randomUUID().toString() : draft.id();
        return new MedicalRuleRow(id, draft.ruleCode(), draft.ruleKind(), draft.scopeType(),
                draft.tenantId(), draft.tenantName(), draft.orgId(), draft.orgName(),
                draft.hospitalCode(), normalizedContent(draft.contentJson()), draft.source(),
                draft.validFrom(), draft.validUntil(), MedicalRuleRow.Status.PENDING,
                submitter.subject(), draft.createdAt());
    }

    /** Store alignment: rewrite parsable content through the codec; unparsable content is left for review to flag. */
    private String normalizedContent(String contentJson) {
        return codec.parse(contentJson).map(codec::write).orElse(contentJson);
    }

    private static boolean isSafetyPattern(MedicalRuleRow row) {
        return row != null && "SAFETY_PATTERN".equals(row.ruleKind());
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("rejection reason is required");
        }
    }
}
