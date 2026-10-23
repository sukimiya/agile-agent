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
 * Real-model end-to-end baseline on the current full-history path. Opt-in only:
 * {@code mvn -B -Pmemory-eval-live -pl agent-server -am test} with {@code MEMORY_EVAL_LLM_BASE_URL},
 * {@code MEMORY_EVAL_LLM_API_KEY} and {@code MEMORY_EVAL_LLM_MODEL} set (optionally
 * {@code MEMORY_EVAL_LLM_MODEL_VERSION}); missing configuration fails the run. Results go to
 * {@code agent-server/target/memory-eval/} (or {@code -Dmemory.eval.output=...}). There is no quality threshold;
 * the run fails only when a case could not be executed (verdict ERROR), after the results are written.
 */
@Tag("memory-eval-live")
class MemoryEvalLiveTest {

    @Test
    void recordsEndToEndAnswerBaselineWithARealModel() {
        var config = MemoryEvalLiveConfig.fromEnv(System.getenv());
        var out = Path.of(System.getProperty("memory.eval.output", "target/memory-eval"));
        var environment = "H2 (MODE=MySQL) + local test Redis, JDK " + System.getProperty("java.version") + ", "
                + System.getProperty("os.name");
        var cases = MemoryEvalCases.load();
        try (var stack = MemoryTestStack.h2()) {
            var runner = new MemoryEvalRunner(stack);
            var llm = new OpenAiCompatibleLlmClient(config.baseUrl(), config.apiKey(), config.model(),
                    WebClient.builder());
            var answers = runner.answers(cases, llm, config.runInfo(environment));
            MemoryEvalReport.writeAnswers(out, answers);
            MemoryEvalReport.writeComponents(out, runner.components(cases, environment), stack.metrics());

            assertThat(answers.results()).hasSize((int) cases.stream().filter(Case::answer).count());
            assertThat(answers.results()).as("cases that could not be executed (see answer-results.md)")
                    .noneMatch(r -> r.verdict() == Verdict.ERROR);
        }
    }
}
