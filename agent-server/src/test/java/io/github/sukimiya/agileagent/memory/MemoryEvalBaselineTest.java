package io.github.sukimiya.agileagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.memory.MemoryEvalCases.Case;
import io.github.sukimiya.agileagent.memory.MemoryEvalCases.Verdict;
import io.github.sukimiya.agileagent.memory.MemoryEvalRunner.ComponentStatus;
import io.github.sukimiya.agileagent.memory.MemoryEvalRunner.RunInfo;
import io.github.sukimiya.agileagent.memory.MemoryMetrics.Availability;
import io.github.sukimiya.agileagent.service.LlmClient;
import reactor.core.publisher.Flux;

/**
 * Engineering tests of the evaluation framework, component status and metric definitions, using a fake model.
 * A fake model's answers are never an answer-quality baseline; that comes only from {@code memory-eval-live}.
 */
class MemoryEvalBaselineTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir Path out;
    MemoryTestStack stack;
    MemoryEvalRunner runner;
    List<Case> cases;

    protected MemoryTestStack newStack() {
        return MemoryTestStack.h2();
    }

    /** Store label of this run; the component baseline is also kept under target/memory-eval/components-{label}. */
    protected String storeLabel() {
        return "h2";
    }

    private String environment() {
        return ("h2".equals(storeLabel()) ? "H2 (MODE=MySQL)" : "MySQL 8.4") + " + local test Redis, JDK "
                + System.getProperty("java.version");
    }

    @BeforeEach
    void setUp() {
        stack = newStack();
        runner = new MemoryEvalRunner(stack);
        cases = MemoryEvalCases.load();
    }

    @AfterEach
    void tearDown() {
        stack.close();
    }

    @Test
    void componentStatusAndAnswerResultsAreRecordedSeparately() throws Exception {
        var components = runner.components(cases, "test");
        var answers = runner.answers(only("coref-01", "recall-01"), fake(q -> "青岛 3000"), fakeRun());
        var componentMd = MemoryEvalReport.writeComponents(out, components, stack.metrics());
        var answerMd = MemoryEvalReport.writeAnswers(out, answers);

        assertThat(componentMd).isNotEqualTo(answerMd);
        assertThat(out.resolve(MemoryEvalReport.COMPONENT_JSON)).exists();
        assertThat(out.resolve(MemoryEvalReport.ANSWER_JSON)).exists();
        var componentJson = Files.readString(out.resolve(MemoryEvalReport.COMPONENT_JSON));
        var answerJson = Files.readString(out.resolve(MemoryEvalReport.ANSWER_JSON));
        // No pass rate mixes the two: component results carry no verdicts, answer results no component status.
        assertThat(componentJson).doesNotContain("\"verdict").doesNotContain("\"CORRECT\"")
                .doesNotContain("\"WRONG\"");
        assertThat(answerJson).doesNotContain("\"status\"").doesNotContain("NOT_APPLICABLE");
        assertThat(Files.readString(componentMd)).contains("组件状态").doesNotContain("端到端回答结果");
        var answerText = Files.readString(answerMd);
        assertThat(answerText).contains("端到端回答结果").doesNotContain("组件状态")
                .contains("fake LLM").contains("不能作为端到端基线");
        // Every M1 engineering check passes.
        assertThat(components.results()).noneMatch(r -> r.status() == ComponentStatus.FAIL);
        assertThat(components.results()).anyMatch(r -> r.status() == ComponentStatus.PASS);
    }

    @Test
    void liveRunRecordsModelConfigAndContextInput() throws Exception {
        var config = MemoryEvalLiveConfig.fromEnv(Map.of(
                MemoryEvalLiveConfig.BASE_URL, "https://llm.example.test/v1",
                MemoryEvalLiveConfig.API_KEY, "sk-never-printed",
                MemoryEvalLiveConfig.MODEL, "model-x",
                MemoryEvalLiveConfig.MODEL_VERSION, "2026-09"));
        assertThat(config.toString()).doesNotContain("sk-never-printed");
        var run = config.runInfo("test");
        assertThat(run.live()).isTrue();
        assertThat(run.model()).isEqualTo("model-x");
        assertThat(run.modelVersion()).isEqualTo("2026-09");
        assertThat(run.endpointHost()).isEqualTo("llm.example.test");
        assertThat(run.temperature()).contains("未发送");
        assertThat(run.contextInput()).isEqualTo(MemoryEvalRunner.FULL_HISTORY);
        assertThat(run.commit()).isNotBlank();

        var baseline = runner.answers(only("early-01"), fake(q -> "A7-3921"), run);

        var result = baseline.results().getFirst();
        assertThat(result.caseId()).isEqualTo("early-01");
        assertThat(result.answer()).isEqualTo("A7-3921");
        assertThat(result.verdict()).isEqualTo(Verdict.CORRECT);
        assertThat(result.turnStatuses()).hasSize(7).containsOnly("COMPLETED");
        assertThat(result.context().mode()).isEqualTo(MemoryEvalRunner.FULL_HISTORY);
        assertThat(result.context().injectedMessages()).isEqualTo(12);
        assertThat(result.context().estimatedTokens()).isPositive();
        // Routing and answer calls of the probe turn each report 11 prompt tokens.
        assertThat(result.context().promptTokens()).isEqualTo(22L);
        assertThat(result.durationMs()).isNotNegative();
        var md = Files.readString(MemoryEvalReport.writeAnswers(out, baseline));
        assertThat(md).contains("model-x").contains("2026-09").contains("llm.example.test").contains("未发送")
                .contains("完整历史").doesNotContain("sk-never-printed").doesNotContain("fake LLM");
    }

    @Test
    void liveConfigFailsWithoutModelSettingsAndNeverExposesTheKey() {
        assertThatThrownBy(() -> MemoryEvalLiveConfig.fromEnv(Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(MemoryEvalLiveConfig.BASE_URL)
                .hasMessageContaining(MemoryEvalLiveConfig.API_KEY)
                .hasMessageContaining(MemoryEvalLiveConfig.MODEL);
        assertThatThrownBy(() -> MemoryEvalLiveConfig.fromEnv(Map.of(MemoryEvalLiveConfig.API_KEY, "sk-secret-value",
                MemoryEvalLiveConfig.MODEL, "m")))
                .hasMessageContaining(MemoryEvalLiveConfig.BASE_URL)
                .hasMessageNotContaining("sk-secret-value");
    }

    @Test
    void baselineFileIsWrittenWithPerCaseStatus() throws Exception {
        var components = runner.components(cases, environment());
        var md = Files.readString(MemoryEvalReport.writeComponents(out, components, stack.metrics()));
        MemoryEvalReport.writeComponents(Path.of("target", "memory-eval", "components-" + storeLabel()), components,
                stack.metrics());
        JsonNode json = JSON.readTree(out.resolve(MemoryEvalReport.COMPONENT_JSON).toFile());

        var ids = cases.stream().map(Case::id).toList();
        assertThat(ids).hasSize(14).doesNotHaveDuplicates();
        var perCase = new java.util.HashMap<String, Integer>();
        json.path("results").forEach(r -> perCase.merge(r.path("caseId").asText(), 1, Integer::sum));
        for (var c : cases) {
            assertThat(perCase.get(c.id())).as(c.id()).isEqualTo(c.components().size());
            assertThat(md).contains("| " + c.id() + " |");
        }
        assertThat(json.path("commit").asText()).isNotBlank();
        assertThat(json.path("summary").path("FAIL").asInt()).isZero();
        assertThat(md).contains("PASS").contains("不适用");
    }

    @Test
    void unimplementedRecallCasesAreNotApplicableNotFailed() {
        var results = runner.components(cases, "test").results();
        var byCase = results.stream().collect(Collectors.groupingBy(MemoryEvalRunner.ComponentResult::caseId));

        assertThat(byCase.get("recall-01")).singleElement().satisfies(r -> {
            assertThat(r.component()).isEqualTo("recall");
            assertThat(r.status()).isEqualTo(ComponentStatus.NOT_APPLICABLE);
            assertThat(r.detail()).contains("M2a");
        });
        assertThat(byCase.get("early-02")).anySatisfy(r -> {
            assertThat(r.component()).isEqualTo("summary");
            assertThat(r.status()).isEqualTo(ComponentStatus.NOT_APPLICABLE);
        }).anySatisfy(r -> {
            assertThat(r.component()).isEqualTo("persistence");
            assertThat(r.status()).isEqualTo(ComponentStatus.PASS);
        });
        assertThat(results).filteredOn(r -> !MemoryEvalCases.M1_COMPONENTS.contains(r.component()))
                .isNotEmpty().allMatch(r -> r.status() == ComponentStatus.NOT_APPLICABLE);
        assertThat(results).filteredOn(r -> MemoryEvalCases.M1_COMPONENTS.contains(r.component()))
                .isNotEmpty().allMatch(r -> r.status() == ComponentStatus.PASS);
    }

    @Test
    void historyInjectionCountedSeparatelyFromRecall() {
        runner.answers(only("coref-01"), fake(q -> "青岛 3000"), fakeRun());

        var injected = stack.meters().find("memory.history.injected.messages").summary();
        assertThat(injected).isNotNull();
        // Three turns: 0, 2 and 4 earlier messages injected.
        assertThat(injected.count()).isEqualTo(3);
        assertThat(injected.totalAmount()).isEqualTo(6.0);
        assertThat(stack.meters().find("memory.history.injected.tokens").summary().totalAmount()).isPositive();
        assertThat(stack.meters().find("memory.recall.items").meter()).isNull();
        assertThat(stack.metrics().recallItems().availability()).isEqualTo(Availability.NOT_APPLICABLE);
    }

    @Test
    void recallLatencyIsNotReportedAsZero() throws Exception {
        runner.answers(only("coref-01"), fake(q -> "青岛 3000"), fakeRun());

        assertThat(stack.meters().getMeters()).noneMatch(m -> m.getId().getName().startsWith("memory.recall"));
        var latency = stack.metrics().recallLatency();
        assertThat(latency.availability()).isEqualTo(Availability.NOT_APPLICABLE);
        assertThat(latency.value()).isNull();
        assertThat(latency.display()).isEqualTo("不适用").doesNotContain("0");
        assertThat(stack.metrics().snapshot().get("memory.recall.latency").display()).isEqualTo("不适用");
        var md = Files.readString(MemoryEvalReport.writeComponents(out, runner.components(only("coref-01"), "test"),
                stack.metrics()));
        assertThat(md).contains("| memory.recall.latency | 不适用 |").doesNotContain("0ms");
    }

    @Test
    void consolidationBacklogShowsDisabledWithoutJobs() {
        // A pending deletion job is maintenance work, not consolidation backlog.
        stack.sql("INSERT INTO memory_jobs (id, tenant_id, subject, conversation_id, job_type, status, attempts,"
                + " available_at, created_at, updated_at) VALUES ('job-1', 'tenant-1', 'alice', ?, 'DELETE_CONVERSATION',"
                + " 'PENDING', 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
                MemoryTestStack.newConversationId());

        var backlog = stack.metrics().consolidationBacklog();
        assertThat(backlog.availability()).isEqualTo(Availability.DISABLED);
        assertThat(backlog.value()).isNull();
        assertThat(backlog.display()).isEqualTo("功能未启用");
        assertThat(stack.meters().find("memory.consolidation.backlog").meter()).isNull();
        assertThat(stack.meters().find("memory.feature.enabled").tag("feature", "consolidation").gauge().value())
                .isZero();
        assertThat(stack.meters().find("memory.feature.enabled").tag("feature", "recall").gauge().value()).isZero();
    }

    private List<Case> only(String... ids) {
        var wanted = List.of(ids);
        var selected = cases.stream().filter(c -> wanted.contains(c.id())).toList();
        assertThat(selected).hasSize(ids.length);
        return selected;
    }

    private static RunInfo fakeRun() {
        return new RunInfo(false, "fake", "n/a", "none", "未发送（使用服务商默认值）", MemoryEvalRunner.FULL_HISTORY,
                MemoryEvalRunner.commit(), Instant.now(), "test");
    }

    /** Routing answers "[]"; answers come from {@code reply}; every call reports usage 11/2/13. */
    static LlmClient fake(Function<String, String> reply) {
        return (messages, tools) -> {
            var usage = new LlmClient.LlmEvent.Usage(11, 2, 13);
            if (messages.getFirst().content().contains("路由模块")) {
                return Flux.just(new LlmClient.LlmEvent.Completed("[]"), usage);
            }
            var text = reply.apply(messages.getLast().content());
            return Flux.just(new LlmClient.LlmEvent.ContentDelta(text), new LlmClient.LlmEvent.Completed(text), usage);
        };
    }
}
