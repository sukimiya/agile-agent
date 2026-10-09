package com.jowo.agent.agent;

import java.util.List;
import java.util.function.Supplier;

import com.jowo.agent.service.LlmClient;

/**
 * Configuration for one domain agent: a system-prompt template, its index slice
 * (API catalog per service, or the knowledge base catalog), and the tools it may call.
 * Roles differ only by configuration, not by class hierarchy.
 */
public record AgentRole(String code, String displayName, String routingBlurb,
                         Supplier<String> index, List<LlmClient.ToolDefinition> tools,
                         String promptTemplate) {

    public String systemPrompt() {
        return promptTemplate.formatted(index.get());
    }
}
