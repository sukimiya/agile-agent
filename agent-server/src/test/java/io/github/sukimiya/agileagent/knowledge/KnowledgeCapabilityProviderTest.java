package io.github.sukimiya.agileagent.knowledge;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import reactor.core.publisher.Mono;

class KnowledgeCapabilityProviderTest {

    private static AgentExecutionContext context(AtomicInteger tokenReads) {
        return new AgentExecutionContext(new IdentityContext("user-7", "Lin", "tenant-1", "Tenant",
                "org-2", "Org", Set.of("member"), Set.of("org-2"), Set.of("org-2"), false, false),
                "conversation-9", () -> {
                    tokenReads.incrementAndGet();
                    return "secret-token";
                });
    }

    @Test
    void definitionBuildsKnowledgePromptAndReadToolFromCurrentCatalog() {
        KnowledgeCatalog catalog = mock(KnowledgeCatalog.class);
        when(catalog.compactIndex()).thenReturn("doc-1 | Stage API | stage 用法\n");
        var provider = new KnowledgeCapabilityProvider(catalog);
        var tokenReads = new AtomicInteger();

        var definition = provider.definition(context(tokenReads)).block();

        assertThat(definition.systemPrompt()).contains("doc-1 | Stage API | stage 用法");
        assertThat(definition.tools()).extracting(t -> t.name()).containsExactly("readKnowledgeDoc");
        assertThat(definition.tools().getFirst().parametersSchema().path("properties").path("id").path("enum").toString())
                .isEqualTo("[\"doc-1\"]");
        assertThat(tokenReads).hasValue(0);
        when(catalog.compactIndex()).thenReturn("doc-2 | Updated | Current\n");
        var updated = provider.definition(context(tokenReads)).block();
        assertThat(updated.systemPrompt()).contains("doc-2 | Updated | Current");
        assertThat(updated.tools().getFirst().parametersSchema().path("properties").path("id").path("enum").toString())
                .isEqualTo("[\"doc-2\"]");
        verify(catalog, times(2)).compactIndex();
    }

    @Test
    void executesReadKnowledgeDocAndReturnsOnlyDocumentContent() {
        KnowledgeCatalog catalog = mock(KnowledgeCatalog.class);
        when(catalog.content("doc-1")).thenReturn(Mono.just("# Approved\ncontent"));
        var provider = new KnowledgeCapabilityProvider(catalog);
        var tokenReads = new AtomicInteger();
        var invocation = new ToolInvocation("readKnowledgeDoc", "call-1",
                JsonNodeFactory.instance.objectNode().put("id", "doc-1"));

        var result = provider.execute(invocation, context(tokenReads)).block();

        assertThat(result.success()).isTrue();
        assertThat(result.displayResult()).isEqualTo("# Approved\ncontent");
        assertThat(tokenReads).hasValue(0);
        verify(catalog).content("doc-1");
    }
}
