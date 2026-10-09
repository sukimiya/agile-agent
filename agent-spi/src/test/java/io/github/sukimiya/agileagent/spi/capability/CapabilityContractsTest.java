package io.github.sukimiya.agileagent.spi.capability;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

class CapabilityContractsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void capabilityDefinitionTakesAnImmutableSnapshotOfTools() {
        var tools = new ArrayList<>(List.of(tool("read")));
        var definition = new CapabilityDefinition("Read documents", tools);

        tools.clear();

        assertThat(definition.tools()).extracting(ToolDefinition::name).containsExactly("read");
        assertThatThrownBy(() -> definition.tools().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatNullPointerException().isThrownBy(() -> new CapabilityDefinition("prompt", null));
        assertThatNullPointerException().isThrownBy(() -> new CapabilityDefinition(null, List.of()));
    }

    @Test
    void toolSchemaCannotBeChangedThroughInputOrAccessor() {
        var schema = mapper.createObjectNode();
        schema.putObject("properties").putObject("query").put("type", "string");
        var definition = new ToolDefinition("read", "Read documents", schema);

        ((ObjectNode) schema.path("properties").path("query")).put("type", "number");
        ((ObjectNode) definition.parametersSchema().path("properties").path("query")).put("type", "boolean");

        assertThat(definition.parametersSchema().path("properties").path("query").path("type").asText())
                .isEqualTo("string");
    }

    @Test
    void invocationArgumentsCannotBeChangedThroughInputOrAccessor() {
        var arguments = mapper.createObjectNode().put("query", "approved");
        var invocation = new ToolInvocation("read", "call-1", arguments);

        arguments.put("query", "changed");
        ((ObjectNode) invocation.arguments()).put("query", "changed again");

        assertThat(invocation.arguments().path("query").asText()).isEqualTo("approved");
    }

    @Test
    void rejectsMissingOrBlankToolAndCallIdentifiers() {
        for (String identifier : new String[] {null, "", " \t"}) {
            assertThatIllegalArgumentException().isThrownBy(() -> tool(identifier));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ToolInvocation(identifier, "call-1", mapper.createObjectNode()));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ToolInvocation("read", identifier, mapper.createObjectNode()));
        }
    }

    @Test
    void rejectsMissingPayloads() {
        assertThatNullPointerException().isThrownBy(() -> new ToolDefinition("read", null, mapper.createObjectNode()));
        assertThatNullPointerException().isThrownBy(() -> new ToolDefinition("read", "Read documents", null));
        assertThatNullPointerException().isThrownBy(() -> new ToolInvocation("read", "call-1", null));
        assertThatNullPointerException().isThrownBy(() -> new ToolResult(false, null));
    }

    @Test
    void tokenAccessorStaysLazyAndIsExcludedFromLoggingAndJson() throws Exception {
        var resolutions = new AtomicInteger();
        String token = "secret-token-sentinel";
        Supplier<String> accessor = new Supplier<>() {
            @Override
            public String get() {
                resolutions.incrementAndGet();
                return token;
            }

            @Override
            public String toString() {
                return token;
            }
        };
        var context = new AgentExecutionContext(identity(), "conversation-1", accessor);

        assertThat(context.toString()).contains("conversation-1").doesNotContain(token);
        assertThat(mapper.writeValueAsString(context)).contains("conversation-1")
                .doesNotContain(token, "accessTokenAccessor");
        assertThat(resolutions.get()).isZero();
        assertThat(context.accessTokenAccessor().get()).isEqualTo(token);
        assertThat(resolutions.get()).isEqualTo(1);
    }

    @Test
    void rejectsIncompleteExecutionContextWithoutResolvingTokens() {
        Supplier<String> accessor = () -> { throw new AssertionError("Token must stay unresolved"); };
        assertThatNullPointerException().isThrownBy(() -> new AgentExecutionContext(null, "conversation-1", accessor));
        assertThatNullPointerException().isThrownBy(() -> new AgentExecutionContext(identity(), "conversation-1", null));
        for (String conversationId : new String[] {null, "", " \t"}) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new AgentExecutionContext(identity(), conversationId, accessor));
        }
    }

    private ToolDefinition tool(String name) {
        return new ToolDefinition(name, "Read documents", mapper.createObjectNode().put("type", "object"));
    }

    private static IdentityContext identity() {
        return new IdentityContext("subject-1", "User", null, null, null, null,
                Set.of(), Set.of(), Set.of(), false, false);
    }
}
