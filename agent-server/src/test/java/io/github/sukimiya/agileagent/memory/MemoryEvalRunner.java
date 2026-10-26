package io.github.sukimiya.agileagent.memory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.agent.DomainAgent;
import io.github.sukimiya.agileagent.agent.InProcessTaskDispatcher;
import io.github.sukimiya.agileagent.agent.Orchestrator;
import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.memory.MemoryEvalCases.Case;
import io.github.sukimiya.agileagent.memory.MemoryEvalCases.Verdict;
import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.memory.context.ContextAssembler;
import io.github.sukimiya.agileagent.memory.context.ContextBudget;
import io.github.sukimiya.agileagent.memory.context.ModelWindow;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.memory.recall.ChunkIndexer;
import io.github.sukimiya.agileagent.memory.recall.ChunkRepository;
import io.github.sukimiya.agileagent.memory.recall.FullTextSearch;
import io.github.sukimiya.agileagent.memory.recall.MemoryRetriever;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService;
import io.github.sukimiya.agileagent.memory.recall.RecallCandidate;
import io.github.sukimiya.agileagent.memory.recall.RecallResult;
import io.github.sukimiya.agileagent.memory.summary.SummaryJobHandler;
import io.github.sukimiya.agileagent.memory.summary.SummaryRepository;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver.ResolvedModel;
import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;
import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.CapabilityDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;
import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Runs the eval set on a {@link MemoryTestStack} through the real M2a path: Orchestrator with the context budget
 * per model window, basic recall (chunk index, full-text and exact matching, cross-conversation switch) and turn
 * records written by the summary handler. Two separate outputs:
 * <ul>
 * <li>{@link #components}: engineering checks of the components each case names, driven by a deterministic fake
 * model, plus retrieval quality (Recall@K, source accuracy, retrieval latency); M2b components are
 * NOT_APPLICABLE, never FAIL.</li>
 * <li>{@link #answers}: the probe answer of each answer case from the given model, with a rule-based verdict, the
 * context input, first-token latency, recall per probe and the model cost per phase (turns, probes, record
 * building). Only a real model ({@code memory-eval-live}) gives an end-to-end baseline.</li>
 * </ul>
 * Every case runs under its own owner ({@code eval-<case>-<run>}), so cross-conversation recall only sees the
 * case's own conversations.
 */
final class MemoryEvalRunner {

    enum ComponentStatus { PASS, FAIL, NOT_APPLICABLE }

    record ComponentResult(String caseId, String category, String requires, String component,
            ComponentStatus status, String detail) {}

    record ComponentBaseline(String environment, String commit, Instant runAt, List<ComponentResult> results,
            RetrievalStats retrieval) {
        Map<ComponentStatus, Long> summary() {
            var out = new java.util.EnumMap<ComponentStatus, Long>(ComponentStatus.class);
            for (var status : ComponentStatus.values()) {
                out.put(status, results.stream().filter(r -> r.status() == status).count());
            }
            return out;
        }
    }

    /** Where an answer run came from; {@code live} is false for engineering runs with a fake model. */
    record RunInfo(boolean live, String model, String modelVersion, String endpointHost, String temperature,
            String contextInput, String commit, Instant startedAt, String environment) {}

    /** Prompt context of the probe turn: how history was supplied and how large it was. */
    record ContextInput(String mode, int injectedMessages, long estimatedTokens, Long promptTokens) {}

    /**
     * Recall of one probe: items returned (before the in-window filter), their reasons in order, whether the
     * expected source turn is among the first K ({@code null} when nothing should be recalled), how many items
     * are the source, whether any item came from another conversation, and whether it reached the prompt.
     */
    record RecallInfo(String caseId, int items, List<String> reasons, Boolean hitAtK, int correctItems,
            boolean crossConversation, boolean coverageLimited, double latencyMs, boolean referenceInjected) {}

    /** Retrieval quality over the recall cases of a run; percentiles by nearest rank over every recall call. */
    record RetrievalStats(int cases, int k, Double recallAtK, Double sourceAccuracy, Double p50Ms, Double p95Ms,
            int latencySamples, List<RecallInfo> perCase) {}

    /** Model calls and provider-reported tokens of one phase. */
    record PhaseCost(int calls, long promptTokens, long completionTokens, long totalTokens) {}

    /** Model cost of an answer run: case turns, probes, and turn-record building (D19). */
    record Cost(PhaseCost turns, PhaseCost probes, PhaseCost records, int recordTurns, int recordsWritten) {}

    /** Capability calls of the probe: with recent background, and continuing with a tool round. */
    record CapabilityInfo(int calls, int withBackground, int withToolResult) {}

    record AnswerResult(String caseId, String category, String requires, String probe, String answer,
            Verdict verdict, String expected, ContextInput context, long durationMs, List<String> turnStatuses,
            String error, Long firstTokenMs, RecallInfo recall, CapabilityInfo capability, String sourceRecord) {}

    record AnswerBaseline(RunInfo run, List<AnswerResult> results, RetrievalStats retrieval, Cost cost,
            Double firstTokenP50Ms, Double firstTokenP95Ms, long probePromptTokens, String settings) {}

    /** Retrieval quality at one weak-hit threshold ({@code app.memory.recall.min-matched-terms}, T5 Q3). */
    record ThresholdResult(int minMatchedTerms, RetrievalStats retrieval) {}

    static final String FULL_HISTORY = "FULL_HISTORY";
    /** M2a context: budgeted recent window per model window plus recalled original turns. */
    static final String M2A_CONTEXT = "M2A_BUDGET_RECALL";
    static final String INVENTORY = "EVAL_INVENTORY";
    static final int K = 5;
    private static final Duration TURN_WAIT = Duration.ofMinutes(4);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final MemoryTestStack stack;
    private final MemoryJobWorker worker;
    private final MemorySettingsService settings;
    private final SummaryRepository summaries;
    private final SummaryJobHandler summarizer;
    private final RecordingRetriever retriever;
    private final ContextBudget budget;
    private final AtomicReference<LlmClient> recordModel = new AtomicReference<>();
    /** Recall cases of the last answer run, kept to replay retrieval at other thresholds without model calls. */
    private final List<Replay> replays = new CopyOnWriteArrayList<>();

    private record Replay(Case c, MemoryScope scope, String conversationId, List<String> turnIds,
            AtomicReference<String> probeTurnId) {}

    MemoryEvalRunner(MemoryTestStack stack) {
        this.stack = stack;
        var db = stack.fixture().databaseClient();
        this.worker = new MemoryJobWorker(db, () -> false);
        var chunks = new ChunkRepository(db, stack.conversations());
        new ChunkIndexer(db, stack.fixture().connectionFactory(), chunks, worker, stack.props());
        this.settings = new MemorySettingsService(db);
        this.summaries = new SummaryRepository(db, stack.conversations());
        this.retriever = new RecordingRetriever(db, stack.props(), settings, chunks, summaries,
                FullTextSearch.forDatabase(db), stack.metrics());
        this.budget = new ContextBudget(stack.props());
        this.summarizer = new SummaryJobHandler(db, stack.fixture().connectionFactory(), summaries,
                stack.conversations(), tenant -> Mono.justOrEmpty(recordModel.get())
                        .map(llm -> new SummaryJobHandler.SummaryModel(llm, "eval-record-model", null, null)),
                worker, stack.props(), stack.metrics());
    }

    /** Recall results per turn and the latency of every recall call of the current run. */
    static final class RecordingRetriever extends MemoryRetriever {
        final Map<String, RecallResult> byTurn = new ConcurrentHashMap<>();
        final List<Double> latencies = new CopyOnWriteArrayList<>();

        RecordingRetriever(org.springframework.r2dbc.core.DatabaseClient db, MemoryProperties props,
                MemorySettingsService settings, ChunkRepository chunks, SummaryRepository summaries,
                FullTextSearch fullText, MemoryMetrics metrics) {
            super(db, props, settings, chunks, summaries, fullText, metrics);
        }

        @Override
        public Mono<RecallResult> recall(TurnTicket ticket, String query) {
            return super.recall(ticket, query).doOnNext(result -> {
                byTurn.put(ticket.turnId(), result);
                latencies.add(result.latency() == null ? 0.0 : result.latency().toNanos() / 1_000_000.0);
            });
        }

        void reset() {
            byTurn.clear();
            latencies.clear();
        }
    }

    private static MemoryScope newScope(Case c) {
        return new MemoryScope(MemoryTestStack.ALICE.tenantId(),
                "eval-" + c.id() + "-" + NonBlockingIds.uuid().substring(0, 8));
    }

    // ---------------------------------------------------------------- component status

    /** Engineering checks of the components each case names; M2b components are not applicable. */
    ComponentBaseline components(List<Case> cases, String environment) {
        retriever.reset();
        var results = new ArrayList<ComponentResult>();
        var recalls = new ArrayList<RecallInfo>();
        for (var c : cases) {
            Scenario scenario = null;
            String failure = null;
            if (c.components().stream().anyMatch(MemoryEvalCases.IMPLEMENTED::contains)) {
                try {
                    scenario = engineeringRun(c);
                    if (c.components().contains("recall") && scenario.recall() != null) {
                        recalls.add(scenario.recall());
                    }
                } catch (RuntimeException | AssertionError e) {
                    failure = "场景执行失败：" + describe(e);
                }
            }
            for (var component : c.components()) {
                if (!MemoryEvalCases.IMPLEMENTED.contains(component)) {
                    results.add(result(c, component, ComponentStatus.NOT_APPLICABLE,
                            "M2a 未实现，属于 " + phaseOf(component) + "；不计为失败"));
                } else if (failure != null) {
                    results.add(result(c, component, ComponentStatus.FAIL, failure));
                } else {
                    results.add(check(c, component, scenario));
                }
            }
        }
        return new ComponentBaseline(environment, commit(), Instant.now(), List.copyOf(results),
                stats(recalls, retriever.latencies));
    }

    /**
     * The weak-hit tuning hook (T5 Q3): retrieval quality of the recall cases at each {@code min-matched-terms}
     * value, on the engineering path. The configured value is restored afterwards.
     */
    List<ThresholdResult> thresholdSweep(List<Case> cases, List<Integer> thresholds) {
        var recall = stack.props().getRecall();
        int configured = recall.getMinMatchedTerms();
        var out = new ArrayList<ThresholdResult>();
        try {
            for (int t : thresholds) {
                recall.setMinMatchedTerms(t);
                retriever.reset();
                var infos = new ArrayList<RecallInfo>();
                for (var c : cases) {
                    if (c.components().contains("recall")) {
                        var s = engineeringRun(c);
                        if (s.recall() != null) {
                            infos.add(s.recall());
                        }
                    }
                }
                out.add(new ThresholdResult(t, stats(infos, retriever.latencies)));
            }
        } finally {
            recall.setMinMatchedTerms(configured);
            retriever.reset();
        }
        return List.copyOf(out);
    }

    /**
     * The weak-hit tuning hook on real data (T5 Q3): replays the probe retrieval of the last {@link #answers}
     * run's recall cases (turns and records written by that run's model) at each threshold. No model calls;
     * each replay is a fresh turn in the probe conversation, ended FAILED after the retrieval.
     */
    List<ThresholdResult> replaySweep(List<Integer> thresholds) {
        var recall = stack.props().getRecall();
        int configured = recall.getMinMatchedTerms();
        var out = new ArrayList<ThresholdResult>();
        try {
            for (int t : thresholds) {
                recall.setMinMatchedTerms(t);
                retriever.reset();
                var infos = new ArrayList<RecallInfo>();
                for (var r : replays) {
                    var ticket = stack.accept(r.scope(), r.conversationId(), NonBlockingIds.uuid(), r.c().probe())
                            .ticket();
                    try {
                        var result = retriever.recall(ticket, r.c().probe()).block(TURN_WAIT);
                        // The original probe turn is now history of the same conversation; it is not a source.
                        retriever.byTurn.put(ticket.turnId(), new RecallResult(result.items().stream()
                                .filter(i -> !i.turnId().equals(r.probeTurnId().get())).toList(),
                                result.coverageLimited(), result.latency()));
                        infos.add(recallInfo(r.c(), r.turnIds(), ticket.turnId(), List.of()));
                    } finally {
                        stack.finalizer().finish(ticket, MemoryModel.TurnStatus.FAILED, "").block(TURN_WAIT);
                    }
                }
                out.add(new ThresholdResult(t, stats(infos, retriever.latencies)));
            }
        } finally {
            recall.setMinMatchedTerms(configured);
            retriever.reset();
        }
        return List.copyOf(out);
    }

    private static String phaseOf(String component) {
        return switch (component) {
            case "memory-judgement" -> "M2b";
            default -> "后续阶段";
        };
    }

    private static ComponentResult result(Case c, String component, ComponentStatus status, String detail) {
        return new ComponentResult(c.id(), c.category(), c.requires(), component, status, detail);
    }

    /** What one engineering run of a case produced. */
    private record Scenario(MemoryScope scope, String conversationId, String probeConversationId,
            List<String> turnIds, String probeTurnId, List<String> sentTexts, List<CallLog> probeCalls,
            List<CallLog> allCalls, ModelWindow window, ErrorCode rejected, RecallInfo recall) {

        /** The probe's answer call (direct answer or aggregate), or null. */
        CallLog probeAnswer() {
            return probeCalls == null ? null
                    : probeCalls.stream().filter(call -> call.kind == CallKind.ANSWER).reduce((a, b) -> b).orElse(null);
        }
    }

    private Scenario engineeringRun(Case c) {
        var scope = newScope(c);
        var llm = new CountingLlm(new EngineeringLlm());
        var orchestrator = orchestrator();
        var conversation = MemoryTestStack.newConversationId();
        var window = window(c);
        if (c.components().contains("size-limit")) {
            var text = MemoryEvalCases.text(c.turns().getFirst());
            try {
                stack.accept(scope, conversation, NonBlockingIds.uuid(), text);
                return new Scenario(scope, conversation, null, List.of(), null, List.of(text), null, List.of(),
                        window, null, null);
            } catch (MemoryException e) {
                return new Scenario(scope, conversation, null, List.of(), null, List.of(text), null, List.of(),
                        window, e.code(), null);
            }
        }
        if (c.crossConversation()) {
            settings.update(scope, true).block(TURN_WAIT);
        }
        var engineering = (EngineeringLlm) llm.delegate;
        var turnIds = new ArrayList<String>();
        var sent = new ArrayList<String>();
        llm.phase.set(Phase.TURN);
        for (var turn : c.turns()) {
            var text = MemoryEvalCases.text(turn);
            engineering.routeToInventory.set(turn.tool());
            turnIds.add(execute(orchestrator, llm, scope, conversation, text, c).turnId());
            sent.add(text);
        }
        if (c.components().contains("summary")) {
            buildRecords(scope, conversation, llm);
        }
        String probeConversation = null;
        String probeTurn = null;
        List<CallLog> probeCalls = null;
        RecallInfo recall = null;
        if (c.probe() != null) {
            probeConversation = c.probeInNewConversation() ? MemoryTestStack.newConversationId() : conversation;
            engineering.routeToInventory.set(c.probeTool());
            llm.phase.set(Phase.PROBE);
            int before = llm.calls.size();
            probeTurn = execute(orchestrator, llm, scope, probeConversation, c.probe(), c).turnId();
            sent.add(c.probe());
            probeCalls = List.copyOf(llm.calls.subList(before, llm.calls.size()));
            recall = recallInfo(c, turnIds, probeTurn, probeCalls);
        }
        return new Scenario(scope, conversation, probeConversation, List.copyOf(turnIds), probeTurn,
                List.copyOf(sent), probeCalls, List.copyOf(llm.calls), window, null, recall);
    }

    private ModelWindow window(Case c) {
        return c.window() == null ? budget.windowFor(null, null)
                : budget.windowFor(c.window().contextWindowTokens(), c.window().maxOutputTokens());
    }

    /** Writes the turn records of every completed turn of the conversation (record phase); returns the count. */
    private int buildRecords(MemoryScope scope, String conversation, CountingLlm llm) {
        var previous = llm.phase.getAndSet(Phase.RECORD);
        recordModel.set(llm);
        try {
            var through = stack.rows("SELECT MAX(sequence) AS s FROM chat_messages WHERE conversation_id = ?",
                    conversation).getFirst().get("S");
            if (through != null) {
                // An invalid model output fails the attempt; the handler skips the turn at the configured limit.
                summarizer.summarize(scope, conversation, ((Number) through).longValue())
                        .retry(stack.props().getSummary().getMaxInvalidAttempts())
                        .onErrorResume(e -> Mono.empty())
                        .block(TURN_WAIT);
            }
            return records(scope, conversation);
        } finally {
            recordModel.set(null);
            llm.phase.set(previous);
        }
    }

    private int records(MemoryScope scope, String conversation) {
        return ((Number) stack.rows("SELECT COUNT(*) AS n FROM memory_summaries WHERE tenant_id = ? AND subject = ?"
                + " AND conversation_id = ?", scope.tenantId(), scope.subject(), conversation).getFirst().get("N"))
                .intValue();
    }

    private RecallInfo recallInfo(Case c, List<String> turnIds, String probeTurn, List<CallLog> probeCalls) {
        var result = retriever.byTurn.get(probeTurn);
        if (result == null) {
            return null;
        }
        var items = result.items();
        String source = c.recallSource() == null ? null : turnIds.get(c.recallSource());
        Boolean hit = source == null ? null
                : items.stream().limit(K).anyMatch(item -> item.turnId().equals(source));
        int correct = source == null ? 0 : (int) items.stream().filter(item -> item.turnId().equals(source)).count();
        boolean injected = probeCalls.stream().anyMatch(call -> call.messages.stream()
                .anyMatch(m -> "user".equals(m.role()) && m.content().startsWith(ContextAssembler.RECALL_HEADER)));
        return new RecallInfo(c.id(), items.size(), items.stream().map(RecallCandidate::reason).toList(), hit, correct,
                items.stream().anyMatch(RecallCandidate::crossConversation), result.coverageLimited(),
                result.latency() == null ? 0 : result.latency().toNanos() / 1_000_000.0, injected);
    }

    static RetrievalStats stats(List<RecallInfo> infos, List<Double> latencies) {
        var withSource = infos.stream().filter(i -> i.hitAtK() != null).toList();
        Double recallAtK = withSource.isEmpty() ? null
                : (double) withSource.stream().filter(RecallInfo::hitAtK).count() / withSource.size();
        int items = infos.stream().mapToInt(RecallInfo::items).sum();
        Double accuracy = items == 0 ? null : (double) infos.stream().mapToInt(RecallInfo::correctItems).sum() / items;
        var sorted = latencies.stream().sorted().toList();
        return new RetrievalStats(infos.size(), K, recallAtK, accuracy, percentile(sorted, 50),
                percentile(sorted, 95), sorted.size(), List.copyOf(infos));
    }

    /** Nearest-rank percentile of sorted values; null without samples. */
    static Double percentile(List<Double> sorted, int p) {
        if (sorted.isEmpty()) {
            return null;
        }
        int rank = (int) Math.ceil(p / 100.0 * sorted.size());
        return Math.round(sorted.get(Math.max(0, rank - 1)) * 100) / 100.0;
    }

    private ComponentResult check(Case c, String component, Scenario s) {
        try {
            String detail = switch (component) {
                case "persistence" -> checkPersistence(s);
                case "terminal" -> checkTerminal(s);
                case "history-injection" -> checkHistoryInjection(s);
                case "paging" -> checkPaging(s);
                case "isolation" -> checkIsolation(s);
                case "tool-provenance" -> checkToolProvenance(s);
                case "size-limit" -> checkSizeLimit(s);
                case "context-budget" -> checkContextBudget(c, s);
                case "recall" -> checkRecall(c, s);
                case "summary" -> checkSummary(s);
                case "capability-background" -> checkCapabilityBackground(s);
                default -> throw new IllegalStateException("no check for " + component);
            };
            return result(c, component, ComponentStatus.PASS, detail);
        } catch (RuntimeException | AssertionError e) {
            return result(c, component, ComponentStatus.FAIL, describe(e));
        }
    }

    private String checkPersistence(Scenario s) {
        var rows = messages(s.conversationId());
        var expectedUser = s.probeConversationId() == null || s.probeConversationId().equals(s.conversationId())
                ? s.sentTexts() : s.sentTexts().subList(0, s.turnIds().size());
        var users = rows.stream().filter(m -> "user".equals(m.role())).map(StoredText::content).toList();
        require(users.equals(expectedUser), "用户消息与发送内容不一致（条数 " + users.size() + "/" + expectedUser.size() + "）");
        var assistants = rows.stream().filter(m -> "assistant".equals(m.role())).count();
        require(assistants == expectedUser.size(), "助手消息条数 " + assistants + "，期望 " + expectedUser.size());
        long chars = expectedUser.stream().mapToLong(String::length).sum();
        return rows.size() + " 条原文按顺序入库，用户输入逐字一致（共 " + chars + " 字）";
    }

    private String checkTerminal(Scenario s) {
        var all = new ArrayList<>(s.turnIds());
        if (s.probeTurnId() != null) {
            all.add(s.probeTurnId());
        }
        for (var id : all) {
            require("COMPLETED".equals(stack.status(id)), "执行 " + id + " 状态为 " + stack.status(id));
        }
        return all.size() + " 个执行均为 COMPLETED";
    }

    private static boolean isReference(LlmClient.Message m) {
        return "user".equals(m.role()) && m.content() != null && m.content().startsWith(ContextAssembler.RECALL_HEADER);
    }

    private String checkHistoryInjection(Scenario s) {
        var answer = s.probeAnswer();
        require(answer != null, "探针没有调用回答模型");
        var stored = messages(s.probeConversationId()).stream()
                .filter(m -> !m.turnId().equals(s.probeTurnId())).toList();
        var prompt = answer.messages.stream().filter(m -> !isReference(m)).toList();
        // system prompt, then the injected history, then the probe question (a recall reference is not history).
        var injected = prompt.subList(1, prompt.size() - 1);
        require(injected.size() == stored.size(), "注入 " + injected.size() + " 条，库中已完成历史 " + stored.size() + " 条");
        for (int i = 0; i < stored.size(); i++) {
            require(injected.get(i).role().equals(stored.get(i).role())
                    && injected.get(i).content().equals(stored.get(i).content()), "第 " + (i + 1) + " 条注入历史与原文不一致");
        }
        require(prompt.getLast().content().equals(s.sentTexts().getLast()), "最后一条不是探针问题");
        return "探针提示包含全部 " + stored.size() + " 条已完成历史（窗口内全部放得下），不含当前执行";
    }

    private String checkPaging(Scenario s) {
        var expected = messages(s.conversationId()).stream().map(StoredText::content).toList();
        var collected = new ArrayList<String>();
        Long before = null;
        int pages = 0;
        do {
            var page = stack.history().page(s.scope(), s.conversationId(), before, 3).block(TURN_WAIT);
            var texts = page.items().stream().map(m -> m.payload().content()).toList();
            collected.addAll(0, texts);
            before = page.hasMore() ? page.nextBeforeSequence() : null;
            pages++;
        } while (before != null && pages < 100);
        require(collected.equals(expected), "分页拼接结果与原文顺序不一致（" + collected.size() + "/" + expected.size() + "）");
        return "每页 3 条共 " + pages + " 页，拼接后与 " + expected.size() + " 条原文顺序一致";
    }

    private String checkIsolation(Scenario s) {
        var other = new MemoryScope("tenant-2", s.scope().subject());
        var before = messages(s.conversationId()).size();
        requireCode(() -> stack.history().page(other, s.conversationId(), null, 10).block(TURN_WAIT),
                ErrorCode.CONVERSATION_NOT_FOUND, "另一租户读取历史");
        requireCode(() -> stack.accept(other, s.conversationId(), NonBlockingIds.uuid(), "探测"),
                ErrorCode.CONVERSATION_NOT_FOUND, "另一租户续写会话");
        require(messages(s.conversationId()).size() == before, "另一租户的请求改变了会话内容");
        return "同一 subject 在另一租户读取与续写均为 404 CONVERSATION_NOT_FOUND，原会话未变";
    }

    private String checkToolProvenance(Scenario s) {
        var rows = stack.toolEventRowsOfConversation(s.conversationId());
        require(rows.size() == 1, "工具记录 " + rows.size() + " 条，期望 1 条");
        var row = rows.getFirst();
        require("SUCCEEDED".equals(row.get("STATUS")), "工具状态 " + row.get("STATUS"));
        require("TOOL_RESULT".equals(row.get("SOURCE_TYPE")), "来源类型 " + row.get("SOURCE_TYPE"));
        require(INVENTORY.equals(row.get("CAPABILITY")), "能力 " + row.get("CAPABILITY"));
        require(String.valueOf(row.get("RESULT")).contains("37"), "工具结果未保存");
        return "工具调用记录为 SUCCEEDED、来源 TOOL_RESULT，结果已保存（提示历史只含最终回答）";
    }

    private String checkSizeLimit(Scenario s) {
        require(s.rejected() == ErrorCode.MESSAGE_TOO_LARGE, "超长消息未被拒绝：" + s.rejected());
        require(messages(s.conversationId()).isEmpty(), "超长消息被部分写入");
        require(stack.rows("SELECT id FROM sessions WHERE id = ?", s.conversationId()).isEmpty(), "创建了会话");
        int bytes = s.sentTexts().getFirst().getBytes(StandardCharsets.UTF_8).length;
        return "约 " + bytes / 1024 + " KiB 的消息返回 413 MESSAGE_TOO_LARGE，未截断、未入库";
    }

    /**
     * Every model call of the case stays within the window's input budget (estimate with the safety factor);
     * for a small window the first turn is out of the probe's recent window; an oversized tool result reaches the
     * model as a placeholder while the full result is recorded.
     */
    private String checkContextBudget(Case c, Scenario s) {
        int available = budget.availableInput(s.window());
        int max = 0;
        int calls = 0;
        for (var call : s.allCalls()) {
            if (call.kind == CallKind.RECORD) {
                continue;
            }
            int estimate = budget.estimate(call.messages);
            require(estimate <= available, call.kind + " 调用估算 " + estimate + " 超过输入预算 " + available);
            max = Math.max(max, estimate);
            calls++;
        }
        var detail = new StringBuilder(calls + " 次模型调用均在输入预算内（最大估算 " + max + "/" + available + "）");
        if (c.window() != null && s.probeTurnId() != null) {
            var answer = s.probeAnswer();
            require(answer != null, "探针没有调用回答模型");
            var first = s.sentTexts().getFirst();
            var history = answer.messages.subList(1, answer.messages.size() - 1).stream().filter(m -> !isReference(m))
                    .toList();
            require(history.stream().noneMatch(m -> first.equals(m.content())), "第一轮仍在近期窗口内，窗口未截断");
            detail.append("；第一轮已不在探针的近期窗口（保留 ").append(history.size()).append(" 条）");
        }
        var tools = s.allCalls().stream().filter(call -> call.kind == CallKind.CAPABILITY)
                .flatMap(call -> call.messages.stream()).filter(m -> "tool".equals(m.role())).toList();
        if (c.turns().stream().anyMatch(MemoryEvalCases.Turn::tool) && c.id().equals("tool-02")) {
            require(tools.stream().anyMatch(m -> m.content().contains("结果过长")), "超长工具结果没有换成占位说明");
            var rows = stack.toolEventRowsOfConversation(s.conversationId());
            require(rows.size() == 1, "工具记录 " + rows.size() + " 条");
            int stored = String.valueOf(rows.getFirst().get("RESULT")).length();
            require(stored >= InventoryCapability.LARGE_CHARS, "工具记录不完整（" + stored + " 字）");
            detail.append("；超长工具结果以占位说明发送给模型，完整结果（").append(stored).append(" 字）保存在工具记录中");
        }
        return detail.toString();
    }

    private String checkRecall(Case c, Scenario s) {
        var info = s.recall();
        require(info != null, "探针没有执行 Recall");
        var answer = s.probeAnswer();
        require(answer != null, "探针没有调用回答模型");
        require(answer.messages.stream().noneMatch(m -> "system".equals(m.role())
                && m.content().contains(ContextAssembler.RECALL_HEADER)), "召回资料出现在系统消息中");
        if (c.recallSource() == null) {
            require(info.items() == 0, "不应召回任何内容，实际 " + info.items() + " 条");
            require(!info.referenceInjected(), "注入了召回资料");
            return "未召回任何内容，提示中没有召回资料（跨会话开关" + (c.crossConversation() ? "开" : "关") + "）";
        }
        require(Boolean.TRUE.equals(info.hitAtK()), "来源轮次不在前 " + K + " 条召回中（共 " + info.items() + " 条）");
        var result = retriever.byTurn.get(s.probeTurnId());
        var source = s.turnIds().get(c.recallSource());
        var item = result.items().stream().filter(i -> i.turnId().equals(source)).findFirst().orElseThrow();
        if (c.recallReason() != null) {
            require(c.recallReason().equals(item.reason()), "召回原因 " + item.reason() + "，期望 " + c.recallReason());
        }
        var reference = answer.messages.stream().filter(MemoryEvalRunner::isReference).findFirst();
        require(reference.isPresent(), "回答调用中没有召回资料消息");
        var sourceText = c.turns().get(c.recallSource()).text();
        require(reference.get().content().contains(sourceText), "召回资料不含来源原文");
        return "来源轮次命中前 " + K + "（共 " + info.items() + " 条，正确 " + info.correctItems() + " 条，原因 "
                + item.reason() + (item.crossConversation() ? "，来自其他会话" : "") + "），原文以参考资料消息注入";
    }

    private String checkSummary(Scenario s) {
        var records = summaries.recordsOf(s.scope(), s.conversationId()).collectList().block(TURN_WAIT);
        require(records.size() == s.turnIds().size(), "记录 " + records.size() + " 条，已完成轮次 " + s.turnIds().size());
        for (var record : records) {
            require(s.turnIds().contains(record.turnId()), "记录指向未知轮次");
            var rows = stack.rows("SELECT MIN(sequence) AS f, MAX(sequence) AS t FROM chat_messages WHERE turn_id = ?",
                    record.turnId()).getFirst();
            require(((Number) rows.get("F")).longValue() == record.fromSequence()
                    && ((Number) rows.get("T")).longValue() == record.toSequence(), "记录的来源范围与原文不一致");
            var original = summaries.originalTurn(s.scope(), s.conversationId(), record.turnId())
                    .map(LlmClient.Message::content).collectList().block(TURN_WAIT);
            var stored = messages(s.conversationId()).stream().filter(m -> m.turnId().equals(record.turnId()))
                    .map(StoredText::content).toList();
            require(original.equals(stored), "按记录取回的原文与库中原文不一致");
        }
        return records.size() + " 条轮次记录，来源范围与原文一致，均可回到原始轮次";
    }

    private String checkCapabilityBackground(Scenario s) {
        var capability = s.probeCalls() == null ? List.<CallLog>of()
                : s.probeCalls().stream().filter(call -> call.kind == CallKind.CAPABILITY).toList();
        require(!capability.isEmpty(), "探针没有调用能力");
        var first = capability.getFirst();
        var previous = s.sentTexts().getFirst();
        require(first.tools > 0, "能力调用没有工具定义");
        require(first.messages.getFirst().content().equals(InventoryCapability.PROMPT), "第一条不是能力提示词");
        var body = first.messages.subList(1, first.messages.size() - 1);
        require(body.stream().anyMatch(m -> "user".equals(m.role()) && previous.equals(m.content()))
                && body.stream().anyMatch(m -> "assistant".equals(m.role())), "能力调用没有上一轮背景");
        require(first.messages.getLast().content().equals(s.sentTexts().getLast()), "最后一条不是当前问题");
        require(capability.stream().anyMatch(call -> call.messages.stream().anyMatch(m -> "tool".equals(m.role()))),
                "带背景的能力调用没有继续工具调用");
        return "能力调用依次为能力提示词、上一轮背景（" + body.size() + " 条）、当前问题，并在同一请求中继续工具调用";
    }

    // ---------------------------------------------------------------- answers

    /** Runs every answer case through the M2a path with {@code llm}; records are written with the same model. */
    AnswerBaseline answers(List<Case> cases, LlmClient llm, RunInfo run) {
        retriever.reset();
        replays.clear();
        var counting = new CountingLlm(llm);
        var orchestrator = orchestrator();
        var results = new ArrayList<AnswerResult>();
        var recalls = new ArrayList<RecallInfo>();
        var recordTurns = new AtomicInteger();
        var recordsWritten = new AtomicInteger();
        for (var c : cases) {
            if (c.answer()) {
                var r = answer(c, orchestrator, counting, recordTurns, recordsWritten);
                results.add(r);
                if (c.components().contains("recall") && r.recall() != null) {
                    recalls.add(r.recall());
                }
            }
        }
        var firstTokens = results.stream().map(AnswerResult::firstTokenMs).filter(Objects::nonNull)
                .map(Long::doubleValue).sorted().toList();
        long probePrompt = results.stream().map(AnswerResult::context).filter(Objects::nonNull)
                .map(ContextInput::promptTokens).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        var cost = new Cost(counting.cost(Phase.TURN), counting.cost(Phase.PROBE), counting.cost(Phase.RECORD),
                recordTurns.get(), recordsWritten.get());
        return new AnswerBaseline(run, List.copyOf(results), stats(recalls, retriever.latencies), cost,
                percentile(firstTokens, 50), percentile(firstTokens, 95), probePrompt, settingsLine());
    }

    /** The recall and record parameters in effect (D19, D24), for the report. */
    String settingsLine() {
        var recall = stack.props().getRecall();
        var summary = stack.props().getSummary();
        return "recall.enabled=" + recall.isEnabled() + ", min-matched-terms=" + recall.getMinMatchedTerms()
                + ", max-items=" + recall.getMaxItems() + ", budget-share=" + recall.getBudgetShare()
                + ", candidate-limit=" + recall.getCandidateLimit() + ", chunk-max-tokens=" + recall.getChunkMaxTokens()
                + "; summary.max-source-tokens=" + summary.getMaxSourceTokens() + ", max-output-tokens="
                + summary.getMaxOutputTokens() + ", max-invalid-attempts=" + summary.getMaxInvalidAttempts()
                + "; context.estimate-factor=" + stack.props().getContext().getEstimateFactor();
    }

    private AnswerResult answer(Case c, Orchestrator orchestrator, CountingLlm llm, AtomicInteger recordTurns,
            AtomicInteger recordsWritten) {
        long start = System.nanoTime();
        var statuses = new ArrayList<String>();
        var expected = MemoryEvalCases.describe(c.expect());
        ContextInput context = null;
        try {
            var scope = newScope(c);
            if (c.crossConversation()) {
                settings.update(scope, true).block(TURN_WAIT);
            }
            var conversation = MemoryTestStack.newConversationId();
            var turnIds = new ArrayList<String>();
            llm.phase.set(Phase.TURN);
            for (var turn : c.turns()) {
                var turnId = execute(orchestrator, llm, scope, conversation, MemoryEvalCases.text(turn), c).turnId();
                turnIds.add(turnId);
                statuses.add(stack.status(turnId));
            }
            String sourceRecord = null;
            if (c.components().contains("summary")) {
                recordTurns.addAndGet(turnIds.size());
                recordsWritten.addAndGet(buildRecords(scope, conversation, llm));
                if (c.recallSource() != null) {
                    var source = turnIds.get(c.recallSource());
                    sourceRecord = summaries.recordsOf(scope, conversation).filter(r -> r.turnId().equals(source))
                            .map(r -> r.summary() + " | 关键词：" + String.join("、", r.keywords())).next()
                            .defaultIfEmpty("（未生成记录）").block(TURN_WAIT);
                }
            }
            var probeConversation = c.probeInNewConversation() ? MemoryTestStack.newConversationId() : conversation;
            var replay = new Replay(c, scope, probeConversation, List.copyOf(turnIds), new AtomicReference<>());
            if (c.components().contains("recall")) {
                replays.add(replay);
            }
            var injected = stack.meters().find("memory.history.injected.messages").summary();
            var tokens = stack.meters().find("memory.history.injected.tokens").summary();
            double messagesBefore = injected == null ? 0 : injected.totalAmount();
            double tokensBefore = tokens == null ? 0 : tokens.totalAmount();
            llm.phase.set(Phase.PROBE);
            int before = llm.calls.size();
            var probe = execute(orchestrator, llm, scope, probeConversation, c.probe(), c);
            replay.probeTurnId().set(probe.turnId());
            var probeCalls = List.copyOf(llm.calls.subList(before, llm.calls.size()));
            statuses.add(stack.status(probe.turnId()));
            var usage = stack.usage(probe.turnId());
            var observed = usage.get("USAGE_OBSERVED");
            Long promptTokens = Boolean.TRUE.equals(observed) || (observed instanceof Number n && n.intValue() == 1)
                    ? ((Number) usage.get("PROMPT_TOKENS")).longValue() : null;
            injected = stack.meters().find("memory.history.injected.messages").summary();
            tokens = stack.meters().find("memory.history.injected.tokens").summary();
            context = new ContextInput(M2A_CONTEXT, (int) ((injected == null ? 0 : injected.totalAmount()) - messagesBefore),
                    (long) ((tokens == null ? 0 : tokens.totalAmount()) - tokensBefore), promptTokens);
            var recall = recallInfo(c, turnIds, probe.turnId(), probeCalls);
            var capability = capabilityInfo(probeCalls);
            var answer = stack.assistantText(probe.turnId());
            long ms = elapsed(start);
            if (statuses.stream().anyMatch(st -> !"COMPLETED".equals(st))) {
                return new AnswerResult(c.id(), c.category(), c.requires(), c.probe(), answer, Verdict.ERROR, expected,
                        context, ms, statuses, "执行未完成：" + statuses, probe.firstTokenMs(), recall, capability,
                        sourceRecord);
            }
            return new AnswerResult(c.id(), c.category(), c.requires(), c.probe(), answer,
                    MemoryEvalCases.judge(c.expect(), answer), expected, context, ms, statuses, null,
                    probe.firstTokenMs(), recall, capability, sourceRecord);
        } catch (RuntimeException | AssertionError e) {
            return new AnswerResult(c.id(), c.category(), c.requires(), c.probe(), null, Verdict.ERROR, expected,
                    context, elapsed(start), statuses, describe(e), null, null, null, null);
        }
    }

    private static CapabilityInfo capabilityInfo(List<CallLog> probeCalls) {
        var capability = probeCalls.stream().filter(call -> call.kind == CallKind.CAPABILITY).toList();
        if (capability.isEmpty()) {
            return null;
        }
        int withBackground = (int) capability.stream().filter(call -> call.tools > 0
                && call.messages.subList(1, call.messages.size()).stream().anyMatch(m -> "assistant".equals(m.role())
                        && (m.toolCalls() == null || m.toolCalls().isEmpty()))).count();
        int withTool = (int) capability.stream()
                .filter(call -> call.messages.stream().anyMatch(m -> "tool".equals(m.role()))).count();
        return new CapabilityInfo(capability.size(), withBackground, withTool);
    }

    // ---------------------------------------------------------------- shared

    private Orchestrator orchestrator() {
        var registry = new CapabilityRegistry(List.of(new InventoryCapability()));
        var dispatcher = new InProcessTaskDispatcher(new DomainAgent(registry, stack.toolRecorder(), budget,
                stack.metrics()));
        var assembler = new ContextAssembler(stack.history(), budget, stack.metrics(), retriever);
        return new Orchestrator(dispatcher, registry, stack.lifecycle(), stack.history(), assembler);
    }

    private record Executed(String turnId, Long firstTokenMs) {}

    /**
     * Accepts and streams one turn to the end under the case's model window, then runs the pending index jobs
     * (as the background worker would). First-token latency: first answer delta after the request.
     */
    private Executed execute(Orchestrator orchestrator, LlmClient llm, MemoryScope scope, String conversationId,
            String text, Case c) {
        var model = c.window() == null ? new ResolvedModel(llm, null, null)
                : new ResolvedModel(llm, c.window().contextWindowTokens(), c.window().maxOutputTokens());
        long start = System.nanoTime();
        var accepted = stack.accept(scope, conversationId, NonBlockingIds.uuid(), text);
        var first = new AtomicLong(-1);
        orchestrator.chat(accepted, "eval-access-token", null, model)
                .doOnNext(line -> {
                    if (first.get() < 0 && line.contains("\"type\":\"delta\"")) {
                        first.set(elapsed(start));
                    }
                })
                .collectList().block(TURN_WAIT);
        worker.runPending(50).block(TURN_WAIT);
        return new Executed(accepted.ticket().turnId(), first.get() < 0 ? null : first.get());
    }

    enum Phase { TURN, PROBE, RECORD }

    enum CallKind { ROUTING, CAPABILITY, ANSWER, RECORD }

    /** One model call as sent, with the provider-reported usage. */
    static final class CallLog {
        final Phase phase;
        final CallKind kind;
        final List<LlmClient.Message> messages;
        final int tools;
        final AtomicLong prompt = new AtomicLong();
        final AtomicLong completion = new AtomicLong();
        final AtomicLong total = new AtomicLong();
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);

        CallLog(Phase phase, List<LlmClient.Message> messages, int tools) {
            this.phase = phase;
            this.messages = List.copyOf(messages);
            this.tools = tools;
            var system = messages.isEmpty() ? "" : String.valueOf(messages.getFirst().content());
            this.kind = system.contains("路由模块") ? CallKind.ROUTING
                    : system.contains("对话记忆索引模块") ? CallKind.RECORD
                    : system.equals(InventoryCapability.PROMPT) ? CallKind.CAPABILITY : CallKind.ANSWER;
        }
    }

    /** Wraps a model, logging every call (messages, tool count, usage) under the current phase. */
    static final class CountingLlm implements LlmClient {
        final LlmClient delegate;
        final AtomicReference<Phase> phase = new AtomicReference<>(Phase.TURN);
        final List<CallLog> calls = new CopyOnWriteArrayList<>();

        CountingLlm(LlmClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public Flux<LlmEvent> chatStream(List<Message> messages, List<LlmClient.ToolDefinition> tools) {
            return track(messages, tools, delegate.chatStream(messages, tools));
        }

        @Override
        public Flux<LlmEvent> chatStream(List<Message> messages, List<LlmClient.ToolDefinition> tools,
                CallOptions options) {
            return track(messages, tools, delegate.chatStream(messages, tools, options));
        }

        private Flux<LlmEvent> track(List<Message> messages, List<LlmClient.ToolDefinition> tools,
                Flux<LlmEvent> events) {
            var log = new CallLog(phase.get(), messages, tools == null ? 0 : tools.size());
            calls.add(log);
            // Some callers stop at Completed (the record handler takes the first Completed), while OpenAI-style
            // streams report usage in a later chunk: the stream is drained independently of the caller
            // (replay + autoConnect is not cancelled with its subscriber) so the provider usage is always counted.
            return events.doOnNext(event -> {
                if (event instanceof LlmEvent.Usage u) {
                    log.prompt.addAndGet(u.promptTokens());
                    log.completion.addAndGet(u.completionTokens());
                    log.total.addAndGet(u.totalTokens());
                }
            }).doFinally(signal -> log.done.countDown()).replay().autoConnect();
        }

        PhaseCost cost(Phase p) {
            var of = calls.stream().filter(c -> c.phase == p).toList();
            for (var c : of) {
                try {
                    c.done.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return new PhaseCost(of.size(), of.stream().mapToLong(c -> c.prompt.get()).sum(),
                    of.stream().mapToLong(c -> c.completion.get()).sum(), of.stream().mapToLong(c -> c.total.get()).sum());
        }
    }

    private record StoredText(String turnId, String role, String content) {}

    private List<StoredText> messages(String conversationId) {
        return stack.rows("SELECT turn_id, role, payload FROM chat_messages WHERE conversation_id = ? ORDER BY sequence",
                conversationId).stream().map(row -> {
                    try {
                        var payload = JSON.readTree(String.valueOf(row.get("PAYLOAD")));
                        return new StoredText(String.valueOf(row.get("TURN_ID")), String.valueOf(row.get("ROLE")),
                                payload.path("content").asText(null));
                    } catch (Exception e) {
                        throw new IllegalStateException("unreadable payload", e);
                    }
                }).toList();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireCode(Runnable action, ErrorCode code, String what) {
        try {
            action.run();
        } catch (MemoryException e) {
            require(e.code() == code, what + " 返回 " + e.code() + "，期望 " + code);
            return;
        }
        throw new AssertionError(what + " 没有被拒绝");
    }

    /** Error description for reports: type and stable code only, never upstream messages or credentials. */
    private static String describe(Throwable e) {
        if (e instanceof MemoryException m) {
            return "MemoryException " + m.code();
        }
        if (e instanceof AssertionError) {
            return String.valueOf(e.getMessage());
        }
        if (e instanceof LlmClient.LlmException l) {
            return "LlmException HTTP " + l.status;
        }
        return e.getClass().getSimpleName();
    }

    private static long elapsed(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    /** Short commit of the working tree ("-dirty" with uncommitted tracked changes), or "unknown". */
    static String commit() {
        try {
            var sha = run("git", "rev-parse", "--short=12", "HEAD");
            var dirty = run("git", "status", "--porcelain", "--untracked-files=no");
            return sha.isBlank() ? "unknown" : sha + (dirty.isBlank() ? "" : "-dirty");
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String run(String... command) throws Exception {
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
            return "";
        }
        return output;
    }

    /** Neutral sample inventory capability with fixed data (no real system behind it). */
    static final class InventoryCapability implements AgentCapabilityProvider {

        static final String PROMPT = "你是样例库存查询能力（评测用，数据固定）。用 lookup_inventory 工具按 SKU 查询库存，"
                + "然后用一句话说明查询结果。";

        @Override public String code() { return INVENTORY; }
        @Override public String displayName() { return "样例库存查询"; }
        @Override public String routingDescription() {
            return "查询样例库存系统中某个商品（SKU）的当前库存数量";
        }

        @Override
        public Mono<CapabilityDefinition> definition(AgentExecutionContext context) {
            var schema = JSON.createObjectNode().put("type", "object");
            schema.putObject("properties").putObject("sku").put("type", "string").put("description", "商品 SKU，例如 SKU-1001");
            schema.putArray("required").add("sku");
            return Mono.just(new CapabilityDefinition(PROMPT,
                    List.of(new ToolDefinition("lookup_inventory", "按 SKU 查询样例库存", schema))));
        }

        /** Characters of the oversized SKU-9999 movement list (well over a 4096-token window). */
        static final int LARGE_CHARS = 20_000;

        @Override
        public Mono<ToolResult> execute(ToolInvocation invocation, AgentExecutionContext context) {
            var sku = invocation.arguments().path("sku").asText("").trim();
            if ("SKU-9999".equalsIgnoreCase(sku)) {
                return Mono.just(new ToolResult(true, largeMovements()));
            }
            return Mono.just("SKU-1001".equalsIgnoreCase(sku)
                    ? new ToolResult(true, "SKU-1001 当前库存 37 件，所在仓库：华东一号仓。")
                    : new ToolResult(false, "未找到该 SKU"));
        }

        static String largeMovements() {
            var out = new StringBuilder("SKU-9999 出入库明细（样例数据）：\n");
            for (int i = 1; out.length() < LARGE_CHARS; i++) {
                out.append("第").append(i).append("笔：样例仓库入库十件，出库八件，结存变化二件，经办人样例员工。\n");
            }
            return out.toString();
        }
    }

    /**
     * Deterministic fake model for engineering checks: routes to the inventory capability only when told to,
     * drives one tool call (SKU-9999 when the question names it), writes a valid turn record for record calls
     * (with paraphrase keywords for the dental appointment of recall-03), and answers with a numbered text.
     */
    static final class EngineeringLlm implements LlmClient {
        final AtomicBoolean routeToInventory = new AtomicBoolean();
        private final AtomicInteger answers = new AtomicInteger();

        @Override
        public Flux<LlmEvent> chatStream(List<Message> messages, List<LlmClient.ToolDefinition> tools) {
            var usage = new LlmEvent.Usage(1, 1, 2);
            var system = messages.getFirst().content();
            if (system.contains("路由模块")) {
                return Flux.just(new LlmEvent.Completed(routeToInventory.get() ? "[\"" + INVENTORY + "\"]" : "[]"), usage);
            }
            if (system.contains("对话记忆索引模块")) {
                return Flux.just(new LlmEvent.Completed(record(messages.getLast().content())), usage);
            }
            if (system.equals(InventoryCapability.PROMPT)) {
                boolean hasResult = messages.stream().anyMatch(m -> "tool".equals(m.role()));
                var sku = messages.getLast().content().contains("SKU-9999") ? "SKU-9999" : "SKU-1001";
                return hasResult
                        ? Flux.just(new LlmEvent.Completed("SKU-9999".equals(sku) ? "SKU-9999 明细已导出。"
                                : "SKU-1001 当前库存 37 件。"), usage)
                        : Flux.just(new LlmEvent.ToolCallsReady(List.of(
                                new ToolCall("eval-call-1", "lookup_inventory", "{\"sku\":\"" + sku + "\"}"))), usage);
            }
            var text = "工程回答 #" + answers.incrementAndGet();
            return Flux.just(new LlmEvent.ContentDelta(text), new LlmEvent.Completed(text), usage);
        }

        private static String record(String transcript) {
            var first = transcript.lines().findFirst().orElse("").strip();
            var summary = first.length() > 40 ? first.substring(0, 40) : first;
            var keywords = transcript.contains("口腔医院") ? List.of("去看牙", "口腔医院", "复诊") : List.<String>of();
            var node = JSON.createObjectNode().put("summary", summary.isEmpty() ? "对话" : summary);
            node.putArray("entities");
            node.putArray("key_facts");
            node.put("decision", false);
            keywords.forEach(node.putArray("keywords")::add);
            if (!node.has("keywords")) {
                node.putArray("keywords");
            }
            node.put("memory_type", "EVENT");
            return node.toString();
        }
    }
}
