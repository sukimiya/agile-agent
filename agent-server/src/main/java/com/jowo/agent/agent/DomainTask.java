package com.jowo.agent.agent;

/** One unit of work routed to a single domain agent. */
public record DomainTask(String taskId, String sessionId, String roleCode,
                          String userMessage, String accessToken) {}
