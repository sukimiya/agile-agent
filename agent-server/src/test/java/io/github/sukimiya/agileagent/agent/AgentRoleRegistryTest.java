package io.github.sukimiya.agileagent.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.knowledge.KnowledgeCatalog;
import io.github.sukimiya.agileagent.service.ApiCatalog;
import io.github.sukimiya.agileagent.service.LlmClient;

class AgentRoleRegistryTest {

    private final ApiCatalog apiCatalog = mock(ApiCatalog.class);
    private final KnowledgeCatalog knowledgeCatalog = mock(KnowledgeCatalog.class);
    private final AgentRoleRegistry registry = new AgentRoleRegistry(apiCatalog, knowledgeCatalog);

    @Test
    void allSixRolesArePresent() {
        assertThat(registry.all()).extracting(AgentRole::code)
                .containsExactlyInAnyOrder("MES", "CRM", "WMS", "QMS", "SRM", "KNOWLEDGE");
    }

    @Test
    void byCodeIsCaseInsensitiveAndReturnsEmptyForUnknown() {
        assertThat(registry.byCode("mes")).isPresent();
        assertThat(registry.byCode("MES")).isPresent();
        assertThat(registry.byCode("nope")).isEmpty();
    }

    @Test
    void apiRoleIndexDelegatesToApiCatalogPerServiceSlice() {
        when(apiCatalog.compactIndex("MES")).thenReturn("MES | x | GET /a | d\n");

        var role = registry.byCode("MES").orElseThrow();

        assertThat(role.index().get()).isEqualTo("MES | x | GET /a | d\n");
    }

    @Test
    void knowledgeRoleIndexDelegatesToKnowledgeCatalog() {
        when(knowledgeCatalog.compactIndex()).thenReturn("doc-1 | 标题 | 摘要\n");

        var role = registry.byCode("KNOWLEDGE").orElseThrow();

        assertThat(role.index().get()).isEqualTo("doc-1 | 标题 | 摘要\n");
    }

    @Test
    void apiRolesExposeGetApiDetailAndCallEnterpriseApiToolsOnly() {
        var role = registry.byCode("CRM").orElseThrow();

        assertThat(role.tools()).extracting(LlmClient.ToolDefinition::name)
                .containsExactlyInAnyOrder("getApiDetail", "callEnterpriseApi");
    }

    @Test
    void knowledgeRoleExposesReadKnowledgeDocToolOnly() {
        var role = registry.byCode("KNOWLEDGE").orElseThrow();

        assertThat(role.tools()).extracting(LlmClient.ToolDefinition::name)
                .containsExactly("readKnowledgeDoc");
    }

    @Test
    void routingCatalogHasOneLinePerRoleWithDisplayName() {
        var catalog = registry.routingCatalog();

        assertThat(catalog.lines().count()).isEqualTo(6);
        assertThat(catalog).contains("MES | 制造执行系统", "KNOWLEDGE |");
    }
}
