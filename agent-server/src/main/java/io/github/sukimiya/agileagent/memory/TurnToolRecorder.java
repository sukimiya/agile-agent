package io.github.sukimiya.agileagent.memory;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.memory.ToolEventRepository.Status;
import io.github.sukimiya.agileagent.service.LlmClient;
import reactor.core.publisher.Mono;

/**
 * Records a turn's tool calls with provenance: capability, tool name, call id, redacted arguments, redacted
 * result or error type, source type ({@link SourceType#TOOL_RESULT}) and times. The start record is written
 * before the provider executes and is fenced: if it cannot be written the tool is not executed
 * ({@link TurnLifecycle.ExecutionRightLostException} when the turn was lost, {@code STORAGE_UNAVAILABLE} when the
 * database failed). If an executed call's outcome cannot be recorded the caller must stop the turn and must not
 * re-execute it; the call stays open and is marked UNCERTAIN by the turn's terminal write. Records are subject to
 * {@code app.memory.max-message-bytes} (rejected, never truncated).
 */
@Component
public class TurnToolRecorder {

    static final int MAX_NAME = 128;

    private final ToolEventRepository events;
    private final MemoryProperties props;

    public TurnToolRecorder(ToolEventRepository events, MemoryProperties props) {
        this.events = events;
        this.props = props;
    }

    public Mono<Void> started(TurnTicket ticket, String capability, LlmClient.ToolCall call) {
        return Mono.defer(() -> {
            var arguments = ToolRecordSanitizer.sanitize(call.argumentsJson());
            if (arguments != null) {
                props.checkMessageSize(arguments);
            }
            return events.upsertStarted(ticket, bounded(capability), bounded(call.name()), callKey(call.id()),
                            arguments, SourceType.TOOL_RESULT)
                    .onErrorMap(TurnToolRecorder::storageFailure)
                    .flatMap(owned -> owned ? Mono.<Void>empty()
                            : Mono.<Void>error(new TurnLifecycle.ExecutionRightLostException()));
        });
    }

    public Mono<Void> finished(TurnTicket ticket, String capability, String callId, String result, boolean success) {
        return finished(ticket, capability, callId, result, success, success ? null : "TOOL_FAILED");
    }

    /** Records the outcome; fails with {@code STORAGE_UNAVAILABLE} (or {@code MESSAGE_TOO_LARGE}) if it cannot. */
    public Mono<Void> finished(TurnTicket ticket, String capability, String callId, String result, boolean success,
            String errorType) {
        return Mono.defer(() -> {
            var stored = ToolRecordSanitizer.sanitize(result);
            if (stored != null) {
                props.checkMessageSize(stored);
            }
            return events.finish(ticket, callKey(callId), success ? Status.SUCCEEDED : Status.FAILED, stored,
                            errorType == null ? null : bounded(errorType, 64))
                    .onErrorMap(TurnToolRecorder::storageFailure)
                    .flatMap(recorded -> recorded ? Mono.<Void>empty()
                            : Mono.<Void>error(new MemoryException(ErrorCode.STORAGE_UNAVAILABLE)));
        });
    }

    public Mono<Void> markUncertain(TurnTicket ticket, String callId) {
        return Mono.defer(() -> events.markUncertain(ticket, callKey(callId)))
                .onErrorMap(TurnToolRecorder::storageFailure)
                .then();
    }

    private static Throwable storageFailure(Throwable error) {
        return error instanceof MemoryException || error instanceof TurnLifecycle.ExecutionRightLostException
                ? error : new MemoryException(ErrorCode.STORAGE_UNAVAILABLE, error);
    }

    private static String bounded(String value) {
        return bounded(value, MAX_NAME);
    }

    private static String bounded(String value, int max) {
        var text = value == null ? "" : value;
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** Call ids longer than the column are stored as a stable digest so they still identify one call. */
    static String callKey(String callId) {
        var id = callId == null ? "" : callId;
        if (id.length() <= MAX_NAME) {
            return id;
        }
        return "sha256:" + HexFormat.of().formatHex(TurnLifecycle.digest(id.getBytes(StandardCharsets.UTF_8)));
    }
}
