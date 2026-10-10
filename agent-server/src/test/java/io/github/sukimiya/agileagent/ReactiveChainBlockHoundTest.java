package io.github.sukimiya.agileagent;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.sukimiya.agileagent.agent.AgentRoleRegistry;
import io.github.sukimiya.agileagent.agent.DomainAgent;
import io.github.sukimiya.agileagent.agent.InProcessTaskDispatcher;
import io.github.sukimiya.agileagent.agent.Orchestrator;
import io.github.sukimiya.agileagent.config.KnowledgeProperties;
import io.github.sukimiya.agileagent.knowledge.KnowledgeCatalog;
import io.github.sukimiya.agileagent.service.*;

import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class ReactiveChainBlockHoundTest {

    @BeforeAll
    static void installBlockHound() {
        BlockHound.install();
    }

    @Test
    void blockHoundDetectsSleepOnParallelScheduler() {
        StepVerifier.create(Mono.fromCallable(() -> {
                    Thread.sleep(1);
                    return "unreachable";
                }).subscribeOn(Schedulers.parallel()))
                .expectError(BlockingOperationError.class)
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void orchestratorChatChainHasNoBlockingCallsOnParallelScheduler(@TempDir Path libDir) throws Exception {
        Files.writeString(libDir.resolve("doc-1.md"), "# Stage API\nstage.set(key, value)", StandardCharsets.UTF_8);
        Files.writeString(libDir.resolve("doc-1.meta.json"),
                "{\"title\":\"Stage API\",\"summary\":\"stage 用法\"}", StandardCharsets.UTF_8);
        var knowledgeProps = new KnowledgeProperties();
        knowledgeProps.setLibDir(libDir.toString());
        var knowledgeCatalog = new KnowledgeCatalog(knowledgeProps);

        ApiCatalog apiCatalog = mock(ApiCatalog.class);
        when(apiCatalog.compactIndex(any())).thenReturn("MES | x | GET /a | d\n");
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ChatMemory memory = mock(ChatMemory.class);
        when(memory.get(any())).thenReturn(Mono.just(List.of()));
        when(memory.append(any(), anyList())).thenReturn(Mono.empty());

        LlmClient llm = mock(LlmClient.class);
        var toolCall = new LlmClient.ToolCall("c1", "readKnowledgeDoc", "{\"id\":\"doc-1\"}");
        when(llm.chatStream(any(), any())).thenReturn(
                        // 1) routing
                        Flux.<LlmClient.LlmEvent>just(new LlmClient.LlmEvent.Completed("[\"KNOWLEDGE\"]")))
                .thenReturn(
                        // 2) domain agent round 1: calls readKnowledgeDoc
                        Flux.<LlmClient.LlmEvent>just(new LlmClient.LlmEvent.ToolCallsReady(List.of(toolCall))))
                .thenReturn(
                        // 3) domain agent round 2: final domain answer
                        Flux.<LlmClient.LlmEvent>just(new LlmClient.LlmEvent.Completed("已读取文档")))
                .thenReturn(
                        // 4) aggregate
                        Flux.<LlmClient.LlmEvent>just(new LlmClient.LlmEvent.Completed("聚合回答"))
                                .subscribeOn(Schedulers.parallel()));

        var roles = new AgentRoleRegistry(apiCatalog, knowledgeCatalog);
        var domainAgent = new DomainAgent(llm, roles.capabilityRegistry(enterpriseApi));
        var dispatcher = new InProcessTaskDispatcher(domainAgent);
        var orchestrator = new Orchestrator(llm, dispatcher, roles, memory);

        StepVerifier.create(orchestrator.chat("conv-1", "sl-tok", "stage.set 怎么用")
                        .subscribeOn(Schedulers.parallel()))
                .expectNextCount(4) // thinking + tool_call + 聚合回答 + done
                .verifyComplete();
    }
}
