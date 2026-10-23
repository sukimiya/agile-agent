package io.github.sukimiya.agileagent.memory;

import java.nio.charset.StandardCharsets;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import reactor.core.publisher.Mono;

/**
 * Collects one turn's assistant output. {@link #delta} persists a fenced checkpoint once 5 s have passed or 8 KiB
 * of new UTF-8 text accumulated since the last one (whichever first); {@link #completed} only stages the final
 * text and usage, which {@link TurnLifecycle} commits after the upstream completes normally. Callers must
 * subscribe to the returned Monos in order (e.g. {@code concatMap}).
 */
public final class TurnRecorder {

    private final TurnTicket ticket;
    private final BiFunction<TurnTicket, String, Mono<Boolean>> checkpointer;
    private final MemoryProperties props;
    private final LongSupplier nanoTime;

    private final StringBuilder text = new StringBuilder();
    private int pendingBytes;
    private long lastCheckpointNanos;
    private String completedText;
    private TokenUsage usage = TokenUsage.notObserved();
    private boolean completed;

    TurnRecorder(TurnTicket ticket, BiFunction<TurnTicket, String, Mono<Boolean>> checkpointer,
            MemoryProperties props, LongSupplier nanoTime) {
        this.ticket = ticket;
        this.checkpointer = checkpointer;
        this.props = props;
        this.nanoTime = nanoTime;
        this.lastCheckpointNanos = nanoTime.getAsLong();
    }

    public TurnTicket ticket() {
        return ticket;
    }

    /** Appends streamed text; may persist a checkpoint. Fails if the turn lost its execution right. */
    public Mono<Void> delta(String delta) {
        return Mono.defer(() -> {
            String snapshot;
            synchronized (this) {
                if (delta == null || delta.isEmpty()) {
                    return Mono.empty();
                }
                text.append(delta);
                pendingBytes += delta.getBytes(StandardCharsets.UTF_8).length;
                var elapsed = nanoTime.getAsLong() - lastCheckpointNanos;
                if (pendingBytes < props.getCheckpointBytes() && elapsed < props.getCheckpointInterval().toNanos()) {
                    return Mono.empty();
                }
                snapshot = text.toString();
            }
            props.checkMessageSize(snapshot);
            return checkpointer.apply(ticket, snapshot).flatMap(owned -> {
                if (!owned) {
                    return Mono.error(new TurnLifecycle.ExecutionRightLostException());
                }
                synchronized (this) {
                    pendingBytes = 0;
                    lastCheckpointNanos = nanoTime.getAsLong();
                }
                return Mono.empty();
            });
        });
    }

    /** Stages the final text and usage (latest call wins); committed only when the upstream completes. */
    public Mono<Void> completed(String fullText, TokenUsage usage) {
        return Mono.fromRunnable(() -> {
            synchronized (this) {
                this.completedText = fullText == null ? text.toString() : fullText;
                this.usage = usage == null ? TokenUsage.notObserved() : usage;
                this.completed = true;
            }
        });
    }

    synchronized boolean isCompleted() {
        return completed;
    }

    synchronized TokenUsage usage() {
        return usage;
    }

    /** Final text when completed, otherwise everything streamed so far. */
    synchronized String text() {
        return completed ? completedText : text.toString();
    }
}
