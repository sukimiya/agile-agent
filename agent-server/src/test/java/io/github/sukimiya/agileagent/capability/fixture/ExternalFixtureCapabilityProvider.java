package io.github.sukimiya.agileagent.capability.fixture;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;
import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.CapabilityDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;
import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import reactor.core.publisher.Mono;

/** Test fixture provider deliberately limited to the public SPI and runtime libraries. */
public final class ExternalFixtureCapabilityProvider implements AgentCapabilityProvider {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final boolean duplicateToolDefinitions;

    public ExternalFixtureCapabilityProvider() {
        this(false);
    }

    public ExternalFixtureCapabilityProvider(boolean duplicateToolDefinitions) {
        this.duplicateToolDefinitions = duplicateToolDefinitions;
    }

    @Override
    public String code() {
        return "FIXTURE";
    }

    @Override
    public String displayName() {
        return "External fixture";
    }

    @Override
    public String routingDescription() {
        return "Test-only SPI provider";
    }

    @Override
    public Mono<CapabilityDefinition> definition(AgentExecutionContext context) {
        var valueSchema = Map.of("type", "string");
        var schema = Map.of("type", "object", "properties", Map.of("value", valueSchema),
                "required", List.of("value"));
        var tool = new ToolDefinition("returnValue", "Return one fixture value", JSON.valueToTree(schema));
        var tools = duplicateToolDefinitions ? List.of(tool, tool) : List.of(tool);
        return Mono.just(new CapabilityDefinition("Return the supplied fixture value.", tools));
    }

    @Override
    public Mono<ToolResult> execute(ToolInvocation invocation, AgentExecutionContext context) {
        if (!"returnValue".equals(invocation.toolName())) {
            return Mono.just(new ToolResult(false, "unknown fixture tool"));
        }
        String value = invocation.arguments().path("value").asText();
        return Mono.just(new ToolResult(true, "fixture:" + value));
    }
}
