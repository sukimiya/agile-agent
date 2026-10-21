package io.github.sukimiya.agileagent.memory;

/**
 * Memory failure with a stable, client-visible error code. The message is the code itself; underlying causes
 * are kept for server logs only and must never be written to response bodies.
 */
public class MemoryException extends RuntimeException {

    public enum ErrorCode {
        INVALID_CONVERSATION_ID(400),
        INVALID_IDEMPOTENCY_KEY(400),
        TENANT_REQUIRED(403),
        CONVERSATION_NOT_FOUND(404),
        IDEMPOTENCY_CONFLICT(409),
        TURN_IN_PROGRESS(409),
        TURN_TERMINATED(409),
        MESSAGE_TOO_LARGE(413),
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

    public MemoryException(ErrorCode code) {
        this(code, null, null);
    }

    public MemoryException(ErrorCode code, Throwable cause) {
        this(code, null, cause);
    }

    private MemoryException(ErrorCode code, String turnId, Throwable cause) {
        super(code.name(), cause);
        this.code = code;
        this.turnId = turnId;
    }

    /** An active turn already owns the conversation; the 409 response carries its id. */
    public static MemoryException turnInProgress(String turnId) {
        return new MemoryException(ErrorCode.TURN_IN_PROGRESS, turnId, null);
    }

    public ErrorCode code() {
        return code;
    }

    /** Active turn id for {@link ErrorCode#TURN_IN_PROGRESS}; {@code null} otherwise. */
    public String turnId() {
        return turnId;
    }
}
