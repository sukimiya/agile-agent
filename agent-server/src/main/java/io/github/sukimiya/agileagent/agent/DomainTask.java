package io.github.sukimiya.agileagent.agent;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

/** One unit of work routed to a single domain agent. */
public record DomainTask(String taskId, String sessionId, String roleCode,
                          String userMessage, @JsonIgnore String accessToken, IdentityContext identity) {

    public DomainTask(String taskId, String sessionId, String roleCode, String userMessage, String accessToken) {
        this(taskId, sessionId, roleCode, userMessage, accessToken,
                new IdentityContext(sessionId, null, null, null, null, null,
                        Set.of(), Set.of(), Set.of(), false, false));
    }

    @Override
    public String toString() {
        return "DomainTask[taskId=" + taskId + ", sessionId=" + sessionId + ", roleCode=" + roleCode
                + ", userMessage=" + userMessage + ", identity=" + identity + "]";
    }
}
