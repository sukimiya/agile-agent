package io.github.sukimiya.agileagent.memory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.agent.DomainAgent;
import io.github.sukimiya.agileagent.agent.InProcessTaskDispatcher;
import io.github.sukimiya.agileagent.agent.Orchestrator;
import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.memory.MemoryEvalCases.Case;
import io.github.sukimiya.agileagent.memory.MemoryEvalCases.Verdict;
import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.service.LlmClient;
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
 * Runs the eval set on a {@link MemoryTestStack} through the real Orchestrator path (routing, direct answer or
 * capability + aggregate, full completed history as prompt context). Two separate outputs:
 * <ul>
 * <li>{@link #components}: engineering checks of the M1 components each case names, driven by a deterministic
 * fake model; components not implemented in M1 are NOT_APPLICABLE, never FAIL.</li>
 * <li>{@link #answers}: the probe answer of each answer case from the given model, with a rule-based verdict and
 * the context input. Only a real model ({@code memory-eval-live}) gives an end-to-end baseline.</li>
 * </ul>
 */
final class MemoryEvalRunner {

    enum ComponentStatus { PASS, FAIL, NOT_APPLICABLE }

    record ComponentResult(String caseId, String category, String requires, String component,
            ComponentStatus status, String detail) {}

    record ComponentBaseline(String environment, String commit, Instant runAt, List<ComponentResult> results) {
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

    record AnswerResult(String caseId, String category, String requires, String probe, String answer,
            Verdict verdict, String expected, ContextInput context, long durationMs, List<String> turnStatuses,
            String error) {}

    record AnswerBaseline(RunInfo run, List<AnswerResult> results) {}

    static final String FULL_HISTORY = "FULL_HISTORY";
    static final String INVENTORY = "EVAL_INVENTORY";
    private static final Duration TURN_WAIT = Duration.ofMinutes(4);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final MemoryScope OTHER_TENANT = new MemoryScope("tenant-2", MemoryTestStack.ALICE.subject());

    private final MemoryTestStack stack;

    MemoryEvalRunner(MemoryTestStack stack) {
        this.stack = stack;
    }

    // ---------------------------------------------------------------- component status

    /** Engineering checks of the M1 components each case names; unimplemented components are not applicable. */
    ComponentBaseline components(List<Case> cases, String environment) {
        var results = new ArrayList<ComponentResult>();
        for (var c : cases) {
            Scenario scenario = null;
            String failure = null;
            if (c.components().stream().anyMatch(MemoryEvalCases.M1_COMPONENTS::contains)) {
                try {
                    scenario = engineeringRun(c);
                } catch (RuntimeException | AssertionError e) {
                    failure = "场景执行失败：" + describe(e);
                }
            }
            for (var component : c.components()) {
                if (!MemoryEvalCases.M1_COMPONENTS.contains(component)) {
                    results.add(result(c, component, ComponentStatus.NOT_APPLICABLE,
                            "M1 未实现，属于 " + phaseOf(component) + "；不计为失败"));
                } else if (failure != null) {
                    results.add(result(c, component, ComponentStatus.FAIL, failure));
                } else {
                    results.add(check(c, component, scenario));
                }
            }
        }
        return new ComponentBaseline(environment, commit(), Instant.now(), List.copyOf(results));
    }

    private static String phaseOf(String component) {
        return switch (component) {
            case "recall", "summary", "context-budget" -> "M2a";
            case "memory-judgement" -> "M2b";
            default -> "后续阶段";
        };
    }

    private static ComponentResult result(Case c, String component, ComponentStatus status, String detail) {
        return new ComponentResult(c.id(), c.category(), c.requires(), component, status, detail);
    }

    /** What one engineering run of a case produced. */
    private record Scenario(String conversationId, String probeConversationId, List<String> turnIds,
            String probeTurnId, List<String> sentTexts, List<LlmClient.Message> probePrompt, ErrorCode rejected) {}

    private Scenario engineeringRun(Case c) {
        var llm = new EngineeringLlm();
        var orchestrator = orchestrator();
        var conversation = MemoryTestStack.newConversationId();
        if (c.components().contains("size-limit")) {
            var text = MemoryEvalCases.text(c.turns().getFirst());
            try {
                stack.accept(conversation, text);
                return new Scenario(conversation, null, List.of(), null, List.of(text), null, null);
            } catch (MemoryException e) {
                return new Scenario(conversation, null, List.of(), null, List.of(text), null, e.code());
            }
        }
        var turnIds = new ArrayList<String>();
        var sent = new ArrayList<String>();
        for (var turn : c.turns()) {
            var text = MemoryEvalCases.text(turn);
            llm.routeToInventory.set(turn.tool());
            turnIds.add(execute(orchestrator, llm, conversation, text));
            sent.add(text);
        }
        String probeConversation = null;
        String probeTurn = null;
        List<LlmClient.Message> probePrompt = null;
        if (c.probe() != null) {
            probeConversation = c.probeInNewConversation() ? MemoryTestStack.newConversationId() : conversation;
            llm.routeToInventory.set(false);
            int before = llm.answerCalls.size();
            probeTurn = execute(orchestrator, llm, probeConversation, c.probe());
            sent.add(c.probe());
            probePrompt = llm.answerCalls.size() > before ? llm.answerCalls.getLast() : null;
        }
        return new Scenario(conversation, probeConversation, List.copyOf(turnIds), probeTurn, List.copyOf(sent),
                probePrompt, null);
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

    private String checkHistoryInjection(Scenario s) {
        require(s.probePrompt() != null, "探针没有调用回答模型");
        var stored = messages(s.probeConversationId()).stream()
                .filter(m -> !m.turnId().equals(s.probeTurnId())).toList();
        var prompt = s.probePrompt();
        // system prompt, then the injected history, then the probe question.
        var injected = prompt.subList(1, prompt.size() - 1);
        require(injected.size() == stored.size(), "注入 " + injected.size() + " 条，库中已完成历史 " + stored.size() + " 条");
        for (int i = 0; i < stored.size(); i++) {
            require(injected.get(i).role().equals(stored.get(i).role())
                    && injected.get(i).content().equals(stored.get(i).content()), "第 " + (i + 1) + " 条注入历史与原文不一致");
        }
        require(prompt.getLast().content().equals(s.sentTexts().getLast()), "最后一条不是探针问题");
        return "探针提示包含全部 " + stored.size() + " 条已完成历史（完整历史注入），不含当前执行";
    }

    private String checkPaging(Scenario s) {
        var expected = messages(s.conversationId()).stream().map(StoredText::content).toList();
        var collected = new ArrayList<String>();
        Long before = null;
        int pages = 0;
        do {
            var page = stack.history().page(MemoryTestStack.ALICE, s.conversationId(), before, 3).block(TURN_WAIT);
            var texts = page.items().stream().map(m -> m.payload().content()).toList();
            collected.addAll(0, texts);
            before = page.hasMore() ? page.nextBeforeSequence() : null;
            pages++;
        } while (before != null && pages < 100);
        require(collected.equals(expected), "分页拼接结果与原文顺序不一致（" + collected.size() + "/" + expected.size() + "）");
        return "每页 3 条共 " + pages + " 页，拼接后与 " + expected.size() + " 条原文顺序一致";
    }

    private String checkIsolation(Scenario s) {
        var before = messages(s.conversationId()).size();
        requireCode(() -> stack.history().page(OTHER_TENANT, s.conversationId(), null, 10).block(TURN_WAIT),
                ErrorCode.CONVERSATION_NOT_FOUND, "另一租户读取历史");
        requireCode(() -> stack.accept(OTHER_TENANT, s.conversationId(), NonBlockingIds.uuid(), "探测"),
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

    // ---------------------------------------------------------------- answers

    /** Runs every answer case through the current system path (Orchestrator, full history) with {@code llm}. */
    AnswerBaseline answers(List<Case> cases, LlmClient llm, RunInfo run) {
        var orchestrator = orchestrator();
        var results = new ArrayList<AnswerResult>();
        for (var c : cases) {
            if (c.answer()) {
                results.add(answer(c, orchestrator, llm));
            }
        }
        return new AnswerBaseline(run, List.copyOf(results));
    }

    private AnswerResult answer(Case c, Orchestrator orchestrator, LlmClient llm) {
        long start = System.nanoTime();
        var statuses = new ArrayList<String>();
        var expected = MemoryEvalCases.describe(c.expect());
        ContextInput context = null;
        try {
            var conversation = MemoryTestStack.newConversationId();
            for (var turn : c.turns()) {
                statuses.add(stack.status(execute(orchestrator, llm, conversation, MemoryEvalCases.text(turn))));
            }
            var probeConversation = c.probeInNewConversation() ? MemoryTestStack.newConversationId() : conversation;
            var injected = stack.meters().find("memory.history.injected.messages").summary();
            var tokens = stack.meters().find("memory.history.injected.tokens").summary();
            double messagesBefore = injected.totalAmount();
            double tokensBefore = tokens.totalAmount();
            var probeTurn = execute(orchestrator, llm, probeConversation, c.probe());
            statuses.add(stack.status(probeTurn));
            var usage = stack.usage(probeTurn);
            var observed = usage.get("USAGE_OBSERVED");
            Long promptTokens = Boolean.TRUE.equals(observed) || (observed instanceof Number n && n.intValue() == 1)
                    ? ((Number) usage.get("PROMPT_TOKENS")).longValue() : null;
            context = new ContextInput(FULL_HISTORY, (int) (injected.totalAmount() - messagesBefore),
                    (long) (tokens.totalAmount() - tokensBefore), promptTokens);
            var answer = stack.assistantText(probeTurn);
            long ms = elapsed(start);
            if (statuses.stream().anyMatch(st -> !"COMPLETED".equals(st))) {
                return new AnswerResult(c.id(), c.category(), c.requires(), c.probe(), answer, Verdict.ERROR, expected,
                        context, ms, statuses, "执行未完成：" + statuses);
            }
            return new AnswerResult(c.id(), c.category(), c.requires(), c.probe(), answer,
                    MemoryEvalCases.judge(c.expect(), answer), expected, context, ms, statuses, null);
        } catch (RuntimeException | AssertionError e) {
            return new AnswerResult(c.id(), c.category(), c.requires(), c.probe(), null, Verdict.ERROR, expected,
                    context, elapsed(start), statuses, describe(e));
        }
    }

    // ---------------------------------------------------------------- shared

    private Orchestrator orchestrator() {
        var registry = new CapabilityRegistry(List.of(new InventoryCapability()));
        var dispatcher = new InProcessTaskDispatcher(new DomainAgent(registry, stack.toolRecorder()));
        return new Orchestrator(dispatcher, registry, stack.lifecycle(), stack.history());
    }

    /** Accepts and streams one turn to the end; returns its turn id. */
    private String execute(Orchestrator orchestrator, LlmClient llm, String conversationId, String text) {
        var accepted = stack.accept(conversationId, text);
        orchestrator.chat(accepted, "eval-access-token", null, llm).collectList().block(TURN_WAIT);
        return accepted.ticket().turnId();
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

        @Override
        public Mono<ToolResult> execute(ToolInvocation invocation, AgentExecutionContext context) {
            var sku = invocation.arguments().path("sku").asText("");
            return Mono.just("SKU-1001".equalsIgnoreCase(sku.trim())
                    ? new ToolResult(true, "SKU-1001 当前库存 37 件，所在仓库：华东一号仓。")
                    : new ToolResult(false, "未找到该 SKU"));
        }
    }

    /**
     * Deterministic fake model for engineering checks: routes to the inventory capability only when told to,
     * drives one tool call, and answers with a numbered text while recording every answer prompt.
     */
    static final class EngineeringLlm implements LlmClient {
        final AtomicBoolean routeToInventory = new AtomicBoolean();
        final List<List<Message>> answerCalls = new CopyOnWriteArrayList<>();
        private final AtomicInteger answers = new AtomicInteger();

        @Override
        public Flux<LlmEvent> chatStream(List<Message> messages, List<LlmClient.ToolDefinition> tools) {
            var usage = new LlmEvent.Usage(1, 1, 2);
            var system = messages.getFirst().content();
            if (system.contains("路由模块")) {
                return Flux.just(new LlmEvent.Completed(routeToInventory.get() ? "[\"" + INVENTORY + "\"]" : "[]"), usage);
            }
            if (system.equals(InventoryCapability.PROMPT)) {
                boolean hasResult = messages.stream().anyMatch(m -> "tool".equals(m.role()));
                return hasResult
                        ? Flux.just(new LlmEvent.Completed("SKU-1001 当前库存 37 件。"), usage)
                        : Flux.just(new LlmEvent.ToolCallsReady(List.of(
                                new ToolCall("eval-call-1", "lookup_inventory", "{\"sku\":\"SKU-1001\"}"))), usage);
            }
            answerCalls.add(List.copyOf(messages));
            var text = "工程回答 #" + answers.incrementAndGet();
            return Flux.just(new LlmEvent.ContentDelta(text), new LlmEvent.Completed(text), usage);
        }
    }
}
