package io.github.sukimiya.agileagent.spi.capability;

import java.util.Objects;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

/** Execution scope with a lazy credential accessor excluded from JSON and logs. */
public record AgentExecutionContext(
        IdentityContext identity,
        String conversationId,
        @JsonIgnore Supplier<String> accessTokenAccessor) {

    public AgentExecutionContext {
        Objects.requireNonNull(identity, "identity must not be null");
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        Objects.requireNonNull(accessTokenAccessor, "accessTokenAccessor must not be null");
    }

    @Override
    public String toString() {
        return "AgentExecutionContext[identity=" + identity + ", conversationId=" + conversationId + "]";
    }
}
