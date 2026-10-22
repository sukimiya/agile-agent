package io.github.sukimiya.agileagent.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.github.sukimiya.agileagent.memory.MemoryException;
import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;

/**
 * Maps {@link MemoryException} to its stable HTTP status and body {@code {code, message[, turnId]}}. The body
 * never carries the underlying cause, SQL, paths or credentials; {@code turnId} appears only for
 * {@code TURN_IN_PROGRESS}. Errors after the SSE response is committed are reported in-stream by the
 * orchestrator instead.
 */
@RestControllerAdvice
public class MemoryExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MemoryExceptionHandler.class);

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MemoryError(String code, String message, String turnId) {}

    @ExceptionHandler(MemoryException.class)
    ResponseEntity<MemoryError> handle(MemoryException exception) {
        var code = exception.code();
        if (code.httpStatus() >= 500 && exception.getCause() != null) {
            log.warn("memory request failed code={}: {}", code,
                    NestedExceptionUtils.getMostSpecificCause(exception).toString());
        }
        return ResponseEntity.status(code.httpStatus())
                .body(new MemoryError(code.name(), message(code), exception.turnId()));
    }

    static String message(ErrorCode code) {
        return switch (code) {
            case INVALID_ARGUMENT -> "Request parameter is invalid";
            case INVALID_CONVERSATION_ID -> "Conversation id is invalid";
            case INVALID_IDEMPOTENCY_KEY -> "Idempotency-Key is invalid";
            case TENANT_REQUIRED -> "Tenant is required";
            case CONVERSATION_NOT_FOUND -> "Conversation not found";
            case IDEMPOTENCY_CONFLICT -> "Idempotency-Key was used for a different request";
            case TURN_IN_PROGRESS -> "A reply is already being generated for this conversation";
            case TURN_TERMINATED -> "This request already ended; retry with a new Idempotency-Key";
            case MESSAGE_TOO_LARGE -> "Message is too large";
            case STORAGE_UNAVAILABLE -> "Storage is temporarily unavailable";
            case INSTANCE_CONFLICT -> "Another service instance is active";
        };
    }
}
