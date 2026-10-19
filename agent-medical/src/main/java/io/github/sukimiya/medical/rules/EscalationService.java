package io.github.sukimiya.medical.rules;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRepository;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRow;
import reactor.core.publisher.Mono;

/**
 * The E-call runtime (Plan 4 spec §7.4, §8, §9): idempotent creation on a rule hit, atomic
 * CAS takeover/completion, SLA escalation with a ceiling, and lightweight stale-claim
 * recovery. The upgrade routine is public and idempotent — the poller calls it on a timer
 * and the ConversationGuard calls it lazily on touch — so both paths converge on the same
 * CAS-fenced updates and can never double-escalate. Upgrade action = escalation_level+1 +
 * recomputed deadline + audit event + log: no real notification channel in this plan
 * (spec §2 ruling 5). Non-final so the guard's unit tests can mock it.
 */
public class EscalationService {

    /** Role allowed to claim/complete tasks, in addition to platform admins (spec §8). */
    public static final String MEDICAL_OPS_ROLE = "MEDICAL_OPS";
    /** Pinned detail of the one-time terminal event when escalation levels run out. */
    static final String CEILING_DETAIL = "已冒泡至最高层级，停止升级";
    /** Pinned detail when a stale claim is released back to PENDING. */
    static final String STALE_RELEASED_DETAIL = "认领超时，任务已回退待认领";
    /** Pinned detail when a stale claim is detected but auto-release is disabled. */
    static final String STALE_DETECTED_DETAIL = "认领超时（自动回退未启用）";

    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);

    private final MedicalHumanTaskRepository tasks;
    private final MedicalTaskEventRepository events;
    private final Clock clock;
    private final long staleClaimSeconds;
    private final boolean staleClaimReleaseEnabled;

    public EscalationService(MedicalHumanTaskRepository tasks, MedicalTaskEventRepository events,
            Clock clock, long staleClaimSeconds, boolean staleClaimReleaseEnabled) {
        this.tasks = tasks;
        this.events = events;
        this.clock = clock;
        this.staleClaimSeconds = staleClaimSeconds;
        this.staleClaimReleaseEnabled = staleClaimReleaseEnabled;
    }

    /** One poller cycle: escalate every overdue open E-call, then recover stale claims. */
    public Mono<Void> tick() {
        return escalateOverdue().then(recoverStaleClaims()).then();
    }

    /**
     * Creates the conversation's E-call, or idempotently reuses the open one (the partial
     * unique index makes a second open row impossible — Review Focus #3). The CREATED audit
     * event is written only when this call actually inserted.
     */
    public Mono<MedicalHumanTaskRow> createEcall(String conversationId, String createdBy,
            EcallPayload payload) {
        String taskId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(clock);
        MedicalHumanTaskRow row = MedicalHumanTaskRow.ecall(taskId, conversationId,
                payload.toJson(), createdBy, payload.priority(),
                now.plusSeconds(payload.slaSeconds()));
        return tasks.insertEcallOrFindExisting(row)
                .flatMap(task -> task.id().equals(taskId)
                        ? events.insert(new MedicalTaskEventRow(UUID.randomUUID().toString(),
                                task.id(), MedicalTaskEventRow.EventType.CREATED, 0, createdBy,
                                "rule_code=" + payload.ruleCode()
                                        + ", triage_category=" + payload.triageCategory(),
                                null))
                                .doOnSuccess(ignored -> log.info(
                                        "E-call created: conversation={} task={} rule={} priority={}",
                                        conversationId, task.id(), payload.ruleCode(),
                                        payload.priority()))
                                .thenReturn(task)
                        : Mono.just(task));
    }

    /** Atomic takeover (spec §8): CAS PENDING→CLAIMED; 0 rows means somebody else won. */
    public Mono<ClaimOutcome> claim(String taskId, IdentityContext actor) {
        if (!mayOperate(actor)) {
            return Mono.just(ClaimOutcome.NOT_AUTHORIZED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return tasks.claim(taskId, actor.subject(), now)
                .flatMap(applied -> {
                    if (applied) {
                        return events.insert(new MedicalTaskEventRow(UUID.randomUUID().toString(),
                                        taskId, MedicalTaskEventRow.EventType.CLAIMED, 0,
                                        actor.subject(), null, null))
                                .doOnSuccess(ignored -> log.info(
                                        "E-call claimed: task={} actor={}", taskId, actor.subject()))
                                .thenReturn(ClaimOutcome.APPLIED);
                    }
                    return tasks.findById(taskId)
                            .map(existing -> ClaimOutcome.CONFLICT)
                            .defaultIfEmpty(ClaimOutcome.MISSING);
                });
    }

    /**
     * Completion (spec §8): ECALL requires CLAIMED-by-owner (or a platform-admin override);
     * NORMAL (C01 consults) completes directly from PENDING — the hospital-confirmation
     * registration entry — or from CLAIMED by owner/admin. The resolution note is
     * mandatory: it is the human-auditable proof of the outcome.
     */
    public Mono<CompleteOutcome> complete(String taskId, String resolutionNote,
            IdentityContext actor) {
        if (resolutionNote == null || resolutionNote.isBlank()) {
            throw new IllegalArgumentException("resolution note is required");
        }
        if (!mayOperate(actor)) {
            return Mono.just(CompleteOutcome.NOT_AUTHORIZED);
        }
        return tasks.findById(taskId)
                .flatMap(task -> completeTask(task, resolutionNote, actor))
                .switchIfEmpty(Mono.just(CompleteOutcome.MISSING));
    }

    private Mono<CompleteOutcome> completeTask(MedicalHumanTaskRow task, String resolutionNote,
            IdentityContext actor) {
        boolean adminOverride = actor.platformAdmin() && !actor.subject().equals(task.claimedBy());
        Mono<Boolean> cas;
        if (task.taskKind() == MedicalHumanTaskRow.TaskKind.ECALL) {
            if (task.status() != MedicalHumanTaskRow.TaskStatus.CLAIMED
                    || (!actor.platformAdmin() && !actor.subject().equals(task.claimedBy()))) {
                return Mono.just(CompleteOutcome.INVALID_STATE);
            }
            cas = adminOverride
                    ? tasks.completeClaimedAny(task.id(), resolutionNote, LocalDateTime.now(clock))
                    : tasks.completeClaimedByOwner(task.id(), actor.subject(), resolutionNote,
                            LocalDateTime.now(clock));
        } else if (task.status() == MedicalHumanTaskRow.TaskStatus.PENDING) {
            cas = tasks.completeFromPending(task.id(), resolutionNote, LocalDateTime.now(clock));
        } else if (task.status() == MedicalHumanTaskRow.TaskStatus.CLAIMED
                && (actor.platformAdmin() || actor.subject().equals(task.claimedBy()))) {
            cas = adminOverride
                    ? tasks.completeClaimedAny(task.id(), resolutionNote, LocalDateTime.now(clock))
                    : tasks.completeClaimedByOwner(task.id(), actor.subject(), resolutionNote,
                            LocalDateTime.now(clock));
        } else {
            return Mono.just(CompleteOutcome.INVALID_STATE);
        }
        return cas.flatMap(applied -> {
            if (!applied) {
                return Mono.just(CompleteOutcome.INVALID_STATE); // concurrent transition won
            }
            return events.insert(new MedicalTaskEventRow(UUID.randomUUID().toString(),
                            task.id(), MedicalTaskEventRow.EventType.COMPLETED, 0,
                            actor.subject(), resolutionNote, null))
                    .doOnSuccess(ignored -> log.info("Task completed: task={} kind={} actor={}",
                            task.id(), task.taskKind(), actor.subject()))
                    .thenReturn(CompleteOutcome.APPLIED);
        });
    }

    /** The poller's full-scan upgrade routine (spec §9). Returns how many tasks escalated. */
    public Mono<Integer> escalateOverdue() {
        LocalDateTime now = LocalDateTime.now(clock);
        return tasks.findOverdueOpenEcalls(now)
                .concatMap(task -> escalateOne(task, now)
                        .onErrorResume(e -> {
                            log.warn("SLA escalation failed for task {}: {}", task.id(),
                                    e.toString());
                            return Mono.just(false);
                        }))
                .filter(escalated -> escalated)
                .count()
                .map(Long::intValue);
    }

    /**
     * The lazy double-check for one conversation (spec §2 ruling 5): the same idempotent
     * routine, errors swallowed — a guard touch must never fail a conversation because an
     * upgrade could not run (the guard itself is what fails closed).
     */
    public Mono<Void> escalateOverdue(String conversationId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return tasks.findOpenEcallByConversationId(conversationId)
                .filter(task -> task.status() == MedicalHumanTaskRow.TaskStatus.PENDING
                        && task.slaDeadline() != null && task.slaDeadline().isBefore(now))
                .flatMap(task -> escalateOne(task, now)
                        .onErrorResume(e -> {
                            log.warn("Lazy SLA escalation failed for task {}: {}", task.id(),
                                    e.toString());
                            return Mono.just(false);
                        }))
                .then()
                .onErrorResume(e -> {
                    log.warn("Lazy SLA check failed for conversation {}: {}", conversationId,
                            e.toString());
                    return Mono.empty();
                });
    }

    private Mono<Boolean> escalateOne(MedicalHumanTaskRow task, LocalDateTime now) {
        Optional<EcallPayload> parsed = EcallPayload.fromJson(task.argumentsJson());
        if (parsed.isEmpty()) {
            // Medical red line: never invent a triage conclusion — skip and log instead.
            log.warn("E-call payload unparsable; skipping escalation: task={}", task.id());
            return Mono.just(false);
        }
        return escalateWithPayload(task, parsed.get(), now);
    }

    private Mono<Boolean> escalateWithPayload(MedicalHumanTaskRow task, EcallPayload payload,
            LocalDateTime now) {
        List<Integer> levels = payload.escalationLevels();
        if (levels == null || levels.isEmpty()) {
            log.warn("E-call payload has no escalation levels; skipping: task={}", task.id());
            return Mono.just(false);
        }
        if (task.escalationLevel() >= levels.size()) {
            // Ceiling (spec §8): one terminal audit event, then never escalate again.
            return events.hasEvent(task.id(), MedicalTaskEventRow.EventType.ESCALATED,
                            CEILING_DETAIL)
                    .flatMap(already -> already
                            ? Mono.just(false)
                            : events.insert(new MedicalTaskEventRow(UUID.randomUUID().toString(),
                                    task.id(), MedicalTaskEventRow.EventType.ESCALATED,
                                    levels.size(), null, CEILING_DETAIL, null))
                                    .doOnSuccess(ignored -> log.info(
                                            "E-call escalation ceiling reached: task={} level={}",
                                            task.id(), levels.size()))
                                    .thenReturn(false));
        }
        Integer window = levels.get(task.escalationLevel());
        if (window == null || window <= 0) {
            log.warn("E-call escalation window invalid at level {}; skipping: task={}",
                    task.escalationLevel(), task.id());
            return Mono.just(false);
        }
        int newLevel = task.escalationLevel() + 1;
        LocalDateTime newDeadline = now.plusSeconds(window);
        return tasks.escalate(task.id(), task.escalationLevel(), newLevel, newDeadline)
                .flatMap(applied -> {
                    if (!applied) {
                        return Mono.just(false); // concurrent upgrade won the CAS — idempotent no-op
                    }
                    return events.insert(new MedicalTaskEventRow(UUID.randomUUID().toString(),
                                    task.id(), MedicalTaskEventRow.EventType.ESCALATED, newLevel,
                                    null, "sla_deadline=" + newDeadline, null))
                            .doOnSuccess(ignored -> log.info(
                                    "E-call escalated: task={} level={} nextDeadline={}",
                                    task.id(), newLevel, newDeadline))
                            .thenReturn(true);
                });
    }

    /**
     * Lightweight recovery (spec §8): CLAIMED tasks older than staleClaimSeconds without
     * completion are treated as crashed operators. Default: release back to PENDING (CAS
     * fenced on the exact claimed_at); with release disabled: detect-only. Either mode
     * writes at most one audit event per stuck claim (pinned detail markers).
     */
    public Mono<Integer> recoverStaleClaims() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusSeconds(staleClaimSeconds);
        return tasks.findClaimedBefore(cutoff)
                .concatMap(task -> recoverOne(task)
                        .onErrorResume(e -> {
                            log.warn("Stale-claim recovery failed for task {}: {}", task.id(),
                                    e.toString());
                            return Mono.just(false);
                        }))
                .filter(recovered -> recovered)
                .count()
                .map(Long::intValue);
    }

    private Mono<Boolean> recoverOne(MedicalHumanTaskRow task) {
        String detail = staleClaimReleaseEnabled ? STALE_RELEASED_DETAIL : STALE_DETECTED_DETAIL;
        return events.hasEvent(task.id(), MedicalTaskEventRow.EventType.ESCALATED, detail)
                .flatMap(already -> {
                    if (already) {
                        return Mono.just(false);
                    }
                    if (!staleClaimReleaseEnabled) {
                        return auditStale(task, detail)
                                .doOnSuccess(ignored -> log.warn(
                                        "Stale claim detected (auto-release disabled): "
                                                + "task={} claimedBy={}", task.id(),
                                        task.claimedBy()))
                                .thenReturn(false);
                    }
                    return tasks.releaseClaim(task.id(), task.claimedAt())
                            .flatMap(released -> {
                                if (!released) {
                                    return Mono.just(false); // claim moved on concurrently
                                }
                                return auditStale(task, detail)
                                        .doOnSuccess(ignored -> log.warn(
                                                "Stale claim released back to PENDING: "
                                                        + "task={} claimedBy={}", task.id(),
                                                task.claimedBy()))
                                        .thenReturn(true);
                            });
                });
    }

    private Mono<Void> auditStale(MedicalHumanTaskRow task, String detail) {
        return events.insert(new MedicalTaskEventRow(UUID.randomUUID().toString(), task.id(),
                MedicalTaskEventRow.EventType.ESCALATED, task.escalationLevel(), null, detail,
                null));
    }

    private static boolean mayOperate(IdentityContext actor) {
        return actor != null && (actor.platformAdmin()
                || actor.roles().contains(MEDICAL_OPS_ROLE));
    }
}
