package io.github.sukimiya.agileagent.memory;

/**
 * Memory failure with a stable, client-visible error code. The message is the code itself; underlying causes
 * are kept for server logs only and must never be written to response bodies.
 */
public class MemoryException extends RuntimeException {

    public enum ErrorCode {
        INVALID_ARGUMENT(400),
        INVALID_CONVERSATION_ID(400),
        INVALID_IDEMPOTENCY_KEY(400),
        TENANT_REQUIRED(403),
        CONVERSATION_NOT_FOUND(404),
        MEMORY_ITEM_NOT_FOUND(404),
        MEMORY_ITEM_CONFLICT(409),
        IDEMPOTENCY_CONFLICT(409),
        TURN_IN_PROGRESS(409),
        TURN_TERMINATED(409),
        MESSAGE_TOO_LARGE(413),
        CONTEXT_BUDGET_EXCEEDED(413),
        STORAGE_UNAVAILABLE(503),
        INSTANCE_CONFLICT(503);

        private final int httpStatus;

        ErrorCode(int httpStatus) {
            this.httpStatus = httpStatus;
        }

        public int httpStatus() {
            return httpStatus;
        }
    }

    private final ErrorCode code;
    private final String turnId;
    private final String conversationId;

    public MemoryException(ErrorCode code) {
        this(code, null, null, null);
    }

    public MemoryException(ErrorCode code, Throwable cause) {
        this(code, null, null, cause);
    }

    private MemoryException(ErrorCode code, String turnId, String conversationId, Throwable cause) {
        super(code.name(), cause);
        this.code = code;
        this.turnId = turnId;
        this.conversationId = conversationId;
    }

    /**
     * An active turn already owns the conversation, or the same key's request is still running; the 409 response
     * carries the turn id and its conversation id (a client retrying a first request may not know the latter).
     */
    public static MemoryException turnInProgress(String turnId, String conversationId) {
        return new MemoryException(ErrorCode.TURN_IN_PROGRESS, turnId, conversationId, null);
    }

    public ErrorCode code() {
        return code;
    }

    /** Active turn id for {@link ErrorCode#TURN_IN_PROGRESS}; {@code null} otherwise. */
    public String turnId() {
        return turnId;
    }

    /** Conversation of the active turn for {@link ErrorCode#TURN_IN_PROGRESS}; {@code null} otherwise. */
    public String conversationId() {
        return conversationId;
    }
}
