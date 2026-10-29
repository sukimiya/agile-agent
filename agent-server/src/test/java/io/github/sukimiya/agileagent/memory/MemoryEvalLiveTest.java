package io.github.sukimiya.agileagent.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.sukimiya.agileagent.memory.MemoryEvalCases.Case;
import io.github.sukimiya.agileagent.memory.MemoryEvalCases.Verdict;
import io.github.sukimiya.agileagent.service.OpenAiCompatibleLlmClient;

/**
 * Real-model end-to-end baseline on the M2b path (context budget per model window, basic recall, turn records
 * written with the same model, memory judgement and the profile after each case's conversation goes idle), compared
 * with the recorded M1 / M2a baselines of the same model ({@code baseline-comparison.md}). Opt-in only:
 * {@code mvn -B -Pmemory-eval-live -pl agent-server -am test} with {@code MEMORY_EVAL_LLM_BASE_URL},
 * {@code MEMORY_EVAL_LLM_API_KEY} and {@code MEMORY_EVAL_LLM_MODEL} set (optionally
 * {@code MEMORY_EVAL_LLM_MODEL_VERSION}); missing configuration fails the run. Results go to
 * {@code agent-server/target/memory-eval/} (or {@code -Dmemory.eval.output=...}). There is no quality threshold;
 * the run fails only when a case could not be executed (verdict ERROR), after the results are written.
 * {@code -Dmemory.eval.minMatchedTerms=N} overrides the weak-hit threshold for the run (T5 Q3 tuning); the value
 * used is in the report's parameter line, next to the engineering threshold sweep and the replay sweep over
 * this run's data. {@code -Dmemory.eval.cases=id,id} runs only those cases (a smaller, cheaper run).
 */
@Tag("memory-eval-live")
class MemoryEvalLiveTest {

    @Test
    void recordsEndToEndAnswerBaselineWithARealModel() {
        var config = MemoryEvalLiveConfig.fromEnv(System.getenv());
        var out = Path.of(System.getProperty("memory.eval.output", "target/memory-eval"));
        var environment = "H2 (MODE=MySQL) + local test Redis, JDK " + System.getProperty("java.version") + ", "
                + System.getProperty("os.name");
        var only = System.getProperty("memory.eval.cases", "").strip();
        var cases = MemoryEvalCases.load().stream()
                .filter(c -> only.isEmpty() || java.util.Arrays.asList(only.split(",")).contains(c.id())).toList();
        assertThat(cases).as("selected cases").isNotEmpty();
        try (var stack = MemoryTestStack.h2()) {
            var threshold = System.getProperty("memory.eval.minMatchedTerms");
            if (threshold != null && !threshold.isBlank()) {
                stack.props().getRecall().setMinMatchedTerms(Integer.parseInt(threshold.trim()));
            }
            var runner = new MemoryEvalRunner(stack);
            var llm = new OpenAiCompatibleLlmClient(config.baseUrl(), config.apiKey(), config.model(),
                    WebClient.builder());
            var answers = runner.answers(cases, llm, config.runInfo(environment));
            MemoryEvalReport.writeAnswers(out, answers);
            var comparison = MemoryEvalReport.compare(MemoryEvalReport.loadBaselines(), answers);
            MemoryEvalReport.writeComparison(out, comparison);
            MemoryEvalReport.writeReplay(out, runner.replaySweep(java.util.List.of(1, 2, 3)), runner.settingsLine());
            MemoryEvalReport.writeComponents(out, runner.components(cases, environment), stack.metrics());
            MemoryEvalReport.writeSweep(out, runner.thresholdSweep(cases, java.util.List.of(1, 2, 3)),
                    runner.settingsLine());

            assertThat(answers.results()).hasSize((int) cases.stream().filter(Case::answer).count());
            assertThat(answers.results()).as("cases that could not be executed (see answer-results.md)")
                    .noneMatch(r -> r.verdict() == Verdict.ERROR);
        }
    }
}
