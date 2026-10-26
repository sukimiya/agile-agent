package io.github.sukimiya.agileagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.memory.context.ContextAssembler;
import io.github.sukimiya.agileagent.memory.context.ContextBudget;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.memory.recall.ChunkIndexer;
import io.github.sukimiya.agileagent.memory.recall.ChunkRepository;
import io.github.sukimiya.agileagent.memory.recall.FullTextSearch;
import io.github.sukimiya.agileagent.memory.recall.MemoryRetriever;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService;
import io.github.sukimiya.agileagent.memory.summary.SummaryRepository;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver.ResolvedModel;
import io.github.sukimiya.agileagent.spi.capability.ToolDefinition;
import reactor.core.publisher.Flux;

/**
 * recall-01 through a whole turn with a fake model: a fact from another conversation of the same user reaches the
 * routing and answering calls as a labelled user reference message only when cross-conversation recall is on;
 * recall runs once per turn.
 */
class OrchestratorRecallTest {

    static final Duration WAIT = Duration.ofSeconds(10);
    static final String PROBE = "我的车牌尾号是多少？只回答数字；如果不知道就回答“不知道”。";

    final MemoryTestStack stack = MemoryTestStack.h2();
    final List<List<LlmClient.Message>> calls = new CopyOnWriteArrayList<>();
    final LlmClient llm = new LlmClient() {
        @Override
        public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools) {
            return chatStream(messages, tools, new CallOptions(null));
        }

        @Override
        public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools, CallOptions options) {
            return Flux.defer(() -> {
                calls.add(List.copyOf(messages));
                if (messages.getFirst().content().contains("路由模块")) {
                    return Flux.just(new LlmEvent.Completed("[]"));
                }
                return Flux.just(new LlmEvent.ContentDelta("6"), new LlmEvent.Completed("6"));
            });
        }
    };

    @AfterEach
    void tearDown() {
        stack.close();
    }

    @Test
    void crossConversationRecallReachesTheModelOnlyWhenEnabled() {
        var db = stack.fixture().databaseClient();
        var worker = new MemoryJobWorker(db, () -> false);
        var chunks = new ChunkRepository(db, stack.conversations());
        new ChunkIndexer(db, stack.fixture().connectionFactory(), chunks, worker, stack.props());
        var settings = new MemorySettingsService(db);
        var retriever = new MemoryRetriever(db, stack.props(), settings, chunks,
                new SummaryRepository(db, stack.conversations()), FullTextSearch.forDatabase(db), stack.metrics());
        var budget = new ContextBudget(stack.props());
        var registry = new CapabilityRegistry(List.of());
        var orchestrator = new Orchestrator(new InProcessTaskDispatcher(new DomainAgent(registry,
                stack.toolRecorder(), budget)), registry, stack.lifecycle(), stack.history(),
                new ContextAssembler(stack.history(), budget, stack.metrics(), retriever));

        var earlier = MemoryTestStack.newConversationId();
        var fact = stack.accept(earlier, "记一下，我的车牌尾号是 6。").ticket();
        assertThat(stack.finalizer().finish(fact, TurnStatus.COMPLETED, "好的，已记下").block(WAIT)).isTrue();
        worker.runPending(10).block(WAIT);

        var now = MemoryTestStack.newConversationId();
        var model = new ResolvedModel(llm, 32_768, 4_096);
        orchestrator.chat(stack.accept(now, PROBE), "tok", null, model).collectList().block(WAIT);
        assertThat(calls).isNotEmpty().allSatisfy(c -> assertThat(c).noneMatch(m -> m.content() != null
                && m.content().contains("车牌尾号是 6")));

        calls.clear();
        settings.update(MemoryTestStack.ALICE, true).block(WAIT);
        orchestrator.chat(stack.accept(now, PROBE), "tok", null, model).collectList().block(WAIT);

        assertThat(calls).hasSizeGreaterThanOrEqualTo(2);
        for (var call : calls) {
            var refs = call.stream().filter(m -> "user".equals(m.role())
                    && m.content().startsWith(ContextAssembler.RECALL_HEADER)).toList();
            assertThat(refs).singleElement().satisfies(m -> assertThat(m.content())
                    .contains("记一下，我的车牌尾号是 6。").contains("其他会话"));
            assertThat(call.stream().filter(m -> "system".equals(m.role())))
                    .noneMatch(m -> m.content().contains("车牌尾号是 6"));
        }
        assertThat(stack.meters().find("memory.recall.latency").timer().count()).isEqualTo(2);
    }
}
