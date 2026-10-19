package io.github.sukimiya.medical.rules;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.sukimiya.medical.hospital.MedicalHumanTaskRepository;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRow;
import reactor.core.publisher.Mono;

/**
 * The provider's entry gate (Plan 4 spec §8): derives the conversation state from its open
 * E-call task, running the lazy SLA double-check first so an overdue task is escalated
 * before the state is rendered. Fail-closed: any query failure or corrupt payload yields
 * FAIL_CLOSED (restricted, zero tools) — the agent is never let loose because a lookup
 * broke. Non-final so provider tests can mock it.
 */
public class ConversationGuard {

    private static final Logger log = LoggerFactory.getLogger(ConversationGuard.class);

    private final MedicalHumanTaskRepository tasks;
    private final EscalationService escalation;

    public ConversationGuard(MedicalHumanTaskRepository tasks, EscalationService escalation) {
        this.tasks = tasks;
        this.escalation = escalation;
    }

    public Mono<ConversationState> state(String conversationId) {
        return escalation.escalateOverdue(conversationId)
                .then(tasks.findOpenEcallByConversationId(conversationId))
                .map(ConversationGuard::toState)
                .defaultIfEmpty(ConversationState.normal())
                .onErrorResume(e -> {
                    log.warn("Conversation guard failed closed for {}: {}", conversationId,
                            e.toString());
                    return Mono.just(ConversationState.failClosed());
                });
    }

    private static ConversationState toState(MedicalHumanTaskRow task) {
        Optional<EcallPayload> payload = EcallPayload.fromJson(task.argumentsJson());
        if (payload.isEmpty()) {
            log.warn("Open E-call payload unparsable; failing closed: task={}", task.id());
            return ConversationState.failClosed();
        }
        return switch (task.status()) {
            case PENDING -> ConversationState.awaitingTakeover(task, payload.get());
            case CLAIMED -> ConversationState.takenOver(task, payload.get());
            // defensive only: the guard query excludes COMPLETED rows
            case COMPLETED -> ConversationState.normal();
        };
    }
}
