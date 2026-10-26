package io.github.sukimiya.agileagent.memory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.github.sukimiya.agileagent.memory.MemoryEvalCases.Verdict;
import io.github.sukimiya.agileagent.memory.MemoryEvalRunner.AnswerBaseline;
import io.github.sukimiya.agileagent.memory.MemoryEvalRunner.AnswerResult;
import io.github.sukimiya.agileagent.memory.MemoryEvalRunner.ComponentBaseline;
import io.github.sukimiya.agileagent.memory.MemoryEvalRunner.ComponentStatus;
import io.github.sukimiya.agileagent.memory.MemoryEvalRunner.PhaseCost;
import io.github.sukimiya.agileagent.memory.MemoryEvalRunner.RecallInfo;
import io.github.sukimiya.agileagent.memory.MemoryEvalRunner.RetrievalStats;
import io.github.sukimiya.agileagent.memory.MemoryEvalRunner.ThresholdResult;

/**
 * Writes the two baselines to separate files (JSON for machines, Markdown for people). They are never merged
 * into one pass rate: component status has no verdicts, answer results have no component status.
 */
final class MemoryEvalReport {

    static final String COMPONENT_JSON = "component-status.json";
    static final String COMPONENT_MD = "component-status.md";
    static final String ANSWER_JSON = "answer-results.json";
    static final String ANSWER_MD = "answer-results.md";
    static final String SWEEP_JSON = "recall-threshold-sweep.json";
    static final String SWEEP_MD = "recall-threshold-sweep.md";

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final int MD_ANSWER_CHARS = 200;

    private MemoryEvalReport() {
    }

    /** Writes component-status.json and .md; returns the Markdown path. */
    static Path writeComponents(Path dir, ComponentBaseline baseline, MemoryMetrics metrics) {
        var summary = baseline.summary();
        var json = new LinkedHashMap<String, Object>();
        json.put("kind", "component-status");
        json.put("environment", baseline.environment());
        json.put("commit", baseline.commit());
        json.put("runAt", baseline.runAt().toString());
        json.put("summary", summary);
        json.put("results", baseline.results());
        json.put("retrieval", baseline.retrieval());
        var readings = new ArrayList<Map<String, Object>>();
        metrics.snapshot().values().forEach(r -> {
            var m = new LinkedHashMap<String, Object>();
            m.put("name", r.name());
            m.put("availability", r.availability());
            m.put("value", r.value());
            m.put("display", r.display());
            m.put("note", r.note());
            readings.add(m);
        });
        json.put("metrics", readings);

        var md = new StringBuilder();
        md.append("# Memory 评测：组件状态（M2a）\n\n");
        md.append("> 工程测试结果：fake LLM 驱动，验证 M1 与 M2a 已实现组件（上下文预算、轮次记录、基础 Recall、能力背景）；"
                + "M2b 组件记为“不适用”，不计为失败。\n\n");
        md.append("- 提交：`").append(baseline.commit()).append("`\n");
        md.append("- 环境：").append(baseline.environment()).append('\n');
        md.append("- 运行时间：").append(baseline.runAt()).append('\n');
        md.append("- 汇总：PASS ").append(summary.get(ComponentStatus.PASS)).append("，FAIL ")
                .append(summary.get(ComponentStatus.FAIL)).append("，不适用 ")
                .append(summary.get(ComponentStatus.NOT_APPLICABLE)).append("\n\n");
        md.append("| 用例 | 类别 | 所需阶段 | 组件 | 状态 | 说明 |\n| --- | --- | --- | --- | --- | --- |\n");
        for (var r : baseline.results()) {
            md.append("| ").append(r.caseId()).append(" | ").append(r.category()).append(" | ").append(r.requires())
                    .append(" | ").append(r.component()).append(" | ").append(status(r.status())).append(" | ")
                    .append(cell(r.detail())).append(" |\n");
        }
        retrieval(md, baseline.retrieval(), "## 检索质量（工程路径）");
        md.append("\n## 基础指标口径（本次运行的进程内读数）\n\n| 指标 | 值 | 说明 |\n| --- | --- | --- |\n");
        metrics.snapshot().values().forEach(r -> md.append("| ").append(r.name()).append(" | ").append(r.display())
                .append(" | ").append(r.note() == null ? "" : cell(r.note())).append(" |\n"));
        return write(dir, COMPONENT_JSON, json, COMPONENT_MD, md.toString());
    }

    /** Recall@K, source accuracy, latency percentiles and the per-case recall table. */
    private static void retrieval(StringBuilder md, RetrievalStats r, String title) {
        if (r == null) {
            return;
        }
        md.append('\n').append(title).append("\n\n");
        md.append("- Recall 用例：").append(r.cases()).append("；Recall@").append(r.k()).append("：").append(ratio(r.recallAtK()))
                .append("；来源正确率：").append(ratio(r.sourceAccuracy())).append('\n');
        md.append("- 检索延迟 p50：").append(ms(r.p50Ms())).append("，p95：").append(ms(r.p95Ms())).append("（")
                .append(r.latencySamples()).append(" 次 Recall，含每个轮次）\n\n");
        md.append("| 用例 | 召回条数 | 原因 | 命中前 ").append(r.k()).append(" | 来源正确条数 | 跨会话 | 已注入提示 | 延迟 ms |\n");
        md.append("| --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (RecallInfo i : r.perCase()) {
            md.append("| ").append(i.caseId()).append(" | ").append(i.items()).append(" | ")
                    .append(String.join("、", i.reasons())).append(" | ")
                    .append(i.hitAtK() == null ? "不应召回" : i.hitAtK() ? "是" : "否").append(" | ")
                    .append(i.correctItems()).append(" | ").append(i.crossConversation() ? "是" : "否").append(" | ")
                    .append(i.referenceInjected() ? "是" : "否").append(" | ").append(i.latencyMs()).append(" |\n");
        }
        md.append("\n来源正确率 = 指向期望来源轮次的召回条数 / 全部召回条数（不应召回的用例返回的条目都计为错误）。\n");
    }

    private static String ratio(Double v) {
        return v == null ? "无样本" : String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static String ms(Double v) {
        return v == null ? "无样本" : v + " ms";
    }

    private static String cost(PhaseCost c) {
        return c.calls() + " 次调用，prompt " + c.promptTokens() + " / completion " + c.completionTokens() + " / total "
                + c.totalTokens() + " Token";
    }

    /** Writes the weak-hit threshold sweep (T5 Q3); returns the Markdown path. */
    static Path writeSweep(Path dir, List<ThresholdResult> sweep, String configured) {
        return writeSweep(dir, sweep, configured, "工程路径（fake LLM），只看检索结果", SWEEP_JSON, SWEEP_MD);
    }

    static final String REPLAY_JSON = "recall-threshold-replay.json";
    static final String REPLAY_MD = "recall-threshold-replay.md";

    /** The replay sweep over the data of a real-model answer run. */
    static Path writeReplay(Path dir, List<ThresholdResult> sweep, String configured) {
        return writeSweep(dir, sweep, configured, "回放：在真实模型运行写入的原文与记录上重放探针检索（不调用模型）",
                REPLAY_JSON, REPLAY_MD);
    }

    private static Path writeSweep(Path dir, List<ThresholdResult> sweep, String configured, String note,
            String jsonName, String mdName) {
        var json = new LinkedHashMap<String, Object>();
        json.put("kind", "recall-threshold-sweep");
        json.put("configured", configured);
        json.put("results", sweep);
        var md = new StringBuilder("# Recall 弱命中阈值扫描（min-matched-terms）\n\n");
        md.append("> ").append(note).append("；配置值：").append(configured).append("\n\n");
        md.append("| min-matched-terms | Recall@5 | 来源正确率 | 召回条数 | p50 ms | p95 ms |\n| --- | --- | --- | --- | --- | --- |\n");
        for (var t : sweep) {
            var r = t.retrieval();
            md.append("| ").append(t.minMatchedTerms()).append(" | ").append(ratio(r.recallAtK())).append(" | ")
                    .append(ratio(r.sourceAccuracy())).append(" | ")
                    .append(r.perCase().stream().mapToInt(RecallInfo::items).sum()).append(" | ")
                    .append(ms(r.p50Ms())).append(" | ").append(ms(r.p95Ms())).append(" |\n");
        }
        md.append("\n逐用例：\n\n");
        for (var t : sweep) {
            md.append("- min-matched-terms=").append(t.minMatchedTerms()).append("：");
            md.append(String.join("；", t.retrieval().perCase().stream().map(i -> i.caseId() + " " + i.items() + " 条"
                    + (i.hitAtK() == null ? "" : i.hitAtK() ? "（命中）" : "（未命中）")).toList())).append('\n');
        }
        return write(dir, jsonName, json, mdName, md.toString());
    }

    /** Writes answer-results.json and .md; returns the Markdown path. */
    static Path writeAnswers(Path dir, AnswerBaseline baseline) {
        var run = baseline.run();
        var verdicts = new EnumMap<Verdict, Long>(Verdict.class);
        for (var v : Verdict.values()) {
            verdicts.put(v, baseline.results().stream().filter(r -> r.verdict() == v).count());
        }
        var byPhase = new TreeMap<String, Map<Verdict, Long>>();
        for (var r : baseline.results()) {
            byPhase.computeIfAbsent(r.requires(), k -> new EnumMap<>(Verdict.class)).merge(r.verdict(), 1L, Long::sum);
        }
        var json = new LinkedHashMap<String, Object>();
        json.put("kind", run.live() ? "answer-results-live" : "answer-results-fake-engineering");
        var runJson = new LinkedHashMap<String, Object>();
        runJson.put("live", run.live());
        runJson.put("model", run.model());
        runJson.put("modelVersion", run.modelVersion());
        runJson.put("endpointHost", run.endpointHost());
        runJson.put("temperature", run.temperature());
        runJson.put("contextInput", run.contextInput());
        runJson.put("commit", run.commit());
        runJson.put("startedAt", run.startedAt().toString());
        runJson.put("environment", run.environment());
        json.put("run", runJson);
        json.put("verdicts", verdicts);
        json.put("verdictsByRequiredPhase", byPhase);
        json.put("results", baseline.results());
        json.put("retrieval", baseline.retrieval());
        json.put("cost", baseline.cost());
        json.put("firstTokenP50Ms", baseline.firstTokenP50Ms());
        json.put("firstTokenP95Ms", baseline.firstTokenP95Ms());
        json.put("probePromptTokens", baseline.probePromptTokens());
        json.put("settings", baseline.settings());

        var md = new StringBuilder();
        md.append("# Memory 评测：端到端回答结果\n\n");
        if (!run.live()) {
            md.append("> 本结果由 fake LLM 生成，仅用于验证评测框架，不能作为端到端基线。\n\n");
        } else {
            md.append("> 真实模型在 M2a 路径（上下文预算与基础 Recall）上的回答；判定为关键词规则，记录时需人工复核。\n\n");
        }
        md.append("- 模型：").append(run.model()).append('\n');
        md.append("- 模型版本：").append(run.modelVersion()).append('\n');
        md.append("- 接口主机：").append(run.endpointHost()).append('\n');
        md.append("- 温度等采样参数：").append(run.temperature()).append('\n');
        md.append("- 上下文输入方式：上下文预算与基础 Recall（").append(run.contextInput())
                .append("）：按模型窗口保留近期完整轮次，窗口外命中的原始轮次作为参考资料注入\n");
        md.append("- 参数：").append(baseline.settings()).append('\n');
        md.append("- 提交：`").append(run.commit()).append("`\n");
        md.append("- 开始时间：").append(run.startedAt()).append('\n');
        md.append("- 环境：").append(run.environment()).append('\n');
        md.append("- 判定汇总：正确 ").append(verdicts.get(Verdict.CORRECT)).append("，部分正确 ")
                .append(verdicts.get(Verdict.PARTIAL)).append("，错误 ").append(verdicts.get(Verdict.WRONG))
                .append("，未能执行 ").append(verdicts.get(Verdict.ERROR)).append('\n');
        byPhase.forEach((phase, counts) -> md.append("  - 所需阶段 ").append(phase).append("：").append(counts).append('\n'));
        md.append("- 首字延迟（探针）p50：").append(ms(baseline.firstTokenP50Ms())).append("，p95：")
                .append(ms(baseline.firstTokenP95Ms())).append('\n');
        md.append("- 探针 prompt_tokens 合计：").append(baseline.probePromptTokens()).append('\n');
        var cost = baseline.cost();
        md.append("- 模型成本：用例轮次 ").append(cost(cost.turns())).append("；探针 ").append(cost(cost.probes()))
                .append("；轮次记录生成 ").append(cost(cost.records())).append("（").append(cost.recordTurns())
                .append(" 个轮次，写入 ").append(cost.recordsWritten()).append(" 条记录）\n");
        md.append("\n| 用例 | 类别 | 所需阶段 | 判定 | 回答 | 期望 | 注入消息数 | 估算 Token | 实际 prompt_tokens | 召回 | 首字 ms | 耗时 ms |\n");
        md.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (var r : baseline.results()) {
            md.append("| ").append(r.caseId()).append(" | ").append(r.category()).append(" | ").append(r.requires())
                    .append(" | ").append(verdict(r)).append(" | ").append(cell(truncate(r.answer()))).append(" | ")
                    .append(cell(r.expected())).append(" | ")
                    .append(r.context() == null ? "" : String.valueOf(r.context().injectedMessages())).append(" | ")
                    .append(r.context() == null ? "" : String.valueOf(r.context().estimatedTokens())).append(" | ")
                    .append(r.context() == null || r.context().promptTokens() == null ? "未上报"
                            : String.valueOf(r.context().promptTokens()))
                    .append(" | ").append(recall(r.recall()))
                    .append(" | ").append(r.firstTokenMs() == null ? "" : String.valueOf(r.firstTokenMs()))
                    .append(" | ").append(r.durationMs()).append(" |\n");
        }
        md.append("\n“实际 prompt_tokens”为探针执行中全部模型调用的合计（服务商上报）；“估算 Token”为注入近期历史的启发式估算；"
                + "“召回”为探针的召回条数与原因（*表示原始轮次已作为参考资料注入）。\n");
        retrieval(md, baseline.retrieval(), "## 检索质量（本次回答运行）");
        var records = baseline.results().stream().filter(r -> r.sourceRecord() != null).toList();
        if (!records.isEmpty()) {
            md.append("\n## 来源轮次的记录（由本次模型生成）\n\n| 用例 | 记录要点与关键词 |\n| --- | --- |\n");
            records.forEach(r -> md.append("| ").append(r.caseId()).append(" | ").append(cell(r.sourceRecord())).append(" |\n"));
        }
        return write(dir, ANSWER_JSON, json, ANSWER_MD, md.toString());
    }

    private static String recall(RecallInfo i) {
        if (i == null) {
            return "";
        }
        return i.items() == 0 ? "0" : i.items() + "（" + String.join("、", i.reasons()) + "）" + (i.referenceInjected() ? "*" : "");
    }

    private static String verdict(AnswerResult r) {
        return switch (r.verdict()) {
            case CORRECT -> "正确";
            case PARTIAL -> "部分正确";
            case WRONG -> "错误";
            case ERROR -> "未能执行（" + cell(r.error()) + "）";
        };
    }

    private static String status(ComponentStatus status) {
        return switch (status) {
            case PASS -> "PASS";
            case FAIL -> "FAIL";
            case NOT_APPLICABLE -> "不适用";
        };
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > MD_ANSWER_CHARS ? s.substring(0, MD_ANSWER_CHARS) + "…" : s;
    }

    private static String cell(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\r", "").replace("\n", "<br>");
    }

    private static Path write(Path dir, String jsonName, Object json, String mdName, String md) {
        try {
            Files.createDirectories(dir);
            JSON.writeValue(dir.resolve(jsonName).toFile(), json);
            var mdPath = dir.resolve(mdName);
            Files.writeString(mdPath, md);
            return mdPath;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> files() {
        return List.of(COMPONENT_JSON, COMPONENT_MD, ANSWER_JSON, ANSWER_MD, SWEEP_JSON, SWEEP_MD, REPLAY_JSON,
                REPLAY_MD);
    }
}
