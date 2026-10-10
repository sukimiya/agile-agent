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
        when(catalog.compactIndex(any())).thenReturn(Mono.just("doc-1 | Stage API | stage 用法\n"));
        var provider = new KnowledgeCapabilityProvider(catalog);
        var tokenReads = new AtomicInteger();

        var definition = provider.definition(context(tokenReads)).block();

        assertThat(definition.systemPrompt()).contains("doc-1 | Stage API | stage 用法");
        assertThat(definition.tools()).extracting(t -> t.name()).containsExactly("readKnowledgeDoc");
        assertThat(definition.tools().getFirst().parametersSchema().path("properties").path("id").path("enum").toString())
                .isEqualTo("[\"doc-1\"]");
        assertThat(tokenReads).hasValue(0);
        when(catalog.compactIndex(any())).thenReturn(Mono.just("doc-2 | Updated | Current\n"));
        var updated = provider.definition(context(tokenReads)).block();
        assertThat(updated.systemPrompt()).contains("doc-2 | Updated | Current");
        assertThat(updated.tools().getFirst().parametersSchema().path("properties").path("id").path("enum").toString())
                .isEqualTo("[\"doc-2\"]");
        verify(catalog, times(2)).compactIndex(any());
    }

    @Test
    void executesReadKnowledgeDocAndReturnsOnlyDocumentContent() {
        KnowledgeCatalog catalog = mock(KnowledgeCatalog.class);
        when(catalog.content(eq("doc-1"), any())).thenReturn(Mono.just("# Approved\ncontent"));
        var provider = new KnowledgeCapabilityProvider(catalog);
        var tokenReads = new AtomicInteger();
        var invocation = new ToolInvocation("readKnowledgeDoc", "call-1",
                JsonNodeFactory.instance.objectNode().put("id", "doc-1"));

        var result = provider.execute(invocation, context(tokenReads)).block();

        assertThat(result.success()).isTrue();
        assertThat(result.displayResult()).isEqualTo("# Approved\ncontent");
        assertThat(tokenReads).hasValue(0);
        verify(catalog).content(eq("doc-1"), any());
    }

    @Test
    void definitionAndReadUseTheLatestExecutionIdentity() {
        KnowledgeCatalog catalog = mock(KnowledgeCatalog.class);
        when(catalog.compactIndex(any())).thenAnswer(call -> Mono.just(
                "tenant-1".equals(call.getArgument(0, IdentityContext.class).tenantId())
                        ? "tenant-doc | Tenant | visible\n" : ""));
        when(catalog.content(eq("tenant-doc"), any())).thenAnswer(call -> Mono.just(
                "tenant-1".equals(call.getArgument(1, IdentityContext.class).tenantId())
                        ? "tenant content" : ""));
        var provider = new KnowledgeCapabilityProvider(catalog);
        var context1 = contextWithIdentity("tenant-1");
        var context2 = contextWithIdentity("tenant-2");

        var first = provider.definition(context1).block();
        var second = provider.definition(context2).block();
        var invocation = new ToolInvocation("readKnowledgeDoc", "call-1",
                JsonNodeFactory.instance.objectNode().put("id", "tenant-doc"));

        assertThat(first.systemPrompt()).contains("tenant-doc |");
        assertThat(second.systemPrompt()).doesNotContain("tenant-doc |");
        assertThat(second.tools().getFirst().parametersSchema().path("properties").path("id").has("enum"))
                .isFalse();
        assertThat(provider.execute(invocation, context2).block().success()).isFalse();
        verify(catalog).compactIndex(context1.identity());
        verify(catalog).compactIndex(context2.identity());
        verify(catalog).content("tenant-doc", context2.identity());
    }

    private static AgentExecutionContext contextWithIdentity(String tenantId) {
        return new AgentExecutionContext(new IdentityContext("user-7", "Lin", tenantId, "Tenant",
                null, null, Set.of("member"), Set.of(), Set.of(), false, false),
                "conversation-9", () -> "secret-token");
    }
}
