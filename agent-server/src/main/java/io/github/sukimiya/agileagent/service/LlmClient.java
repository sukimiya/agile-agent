package io.github.sukimiya.agileagent.service;

import java.util.List;
import java.util.Map;

public interface LlmClient {

    record ToolDefinition(String name, String description, Map<String, Object> parametersSchema) {}

    record ToolCall(String id, String name, String argumentsJson) {}

    record Message(String role, String content, String toolCallId, List<ToolCall> toolCalls) {

        public static Message system(String content) {
            return new Message("system", content, null, null);
        }

        public static Message user(String content) {
            return new Message("user", content, null, null);
        }

        public static Message assistantText(String content) {
            return new Message("assistant", content, null, null);
        }

        public static Message assistantToolCalls(List<ToolCall> calls) {
            return new Message("assistant", null, null, calls);
        }

        public static Message toolResult(String toolCallId, String content) {
            return new Message("tool", content, toolCallId, null);
        }
    }

    class LlmException extends RuntimeException {
        public final int status;
        public LlmException(String message, int status) {
            super(message);
            this.status = status;
        }
    }

    sealed interface LlmEvent permits LlmEvent.ContentDelta, LlmEvent.ToolCallsReady, LlmEvent.Completed, LlmEvent.Usage {
        record ContentDelta(String text) implements LlmEvent {}
        record ToolCallsReady(List<ToolCall> calls) implements LlmEvent {}
        record Completed(String fullText) implements LlmEvent {}
        record Usage(long promptTokens, long completionTokens, long totalTokens) implements LlmEvent {
            public Usage {
                if (promptTokens < 0 || completionTokens < 0 || totalTokens < 0) {
                    throw new IllegalArgumentException("Usage token counts must be nonnegative");
                }
            }
        }
    }

    reactor.core.publisher.Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools);

    /** Per-call request options; {@code maxTokens == null} means the provider default (nothing is sent). */
    record CallOptions(Integer maxTokens) {}

    /**
     * A call with request options. Clients that cannot apply them (test doubles, adapters) inherit this default,
     * which ignores the options and makes the plain call.
     */
    default reactor.core.publisher.Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools,
            CallOptions options) {
        return chatStream(messages, tools);
    }

    default reactor.core.publisher.Mono<String> summarize(String content) {
        var messages = List.of(
                Message.system("你是一个文档摘要助手。用一到两句话总结这份文档讲的是什么、"
                        + "适用于回答哪类问题，供审核人员快速判断这份知识库文档的用途。"
                        + "只输出摘要正文，不要额外解释。"),
                Message.user(content));
        return chatStream(messages, List.of())
                .ofType(LlmEvent.Completed.class)
                .map(LlmEvent.Completed::fullText)
                .next();
    }
}
