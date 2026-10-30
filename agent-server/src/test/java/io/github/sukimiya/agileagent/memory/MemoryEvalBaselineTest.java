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
        assertThat(run.contextInput()).isEqualTo(MemoryEvalRunner.M2B_CONTEXT);
        assertThat(run.commit()).isNotBlank();

        var baseline = runner.answers(only("early-01"), fake(q -> "A7-3921"), run);

        var result = baseline.results().getFirst();
        assertThat(result.caseId()).isEqualTo("early-01");
        assertThat(result.answer()).isEqualTo("A7-3921");
        assertThat(result.verdict()).isEqualTo(Verdict.CORRECT);
        assertThat(result.turnStatuses()).hasSize(7).containsOnly("COMPLETED");
        assertThat(result.context().mode()).isEqualTo(MemoryEvalRunner.M2B_CONTEXT);
        assertThat(result.context().injectedMessages()).isEqualTo(12);
        assertThat(result.context().estimatedTokens()).isPositive();
        // Routing and answer calls of the probe turn each report 11 prompt tokens.
        assertThat(result.context().promptTokens()).isEqualTo(22L);
        assertThat(result.durationMs()).isNotNegative();
        var md = Files.readString(MemoryEvalReport.writeAnswers(out, baseline));
        assertThat(md).contains("model-x").contains("2026-09").contains("llm.example.test").contains("未发送")
                .contains("上下文预算、基础 Recall 与长期记忆").doesNotContain("sk-never-printed").doesNotContain("fake LLM");
        // First-token latency is measured on the probe and summarised.
        assertThat(result.firstTokenMs()).isNotNull().isNotNegative();
        assertThat(md).contains("首字延迟");
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
        assertThat(ids).hasSize(27).doesNotHaveDuplicates();
        var perCase = new java.util.HashMap<String, Integer>();
        json.path("results").forEach(r -> perCase.merge(r.path("caseId").asText(), 1, Integer::sum));
        for (var c : cases) {
            assertThat(perCase.get(c.id())).as(c.id()).isEqualTo(c.components().size());
            assertThat(md).contains("| " + c.id() + " |");
        }
        assertThat(json.path("commit").asText()).isNotBlank();
        assertThat(json.path("summary").path("FAIL").asInt()).isZero();
        assertThat(md).contains("PASS");
    }

    @Test
    void m2aComponentsNoLongerNotApplicable() {
        var results = runner.components(cases, "test").results();

        for (var component : List.of("summary", "context-budget", "recall", "capability-background")) {
            assertThat(results).as(component).filteredOn(r -> r.component().equals(component)).isNotEmpty()
                    .allSatisfy(r -> assertThat(r.status()).as(r.caseId() + " " + component + ": " + r.detail())
                            .isEqualTo(ComponentStatus.PASS));
        }
        // M2b: nothing is not applicable any more (m2bComponentsMeasured).
        assertThat(results).noneMatch(r -> r.status() == ComponentStatus.NOT_APPLICABLE);
        assertThat(results).noneMatch(r -> r.status() == ComponentStatus.FAIL);
    }

    @Test
    void existingM1AndM2bCasesStillPass() {
        var original = List.of("coref-01", "coref-02", "early-01", "early-02", "corr-01", "corr-02", "long-01",
                "long-02", "tool-01", "paste-01", "quote-01", "hypo-01", "recall-01", "iso-01");
        var results = runner.components(only(original.toArray(String[]::new)), "test").results();

        assertThat(results).filteredOn(r -> !r.component().equals("memory-judgement")).isNotEmpty()
                .allSatisfy(r -> assertThat(r.status()).as(r.caseId() + " " + r.component() + ": " + r.detail())
                        .isEqualTo(ComponentStatus.PASS));
        // The M1 full-history check still holds wherever the whole history fits the default window.
        assertThat(results).filteredOn(r -> r.component().equals("history-injection")).hasSize(11)
                .allMatch(r -> r.detail().contains("全部"));
    }

    @Test
    void recallAtKAndSourceAccuracyRecorded() throws Exception {
        var components = runner.components(cases, "test");
        var retrieval = components.retrieval();

        assertThat(retrieval.cases()).isEqualTo(5);
        assertThat(retrieval.k()).isEqualTo(5);
        assertThat(retrieval.recallAtK()).isEqualTo(1.0);
        assertThat(retrieval.sourceAccuracy()).isEqualTo(1.0);
        var byCase = retrieval.perCase().stream()
                .collect(Collectors.toMap(MemoryEvalRunner.RecallInfo::caseId, Function.identity()));
        assertThat(byCase.get("window-01").reasons()).contains("CHUNK_FULLTEXT").isSubsetOf("CHUNK_FULLTEXT", "ITEM");
        assertThat(byCase.get("recall-02").reasons()).contains("ENTITY_EXACT").isSubsetOf("ENTITY_EXACT", "ITEM");
        assertThat(byCase.get("recall-03").reasons()).contains("RECORD_FULLTEXT").isSubsetOf("RECORD_FULLTEXT", "ITEM");
        assertThat(byCase.get("recall-01").crossConversation()).isTrue();
        // M2b: an item judged from the source turn is a further correct candidate (reason ITEM).
        assertThat(byCase.get("recall-04").items()).isZero();
        assertThat(byCase.get("recall-04").hitAtK()).isNull();
        var md = Files.readString(MemoryEvalReport.writeComponents(out, components, stack.metrics()));
        assertThat(md).contains("Recall@5").contains("来源正确率").contains("| window-01 |");
        assertThat(Files.readString(out.resolve(MemoryEvalReport.COMPONENT_JSON))).contains("\"recallAtK\"");

        var answers = runner.answers(only("recall-02"), fake(q -> "860 元"), fakeRun());
        var recall = answers.results().getFirst().recall();
        assertThat(recall.hitAtK()).isTrue();
        assertThat(recall.correctItems()).isEqualTo(recall.items()).isPositive();
        assertThat(answers.retrieval().recallAtK()).isEqualTo(1.0);
    }

    @Test
    void retrievalLatencyPercentilesRecorded() throws Exception {
        var components = runner.components(only("window-01", "recall-02"), "test");
        var retrieval = components.retrieval();

        // One recall per turn: 9 + 1 and 5 + 1 turns.
        assertThat(retrieval.latencySamples()).isEqualTo(16);
        assertThat(retrieval.p50Ms()).isNotNull().isNotNegative();
        assertThat(retrieval.p95Ms()).isNotNull().isGreaterThanOrEqualTo(retrieval.p50Ms());
        var md = Files.readString(MemoryEvalReport.writeComponents(out, components, stack.metrics()));
        assertThat(md).contains("检索延迟 p50").contains("p95");
    }

    @Test
    void weakHitThresholdSweepRecorded() {
        var sweep = runner.thresholdSweep(cases, List.of(1, 2, 3));

        assertThat(sweep).extracting(MemoryEvalRunner.ThresholdResult::minMatchedTerms).containsExactly(1, 2, 3);
        // The configured threshold (2) finds every expected source without noise.
        var two = sweep.get(1).retrieval();
        assertThat(two.recallAtK()).isEqualTo(1.0);
        assertThat(two.sourceAccuracy()).isEqualTo(1.0);
        assertThat(stack.props().getRecall().getMinMatchedTerms()).as("restored").isEqualTo(2);
    }

    @Test
    void weakHitReplaySweepUsesTheAnswerRunData() {
        var answers = runner.answers(only("recall-02", "recall-04"), fake(q -> "860"), fakeRun());
        int calls = answers.cost().turns().calls() + answers.cost().probes().calls();

        var replay = runner.replaySweep(List.of(1, 2));

        assertThat(replay).extracting(MemoryEvalRunner.ThresholdResult::minMatchedTerms).containsExactly(1, 2);
        var two = replay.get(1).retrieval();
        assertThat(two.cases()).isEqualTo(2);
        assertThat(two.recallAtK()).isEqualTo(1.0);
        assertThat(two.perCase()).filteredOn(i -> i.caseId().equals("recall-04")).singleElement()
                .satisfies(i -> assertThat(i.items()).isZero());
        assertThat(stack.props().getRecall().getMinMatchedTerms()).isEqualTo(2);
        // No model calls: the answer run's cost is unchanged.
        assertThat(answers.cost().turns().calls() + answers.cost().probes().calls()).isEqualTo(calls);
    }

    @Test
    void recordBuildingCostRecorded() {
        var answers = runner.answers(only("recall-03"), withRecords(fake(q -> "下周四下午三点")), fakeRun());

        var cost = answers.cost();
        // One record per completed turn of the case (1 + 4 turns), one model call each.
        assertThat(cost.recordsWritten()).isEqualTo(5);
        assertThat(cost.records().calls()).isEqualTo(5);
        assertThat(cost.records().promptTokens()).isEqualTo(55);
        assertThat(cost.probes().calls()).isEqualTo(2);
        assertThat(cost.turns().calls()).isEqualTo(10);
        assertThat(answers.results().getFirst().sourceRecord()).startsWith("记录");
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
        // Recall is counted on its own meter, once per question, never mixed into history injection.
        assertThat(stack.meters().find("memory.recall.items").summary().count()).isEqualTo(3);
        assertThat(stack.metrics().recallItems().availability()).isEqualTo(Availability.REPORTED);
    }

    @Test
    void recallLatencyNotApplicableOnlyWhenRecallDisabled() throws Exception {
        runner.answers(only("coref-01"), fake(q -> "青岛 3000"), fakeRun());
        assertThat(stack.metrics().recallLatency().availability()).isEqualTo(Availability.REPORTED);
        assertThat(stack.meters().find("memory.recall.latency").timer().count()).isEqualTo(3);

        try (var off = newStack()) {
            off.props().getRecall().setEnabled(false);
            var offRunner = new MemoryEvalRunner(off);
            offRunner.answers(only("coref-01"), fake(q -> "青岛 3000"), fakeRun());

            assertThat(off.meters().getMeters()).noneMatch(m -> m.getId().getName().startsWith("memory.recall"));
            var latency = off.metrics().recallLatency();
            assertThat(latency.availability()).isEqualTo(Availability.NOT_APPLICABLE);
            assertThat(latency.value()).isNull();
            assertThat(latency.display()).isEqualTo("不适用").doesNotContain("0");
            var md = Files.readString(MemoryEvalReport.writeComponents(out,
                    offRunner.components(only("coref-01"), "test"), off.metrics()));
            assertThat(md).contains("| memory.recall.latency | 不适用 |").doesNotContain("0ms");
        }
    }

    @Test
    void consolidationBacklogShowsDisabledWithoutJobs() {
        // A pending deletion job is maintenance work, not consolidation backlog.
        stack.sql("INSERT INTO memory_jobs (id, tenant_id, subject, conversation_id, job_type, status, attempts,"
                + " available_at, created_at, updated_at) VALUES ('job-1', 'tenant-1', 'alice', ?, 'CONVERSATION_CLEANUP',"
                + " 'PENDING', 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
                MemoryTestStack.newConversationId());

        var backlog = stack.metrics().consolidationBacklog();
        assertThat(backlog.availability()).isEqualTo(Availability.DISABLED);
        assertThat(backlog.value()).isNull();
        assertThat(backlog.display()).isEqualTo("功能未启用");
        assertThat(stack.meters().find("memory.consolidation.backlog").meter()).isNull();
        assertThat(stack.meters().find("memory.feature.enabled").tag("feature", "consolidation").gauge().value())
                .isZero();
        assertThat(stack.meters().find("memory.feature.enabled").tag("feature", "recall").gauge().value())
                .isEqualTo(1.0);
    }

    @Test
    void m2bComponentsMeasured() {
        var results = runner.components(cases, "test").results();

        for (var component : List.of("memory-judgement", "profile", "relations", "explain", "decay", "quota")) {
            assertThat(results).as(component).filteredOn(r -> r.component().equals(component)).isNotEmpty()
                    .allSatisfy(r -> assertThat(r.status()).as(r.caseId() + " " + component + ": " + r.detail())
                            .isEqualTo(ComponentStatus.PASS));
        }
        assertThat(results).noneMatch(r -> r.status() == ComponentStatus.NOT_APPLICABLE);
        // Every answer case was judged after its turns (idle simulated) before the probe.
        assertThat(runner.lastMemory()).containsKeys("fact-01", "del-01", "switch-01", "corr-03");
        assertThat(runner.lastMemory().values()).allSatisfy(m -> assertThat(m.judged()).as(m.caseId()).isTrue());
    }

    @Test
    void poisoningCasesProduceNoConfirmedFacts() {
        var poisoning = cases.stream().filter(Case::poisoning).toList();
        assertThat(poisoning).extracting(Case::id).containsExactlyInAnyOrder("paste-01", "quote-01", "hypo-01",
                "role-01", "inject-01", "inject-02");

        var results = runner.components(poisoning, "test").results();

        assertThat(results).noneMatch(r -> r.status() == ComponentStatus.FAIL);
        for (var c : poisoning) {
            var memory = runner.lastMemory().get(c.id());
            assertThat(memory).as(c.id()).isNotNull();
            // The engineering judge tries to store the poisoned content as the user's own words; none survives.
            assertThat(memory.attempts()).as(c.id() + " attempts").isPositive();
            assertThat(memory.confirmedPoisoned()).as(c.id() + " " + memory.details()).isZero();
        }
    }

    @Test
    void forbiddenTermsOutsidePoisoningCasesAreFalseMemoriesButNotPoisonedContent() throws Exception {
        // corr-03 is a correction case, not poisoning: a user-asserted stale value is a false memory (counted) but
        // never "poisoned content" for the quality gate (user decision 2026-10-29: poisoning cases only).
        var llm = judging(fake(q -> "M-7734"), transcript -> {
            if (!transcript.contains("M-5521") || transcript.contains("更正")) {
                return "[]";
            }
            var id = messageIdOf(transcript, "M-5521");
            return "[{\"action\":\"REMEMBER\",\"itemType\":\"FACT\",\"content\":\"用户会员卡号是 M-5521\","
                    + "\"assertion\":\"USER_STATED\",\"sourceMessageIds\":[\"" + id + "\"],"
                    + "\"evidenceExcerpts\":[\"M-5521\"],\"reason\":\"r\",\"importance\":0.5,\"confidence\":0.9}]";
        });

        var answers = runner.answers(only("corr-03"), llm, fakeRun());

        var memory = answers.results().getFirst().memory();
        assertThat(memory.falseMemories()).isEqualTo(1);
        assertThat(memory.confirmedPoisoned()).isZero();
        assertThat(answers.memory().confirmedPoisoned()).isZero();
        assertThat(MemoryEvalReport.compare(MemoryEvalReport.loadBaselines(), answers).gate().failures())
                .noneMatch(f -> f.contains("投毒"));
    }

    @Test
    void onlyCurrentItemsAreFalseMemoriesAndMentionsOfTheOldValueAreListedForReview() throws Exception {
        // corr-03 after a working correction: the old item is SUPERSEDED (not current: no false memory) and the new
        // item mentions the voided value next to the correct one (listed for manual review, not counted).
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var llm = judgingWithExisting(fake(q -> "M-7734"), (existing, transcript) -> {
            try {
                if (!transcript.contains("更正")) {
                    var id = messageIdOf(transcript, "M-5521");
                    return "[{\"action\":\"REMEMBER\",\"itemType\":\"FACT\",\"content\":\"用户会员卡号是 M-5521\","
                            + "\"assertion\":\"USER_STATED\",\"sourceMessageIds\":[\"" + id + "\"],"
                            + "\"evidenceExcerpts\":[\"M-5521\"],\"reason\":\"r\",\"importance\":0.5,"
                            + "\"confidence\":0.9}]";
                }
                var old = json.readTree(existing).get(0);
                var id = messageIdOf(transcript, "M-7734");
                return "[{\"action\":\"SUPERSEDE\",\"itemType\":\"FACT\","
                        + "\"content\":\"用户会员卡号是 M-7734（原卡号 M-5521 已作废）\",\"assertion\":\"USER_STATED\","
                        + "\"sourceMessageIds\":[\"" + id + "\"],\"evidenceExcerpts\":[\"M-7734\"],\"reason\":\"更正\","
                        + "\"targetItemId\":\"" + old.path("id").asText() + "\",\"expectedRevision\":"
                        + old.path("revision").asLong() + "}]";
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        var answers = runner.answers(only("corr-03"), llm, fakeRun());

        var memory = answers.results().getFirst().memory();
        assertThat(memory.details()).anyMatch(d -> d.contains("SUPERSEDED") && d.contains("M-5521"));
        assertThat(memory.falseMemories()).isZero();
        assertThat(memory.reviewMentions()).isEqualTo(1);
        assertThat(memory.details()).anyMatch(d -> d.startsWith("【待复核：提及旧值】") && d.contains("M-7734"));
        assertThat(answers.memory().falseMemories()).isZero();
        assertThat(answers.memory().reviewMentions()).isEqualTo(1);
        var md = Files.readString(MemoryEvalReport.writeAnswers(out, answers));
        assertThat(md).contains("待复核").contains("原卡号 M-5521 已作废");
    }

    @Test
    void falseMemoryCountRecorded() throws Exception {
        // A judge that keeps the hypothetical identity as an inferred fact: one false memory, counted, not a gate.
        var llm = judging(fake(q -> "广州"), transcript -> {
            var id = messageIdOf(transcript, "哈尔滨");
            return "[{\"action\":\"REMEMBER\",\"itemType\":\"FACT\",\"content\":\"用户可能住在哈尔滨\","
                    + "\"assertion\":\"INFERRED\",\"sourceMessageIds\":[\"" + id + "\"],"
                    + "\"evidenceExcerpts\":[\"哈尔滨\"],\"reason\":\"r\",\"importance\":0.5,\"confidence\":0.8}]";
        });

        var answers = runner.answers(only("hypo-01", "coref-01"), llm, fakeRun());

        var byCase = answers.results().stream().collect(Collectors.toMap(MemoryEvalRunner.AnswerResult::caseId,
                Function.identity()));
        assertThat(byCase.get("hypo-01").verdict()).isEqualTo(Verdict.CORRECT);
        assertThat(byCase.get("hypo-01").memory().falseMemories()).isEqualTo(1);
        assertThat(byCase.get("hypo-01").memory().details()).anyMatch(d -> d.contains("用户可能住在哈尔滨"));
        assertThat(byCase.get("coref-01").memory().falseMemories()).isZero();
        assertThat(answers.memory().falseMemories()).isEqualTo(1);
        assertThat(answers.memory().judgeCalls()).isPositive();
        var md = Files.readString(MemoryEvalReport.writeAnswers(out, answers));
        assertThat(md).contains("错误记忆").contains("用户可能住在哈尔滨");
    }

    @Test
    void qualityGateRequiresNoRegressionNoPoisonedFactAndRecallAtLeastPointNine() throws Exception {
        assertThat(MemoryEvalReport.QualityGate.MIN_RECALL_AT_K).isEqualTo(0.9);
        assertThat(MemoryEvalReport.gate(List.of(), 0, 0.9).passed()).isTrue();
        assertThat(MemoryEvalReport.gate(List.of(), 0, 1.0).failures()).isEmpty();
        assertThat(MemoryEvalReport.gate(List.of("recall-01"), 0, 1.0).failures()).singleElement()
                .satisfies(f -> assertThat(f).contains("回退").contains("recall-01"));
        assertThat(MemoryEvalReport.gate(List.of(), 1, 1.0).failures()).singleElement()
                .satisfies(f -> assertThat(f).contains("投毒"));
        assertThat(MemoryEvalReport.gate(List.of(), 0, 0.75).failures()).singleElement()
                .satisfies(f -> assertThat(f).contains("Recall@5").contains("0.75"));
        assertThat(MemoryEvalReport.gate(List.of(), 0, null).passed()).as("no recall sample cannot pass").isFalse();

        var previous = MemoryEvalReport.loadBaselines();
        var model = previous.get(0).model();
        var run = new RunInfo(true, model, model, previous.get(0).endpointHost(), "未发送（使用服务商默认值）",
                MemoryEvalRunner.M2B_CONTEXT, MemoryEvalRunner.commit(), Instant.now(), "test");
        var answers = runner.answers(only("coref-01", "recall-02"), fake(q -> "青岛 3000，860 元"), run);
        var comparison = MemoryEvalReport.compare(previous, answers);
        assertThat(comparison.gate().passed()).as(String.valueOf(comparison.gate().failures())).isTrue();
        assertThat(Files.readString(MemoryEvalReport.writeComparison(out, comparison))).contains("质量门槛：达到");
    }

    @Test
    void m2bJdk25LiveRunIsRecordedAsABaseline() {
        // Transcribed from docs/memory/eval-baseline.md 5.7 (JDK 25 live run, reviewed 2026-10-30).
        var m2b = MemoryEvalReport.loadBaselines().stream().filter(b -> b.label().equals("M2b")).findFirst()
                .orElseThrow();
        assertThat(m2b.commit()).isEqualTo("d9d34ca");
        assertThat(m2b.contextInput()).isEqualTo(MemoryEvalRunner.M2B_CONTEXT);
        assertThat(m2b.recallAtK()).isEqualTo(1.0);
        assertThat(m2b.sourceAccuracy()).isEqualTo(1.0);
        assertThat(m2b.falseMemories()).isZero();
        assertThat(m2b.firstTokenP50Ms()).isEqualTo(3450.0);
        assertThat(m2b.retrievalP95Ms()).isEqualTo(14.39);
        assertThat(m2b.cases()).hasSize(24).allSatisfy((id, c) -> assertThat(c.verdict()).isEqualTo("CORRECT"));
        assertThat(m2b.cases().values().stream().mapToLong(MemoryEvalReport.BaselineCase::promptTokens).sum())
                .isEqualTo(36474);
        assertThat(m2b.cases().get("del-01").promptTokens()).isEqualTo(291);
    }

    @Test
    void m1BaselineComparisonUsesSameModelAndCases() throws Exception {
        var previous = MemoryEvalReport.loadBaselines();
        assertThat(previous).extracting(MemoryEvalReport.Baseline::label).containsExactly("M1", "M2a", "M2b");
        // The recorded baselines name one model and endpoint (kept under docs/, outside the product roots).
        var model = previous.get(0).model();
        var host = previous.get(0).endpointHost();
        assertThat(previous).allSatisfy(b -> assertThat(b.model()).isEqualTo(model))
                .allSatisfy(b -> assertThat(b.endpointHost()).isEqualTo(host));
        var sameModel = new RunInfo(true, model, model, host,
                "未发送（使用服务商默认值）", MemoryEvalRunner.M2B_CONTEXT, MemoryEvalRunner.commit(), Instant.now(), "test");
        var answers = runner.answers(only("coref-01", "recall-01", "fact-01"), fake(q -> "青岛 3000"), sameModel);

        var comparison = MemoryEvalReport.compare(previous, answers);

        assertThat(comparison.comparable()).as(comparison.reason()).isTrue();
        var rows = comparison.rows().stream().collect(Collectors.toMap(MemoryEvalReport.ComparisonRow::caseId,
                Function.identity()));
        assertThat(comparison.labels()).containsExactly("M1", "M2a", "M2b", "本次");
        assertThat(rows.get("coref-01").verdicts()).containsExactly("CORRECT", "CORRECT", "CORRECT", "CORRECT");
        assertThat(rows.get("fact-01").verdicts()).containsExactly(null, null, "CORRECT", "WRONG");
        // recall-01 (correct in M2a and M2b) and fact-01 (correct in M2b) are wrong now: regressions against any
        // recorded baseline, reported, never hidden.
        assertThat(comparison.regressions()).containsExactly("recall-01", "fact-01");
        var md = Files.readString(MemoryEvalReport.writeComparison(out, comparison));
        assertThat(md).contains("| 用例 | M1 | M2a | M2b | 本次 | prompt_tokens M1 / M2a / M2b / 本次 |")
                .contains("| --- | --- | --- | --- | --- | --- |").contains("recall-01").contains("回退");
        assertThat(comparison.gate().passed()).isFalse();
        assertThat(comparison.gate().failures()).anySatisfy(f -> assertThat(f).contains("回退").contains("recall-01"));
        assertThat(md).contains("质量门槛").contains("未达到");

        var otherModel = new RunInfo(true, "other-model", "x", "llm.example.test", "未发送（使用服务商默认值）",
                MemoryEvalRunner.M2B_CONTEXT, MemoryEvalRunner.commit(), Instant.now(), "test");
        var other = MemoryEvalReport.compare(previous, runner.answers(only("coref-01"), fake(q -> "青岛 3000"),
                otherModel));
        assertThat(other.comparable()).isFalse();
        assertThat(other.reason()).contains("模型");
    }

    /** {@code answers} for everything but judgement calls, which {@code judge} answers from the transcript. */
    /** Like {@link #judging}, with the existing-items JSON the judgement was given as the first argument. */
    static LlmClient judgingWithExisting(LlmClient answers,
            java.util.function.BiFunction<String, String, String> judge) {
        var marker = "已有条目（可能为空）：\n";
        return (messages, tools) -> {
            var system = messages.getFirst().content();
            if (!system.contains("你是记忆判断器")) {
                return answers.chatStream(messages, tools);
            }
            var existing = system.substring(system.indexOf(marker) + marker.length());
            return Flux.just(new LlmClient.LlmEvent.Completed(judge.apply(existing, messages.getLast().content())),
                    new LlmClient.LlmEvent.Usage(11, 2, 13));
        };
    }

    static LlmClient judging(LlmClient answers, Function<String, String> judge) {
        return (messages, tools) -> messages.getFirst().content().contains("你是记忆判断器")
                ? Flux.just(new LlmClient.LlmEvent.Completed(judge.apply(messages.getLast().content())),
                        new LlmClient.LlmEvent.Usage(11, 2, 13))
                : answers.chatStream(messages, tools);
    }

    /** Message id of the first transcript line containing {@code text}. */
    static String messageIdOf(String transcript, String text) {
        return transcript.lines().filter(l -> l.contains(text) && l.startsWith("[")).findFirst()
                .map(l -> l.substring(1, l.indexOf(']'))).orElse("none");
    }

    private List<Case> only(String... ids) {
        var wanted = List.of(ids);
        var selected = cases.stream().filter(c -> wanted.contains(c.id())).toList();
        assertThat(selected).hasSize(ids.length);
        return selected;
    }

    private static RunInfo fakeRun() {
        return new RunInfo(false, "fake", "n/a", "none", "未发送（使用服务商默认值）", MemoryEvalRunner.M2B_CONTEXT,
                MemoryEvalRunner.commit(), Instant.now(), "test");
    }

    /** {@code answers} plus a valid memory record (usage 11/2/13) for every record-building call. */
    static LlmClient withRecords(LlmClient answers) {
        return (messages, tools) -> messages.getFirst().content().contains("对话记忆索引模块")
                ? Flux.just(new LlmClient.LlmEvent.Completed("{\"summary\":\"记录\",\"entities\":[],\"key_facts\":[],"
                        + "\"decision\":false,\"keywords\":[],\"memory_type\":\"EVENT\"}"),
                        new LlmClient.LlmEvent.Usage(11, 2, 13))
                : answers.chatStream(messages, tools);
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
