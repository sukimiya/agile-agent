package io.github.sukimiya.agileagent.memory.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.command.MemoryCommand.Kind;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.spi.capability.ToolDefinition;
import reactor.core.publisher.Flux;

/** The forget / restore classification call with a fake model: JSON mode, only the input, failure is "no". */
class MemoryCommandClassifierTest {

    static final Duration WAIT = Duration.ofSeconds(5);

    final List<List<LlmClient.Message>> calls = new CopyOnWriteArrayList<>();
    final List<LlmClient.CallOptions> options = new CopyOnWriteArrayList<>();
    final List<LlmClient.LlmEvent> usage = new CopyOnWriteArrayList<>();
    final List<String> outcomes = new CopyOnWriteArrayList<>();

    LlmClient model(Function<List<LlmClient.Message>, Flux<LlmClient.LlmEvent>> answer) {
        return new LlmClient() {
            @Override
            public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools) {
                return chatStream(messages, tools, new CallOptions(null));
            }

            @Override
            public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools, CallOptions o) {
                calls.add(messages);
                options.add(o);
                return answer.apply(messages);
            }
        };
    }

    java.util.Optional<MemoryCommand> classify(LlmClient llm, String input) {
        return MemoryCommandClassifier.classify(llm, input, usage::add, Duration.ofMillis(500), outcomes::add)
                .block(WAIT);
    }

    @Test
    void forgetAndRestoreAreReadFromJsonModeOutput() {
        var llm = model(m -> Flux.just(new LlmClient.LlmEvent.Completed(
                "{\"command\":\"forget\",\"topic\":\" 口腔医院复诊 \"}"), new LlmClient.LlmEvent.Usage(4, 2, 6)));

        assertThat(classify(llm, "口腔医院复诊的事别记了")).hasValue(new MemoryCommand(Kind.FORGET, "口腔医院复诊"));
        assertThat(options).singleElement().satisfies(o -> {
            assertThat(o.jsonObject()).isTrue();
            assertThat(o.maxTokens()).isEqualTo(MemoryCommandClassifier.MAX_OUTPUT_TOKENS);
        });
        assertThat(calls.getFirst()).hasSize(2);
        assertThat(calls.getFirst().get(0).role()).isEqualTo("system");
        assertThat(calls.getFirst().get(1).content()).isEqualTo("{\"input\":\"口腔医院复诊的事别记了\"}");
        assertThat(usage).hasSize(2);
        assertThat(outcomes).containsExactly("FORGET");

        var restore = model(m -> Flux.just(new LlmClient.LlmEvent.Completed(
                "```json\n{\"command\":\"RESTORE\",\"topic\":\"复诊\"}\n```")));
        assertThat(classify(restore, "把复诊那条想起来")).hasValue(new MemoryCommand(Kind.RESTORE, "复诊"));
    }

    @Test
    void anythingElseIsNotACommand() {
        for (var out : List.of("{\"command\":\"NONE\",\"topic\":\"\"}", "{\"command\":\"FORGET\",\"topic\":\"\"}",
                "{\"command\":\"DELETE_ALL\",\"topic\":\"x\"}", "不是 JSON", "[]", "",
                "{\"command\":\"FORGET\",\"topic\":\"" + "长".repeat(300) + "\"}")) {
            var llm = model(m -> Flux.just(new LlmClient.LlmEvent.Completed(out)));
            assertThat(classify(llm, "忘记")).as(out).isEmpty();
        }
        assertThat(classify(model(m -> Flux.empty()), "忘记")).isEmpty();
    }

    @Test
    void modelFailureOrTimeoutIsNotACommand() {
        assertThat(classify(model(m -> Flux.error(new IllegalStateException("down"))), "忘记它")).isEmpty();
        assertThat(classify(model(m -> Flux.never()), "忘记它")).isEmpty();
        assertThat(outcomes).containsExactly("FAILED", "FAILED");
    }
}
