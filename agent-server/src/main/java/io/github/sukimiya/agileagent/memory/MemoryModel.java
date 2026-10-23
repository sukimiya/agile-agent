package io.github.sukimiya.agileagent.memory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import io.github.sukimiya.agileagent.service.LlmClient;

/** Data contracts shared by the durable memory components (turns, messages, history). */
public final class MemoryModel {

    private MemoryModel() {}

    /**
     * Turn state machine: ACCEPTED → RUNNING → one terminal state. Terminal states never regress or re-run.
     * Client disconnect → CANCELLED; process exit or lost lease/fence → INTERRUPTED; execution error → FAILED.
     */
    public enum TurnStatus {
        ACCEPTED(false),
        RUNNING(false),
        COMPLETED(true),
        FAILED(true),
        CANCELLED(true),
        INTERRUPTED(true);

        private final boolean terminal;

        TurnStatus(boolean terminal) {
            this.terminal = terminal;
        }

        public boolean terminal() {
            return terminal;
        }
    }

    /** A request to accept a new turn; {@code requestId} is the Idempotency-Key or a server-generated id. */
    public record TurnCommand(MemoryScope scope, String conversationId, String requestId, String text) {
        public TurnCommand {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(conversationId, "conversationId");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(text, "text");
        }
    }

    /** Right to execute one turn; every write carries the fence so a stale executor cannot overwrite. */
    public record TurnTicket(MemoryScope scope, String conversationId, String turnId, long fence) {
        public TurnTicket {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(conversationId, "conversationId");
            Objects.requireNonNull(turnId, "turnId");
        }
    }

    /** Token usage; {@code observed=false} means the provider reported none, which is not the same as zero. */
    public record TokenUsage(boolean observed, long promptTokens, long completionTokens, long totalTokens) {
        public static TokenUsage notObserved() {
            return new TokenUsage(false, 0, 0, 0);
        }
    }

    /** Result of accepting a turn; {@code replay=true} returns a completed turn's stored answer and usage. */
    public record AcceptedTurn(TurnTicket ticket, boolean replay, String answer, TokenUsage usage) {
        public AcceptedTurn {
            Objects.requireNonNull(ticket, "ticket");
            usage = usage == null ? TokenUsage.notObserved() : usage;
        }
    }

    /** One durable message with a stable id and per-conversation sequence. */
    public record StoredMessage(String id, long sequence, LlmClient.Message payload, SourceType source,
            TurnStatus status, Instant occurredAt) {
        public StoredMessage {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(source, "source");
        }
    }

    /** Page of original messages in ascending sequence; {@code nextBeforeSequence} fetches older ones. */
    public record HistoryPage(List<StoredMessage> items, Long nextBeforeSequence, boolean hasMore) {
        public HistoryPage {
            items = List.copyOf(items);
        }
    }

    /** Bounded recent window as cached, validated against the conversation revision. */
    public record HistoryWindow(List<StoredMessage> items, boolean hasMore, long revision) {
        public HistoryWindow {
            items = List.copyOf(items);
        }
    }
}
