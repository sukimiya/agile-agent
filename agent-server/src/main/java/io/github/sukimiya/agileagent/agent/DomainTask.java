package io.github.sukimiya.agileagent.agent;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.memory.context.ModelWindow;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

/**
 * One unit of work routed to a single domain agent. {@code ticket} is the accepted turn the work belongs to;
 * tool calls are recorded against it, and a task without a ticket never executes tools. {@code window} is the
 * model window its tool loop is budgeted to; {@code null} means no budget. {@code background} is the candidate
 * recent conversation (completed turns, oldest first, never the current turn) that the domain agent fits to its
 * own budget; {@code backgroundTruncated} says older turns were already left out when it was assembled.
 */
public record DomainTask(String taskId, String sessionId, String roleCode,
                          String userMessage, @JsonIgnore String accessToken, IdentityContext identity,
                          @JsonIgnore TurnTicket ticket, @JsonIgnore ModelWindow window,
                          @JsonIgnore List<LlmClient.Message> background, @JsonIgnore boolean backgroundTruncated) {

    public DomainTask {
        background = background == null ? List.of() : List.copyOf(background);
    }

    /** A budgeted task without background. */
    public DomainTask(String taskId, String sessionId, String roleCode, String userMessage, String accessToken,
                      IdentityContext identity, TurnTicket ticket, ModelWindow window) {
        this(taskId, sessionId, roleCode, userMessage, accessToken, identity, ticket, window, List.of(), false);
    }

    /** A task with no context budget for its tool loop (the M1 behaviour). */
    public DomainTask(String taskId, String sessionId, String roleCode, String userMessage, String accessToken,
                      IdentityContext identity, TurnTicket ticket) {
        this(taskId, sessionId, roleCode, userMessage, accessToken, identity, ticket, null);
    }

    /** A task outside any accepted turn (tests and tool-free use only): its tool calls are refused. */
    public DomainTask(String taskId, String sessionId, String roleCode, String userMessage, String accessToken) {
        this(taskId, sessionId, roleCode, userMessage, accessToken,
                new IdentityContext(sessionId, null, null, null, null, null,
                        Set.of(), Set.of(), Set.of(), false, false), null);
    }

    @Override
    public String toString() {
        return "DomainTask[taskId=" + taskId + ", sessionId=" + sessionId + ", roleCode=" + roleCode
                + ", userMessage=" + userMessage + ", identity=" + identity + "]";
    }
}
