package io.github.sukimiya.agileagent.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import io.github.sukimiya.agileagent.config.KnowledgeProperties;
import io.github.sukimiya.agileagent.config.MdcContextConfig;
import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.knowledge.KnowledgeCatalog;
import io.github.sukimiya.agileagent.knowledge.KnowledgeCapabilityProvider;
import io.github.sukimiya.agileagent.memory.MemoryTestDoubles;
import io.github.sukimiya.agileagent.service.LlmClient;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Verifies that log events emitted anywhere during one Orchestrator.chat() call — across
 * routing, dispatch, and domain-agent tool execution, which may run on different threads —
 * carry a consistent traceId (via MDC bridged from Reactor Context) and that domain-agent
 * log events carry the correct role.
 */
class AgentLoggingTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger agentLogger;

    @BeforeAll
    static void enableContextPropagation() {
        MdcContextConfig.configure();
    }

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        agentLogger = (Logger) LoggerFactory.getLogger("io.github.sukimiya.agileagent");
        agentLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        agentLogger.detachAppender(appender);
    }

    @Test
    void allLogEventsShareTraceIdAndDomainAgentLogsCarryTheirRole(@TempDir Path libDir) throws Exception {
        String knowledgeId = "00000000-0000-0000-0000-000000000001";
        Files.writeString(libDir.resolve(knowledgeId + ".v1.md"), "# Stage API\nstage.set(key, value)", StandardCharsets.UTF_8);
        Files.writeString(libDir.resolve(knowledgeId + ".v1.meta.json"),
                "{\"title\":\"Stage API\",\"summary\":\"stage 用法\",\"approvedBy\":\"reviewer-1\",\"approvedAt\":\"2026-07-28T10:00:00\",\"scopeType\":\"GLOBAL\",\"tenantId\":null,\"tenantName\":null,\"orgId\":null,\"orgName\":null}", StandardCharsets.UTF_8);
        var knowledgeProps = new KnowledgeProperties();
        knowledgeProps.setLibDir(libDir.toString());
        var knowledgeRepository = mock(io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.class);
        when(knowledgeRepository.findApprovedArtifactPointers()).thenReturn(Flux.just(
                new io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.ApprovedArtifactPointer(
                        knowledgeId, "reviewer-1", 1L,
                        io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.ScopeType.GLOBAL,
                        null, null)));
        var knowledgeCatalog = new KnowledgeCatalog(knowledgeProps, knowledgeRepository,
                new io.github.sukimiya.agileagent.knowledge.KnowledgeAccessPolicy());

        LlmClient llm = mock(LlmClient.class);
        var toolCall = new LlmClient.ToolCall("c1", "readKnowledgeDoc", "{\"id\":\"" + knowledgeId + "\"}");
        when(llm.chatStream(any(), any()))
                .thenReturn(Flux.<LlmClient.LlmEvent>just(new LlmClient.LlmEvent.Completed("[\"KNOWLEDGE\"]"))
                        .subscribeOn(Schedulers.parallel())) // 1) routing, forced onto another thread
                .thenReturn(Flux.<LlmClient.LlmEvent>just(new LlmClient.LlmEvent.ToolCallsReady(List.of(toolCall)))
                        .subscribeOn(Schedulers.parallel())) // 2) domain round 1, another thread again
                .thenReturn(Flux.<LlmClient.LlmEvent>just(new LlmClient.LlmEvent.Completed("已读取文档"))) // 3) domain round 2
                .thenReturn(Flux.<LlmClient.LlmEvent>just(new LlmClient.LlmEvent.Completed("聚合回答"))); // 4) aggregate

        var capabilities = new CapabilityRegistry(List.of(new KnowledgeCapabilityProvider(knowledgeCatalog)));
        var domainAgent = new DomainAgent(capabilities);
        var dispatcher = new InProcessTaskDispatcher(domainAgent);
        var orchestrator = new Orchestrator(dispatcher, capabilities, MemoryTestDoubles.passThroughLifecycle(),
                MemoryTestDoubles.fixedHistory("stage.set 怎么用", List.of()));

        orchestrator.chat(MemoryTestDoubles.acceptedTurn("conv-1"), "sl-tok", null, llm)
                .subscribeOn(Schedulers.parallel())
                .collectList()
                .block();

        List<ILoggingEvent> events = appender.list;
        assertThat(events).isNotEmpty();
        assertThat(events).allSatisfy(e -> assertThat(e.getFormattedMessage()).doesNotContain("sl-tok"));

        Set<String> traceIds = events.stream()
                .map(e -> e.getMDCPropertyMap().get("traceId"))
                .collect(Collectors.toSet());
        assertThat(traceIds).hasSize(1);
        assertThat(traceIds.iterator().next()).isNotBlank();

        List<ILoggingEvent> domainAgentEvents = events.stream()
                .filter(e -> e.getLoggerName().contains("DomainAgent"))
                .toList();
        assertThat(domainAgentEvents).isNotEmpty();
        assertThat(domainAgentEvents).allSatisfy(e ->
                assertThat(e.getMDCPropertyMap().get("role")).isEqualTo("KNOWLEDGE"));
    }
}
