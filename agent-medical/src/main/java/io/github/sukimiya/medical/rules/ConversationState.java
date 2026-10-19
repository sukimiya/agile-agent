package io.github.sukimiya.medical.rules;

import io.github.sukimiya.medical.hospital.MedicalHumanTaskRow;

/**
 * The conversation's derived state (Plan 4 spec §8): computed from the conversation's open
 * E-call row — the single source of truth, no conversation state table. FAIL_CLOSED is the
 * guard's answer when the derivation itself breaks (query failure, corrupt payload):
 * restricted like every other non-normal state, never looser.
 */
public record ConversationState(State state, MedicalHumanTaskRow ecallTask, EcallPayload payload) {

    public enum State { NORMAL, AWAITING_TAKEOVER, TAKEN_OVER, FAIL_CLOSED }

    public static ConversationState normal() {
        return new ConversationState(State.NORMAL, null, null);
    }

    public static ConversationState awaitingTakeover(MedicalHumanTaskRow task,
            EcallPayload payload) {
        return new ConversationState(State.AWAITING_TAKEOVER, task, payload);
    }

    public static ConversationState takenOver(MedicalHumanTaskRow task, EcallPayload payload) {
        return new ConversationState(State.TAKEN_OVER, task, payload);
    }

    public static ConversationState failClosed() {
        return new ConversationState(State.FAIL_CLOSED, null, null);
    }

    /** True for every state in which the agent is restricted (zero tools, fixed script). */
    public boolean restricted() {
        return state != State.NORMAL;
    }
}
