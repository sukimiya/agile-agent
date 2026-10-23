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

/**
 * Writes the two baselines to separate files (JSON for machines, Markdown for people). They are never merged
 * into one pass rate: component status has no verdicts, answer results have no component status.
 */
final class MemoryEvalReport {

    static final String COMPONENT_JSON = "component-status.json";
    static final String COMPONENT_MD = "component-status.md";
    static final String ANSWER_JSON = "answer-results.json";
    static final String ANSWER_MD = "answer-results.md";

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
        md.append("# Memory 评测：组件状态（M1）\n\n");
        md.append("> 工程测试结果：fake LLM 驱动，验证 M1 已实现组件；未实现组件记为“不适用”，不计为失败。\n\n");
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
        md.append("\n## 基础指标口径（本次运行的进程内读数）\n\n| 指标 | 值 | 说明 |\n| --- | --- | --- |\n");
        metrics.snapshot().values().forEach(r -> md.append("| ").append(r.name()).append(" | ").append(r.display())
                .append(" | ").append(r.note() == null ? "" : cell(r.note())).append(" |\n"));
        return write(dir, COMPONENT_JSON, json, COMPONENT_MD, md.toString());
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

        var md = new StringBuilder();
        md.append("# Memory 评测：端到端回答结果\n\n");
        if (!run.live()) {
            md.append("> 本结果由 fake LLM 生成，仅用于验证评测框架，不能作为端到端基线。\n\n");
        } else {
            md.append("> 真实模型在当前“完整历史注入”路径上的回答；判定为关键词规则，记录时需人工复核。M1 不设质量门槛。\n\n");
        }
        md.append("- 模型：").append(run.model()).append('\n');
        md.append("- 模型版本：").append(run.modelVersion()).append('\n');
        md.append("- 接口主机：").append(run.endpointHost()).append('\n');
        md.append("- 温度等采样参数：").append(run.temperature()).append('\n');
        md.append("- 上下文输入方式：完整历史（").append(run.contextInput())
                .append("）：已完成执行的全部原文消息，按顺序注入路由与回答两次模型调用\n");
        md.append("- 提交：`").append(run.commit()).append("`\n");
        md.append("- 开始时间：").append(run.startedAt()).append('\n');
        md.append("- 环境：").append(run.environment()).append('\n');
        md.append("- 判定汇总：正确 ").append(verdicts.get(Verdict.CORRECT)).append("，部分正确 ")
                .append(verdicts.get(Verdict.PARTIAL)).append("，错误 ").append(verdicts.get(Verdict.WRONG))
                .append("，未能执行 ").append(verdicts.get(Verdict.ERROR)).append('\n');
        byPhase.forEach((phase, counts) -> md.append("  - 所需阶段 ").append(phase).append("：").append(counts).append('\n'));
        md.append("\n| 用例 | 类别 | 所需阶段 | 判定 | 回答 | 期望 | 注入消息数 | 估算 Token | 实际 prompt_tokens | 耗时 ms |\n");
        md.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (var r : baseline.results()) {
            md.append("| ").append(r.caseId()).append(" | ").append(r.category()).append(" | ").append(r.requires())
                    .append(" | ").append(verdict(r)).append(" | ").append(cell(truncate(r.answer()))).append(" | ")
                    .append(cell(r.expected())).append(" | ")
                    .append(r.context() == null ? "" : String.valueOf(r.context().injectedMessages())).append(" | ")
                    .append(r.context() == null ? "" : String.valueOf(r.context().estimatedTokens())).append(" | ")
                    .append(r.context() == null || r.context().promptTokens() == null ? "未上报"
                            : String.valueOf(r.context().promptTokens()))
                    .append(" | ").append(r.durationMs()).append(" |\n");
        }
        md.append("\n“实际 prompt_tokens”为探针执行中路由与回答两次调用的合计（服务商上报）；“估算 Token”为注入历史的启发式估算。\n");
        return write(dir, ANSWER_JSON, json, ANSWER_MD, md.toString());
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
        return List.of(COMPONENT_JSON, COMPONENT_MD, ANSWER_JSON, ANSWER_MD);
    }
}
