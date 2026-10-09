package io.github.sukimiya.agileagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class LlmClientSummarizeTest {

    @Test
    void summarizeReturnsCompletedFullText() {
        LlmClient client = (messages, tools) -> Flux.just(
                new LlmClient.LlmEvent.ContentDelta("这是"),
                new LlmClient.LlmEvent.ContentDelta("摘要"),
                new LlmClient.LlmEvent.Completed("这是摘要"));

        StepVerifier.create(client.summarize("一份很长的文档内容"))
                .expectNext("这是摘要")
                .verifyComplete();
    }

    @Test
    void summarizePassesContentAsUserMessageWithNoTools() {
        var captured = new AtomicReference<List<LlmClient.Message>>();
        LlmClient client = (messages, tools) -> {
            captured.set(messages);
            assertThat(tools).isEmpty();
            return Flux.just(new LlmClient.LlmEvent.Completed("摘要"));
        };

        client.summarize("文档内容").block();

        assertThat(captured.get()).extracting(LlmClient.Message::role).contains("system", "user");
        assertThat(captured.get()).extracting(LlmClient.Message::content).contains("文档内容");
    }
}
