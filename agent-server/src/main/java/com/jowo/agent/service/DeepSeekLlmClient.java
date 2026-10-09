package com.jowo.agent.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jowo.agent.config.DeepSeekProperties;

import reactor.core.publisher.Flux;

@Component
public class DeepSeekLlmClient implements LlmClient {

    private final DeepSeekProperties props;
    private final WebClient web;

    public DeepSeekLlmClient(DeepSeekProperties props, WebClient.Builder webBuilder) {
        this.props = props;
        this.web = webBuilder
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .build();
    }

    static Map<String, Object> buildRequestBody(String model, List<Message> messages, List<ToolDefinition> tools) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", model);
        body.put("messages", messages.stream().map(DeepSeekLlmClient::toWireMessage).toList());
        body.put("tools", tools.stream().map(DeepSeekLlmClient::toWireTool).toList());
        body.put("tool_choice", "auto");
        return body;
    }

    private static Map<String, Object> toWireMessage(Message m) {
        var wire = new LinkedHashMap<String, Object>();
        wire.put("role", m.role());
        if (m.content() != null) wire.put("content", m.content());
        if (m.toolCallId() != null) wire.put("tool_call_id", m.toolCallId());
        if (m.toolCalls() != null) {
            wire.put("tool_calls", m.toolCalls().stream().map(tc -> Map.of(
                    "id", tc.id(),
                    "type", "function",
                    "function", Map.of("name", tc.name(), "arguments", tc.argumentsJson())
            )).toList());
        }
        return wire;
    }

    private static Map<String, Object> toWireTool(ToolDefinition t) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", t.name(),
                        "description", t.description(),
                        "parameters", t.parametersSchema()
                )
        );
    }

    @Override
    public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools) {
        var body = buildRequestBody(props.model(), messages, tools);
        body.put("stream", true);
        return Flux.defer(() -> {
            var aggregator = new StreamAggregator();
            return web.post().uri("/chat/completions")
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp ->
                            resp.bodyToMono(String.class).defaultIfEmpty("")
                                .map(b -> new LlmException("DeepSeek error: " + b, resp.statusCode().value())))
                    .bodyToFlux(String.class)
                    .takeWhile(data -> !"[DONE]".equals(data.trim()))
                    .concatMap(data -> Flux.fromIterable(aggregator.onChunk(parseChunk(data))))
                    .onErrorMap(e -> !(e instanceof LlmException),
                            e -> new LlmException("DeepSeek unreachable: " + e.getMessage(), 502));
        });
    }

    private static JsonNode parseChunk(String data) {
        try {
            return new ObjectMapper().readTree(data);
        } catch (Exception e) {
            throw new LlmException("DeepSeek stream parse error: " + e.getMessage(), 502);
        }
    }

    static final class StreamAggregator {
        private final StringBuilder content = new StringBuilder();
        private final java.util.SortedMap<Integer, ToolCallBuilder> builders = new java.util.TreeMap<>();

        private static final class ToolCallBuilder {
            String id = "";
            String name = "";
            final StringBuilder args = new StringBuilder();
            ToolCall build() { return new ToolCall(id, name, args.toString()); }
        }

        List<LlmEvent> onChunk(JsonNode chunk) {
            var out = new java.util.ArrayList<LlmEvent>();
            JsonNode choice = chunk.path("choices").path(0);
            JsonNode delta = choice.path("delta");

            String piece = delta.path("content").asText(null);
            if (piece != null && !piece.isEmpty()) {
                content.append(piece);
                out.add(new LlmEvent.ContentDelta(piece));
            }
            for (JsonNode tc : delta.path("tool_calls")) {
                var b = builders.computeIfAbsent(tc.path("index").asInt(), i -> new ToolCallBuilder());
                if (tc.hasNonNull("id")) b.id = tc.path("id").asText();
                JsonNode fn = tc.path("function");
                if (fn.hasNonNull("name")) b.name = fn.path("name").asText();
                b.args.append(fn.path("arguments").asText(""));
            }
            String finish = choice.path("finish_reason").asText(null);
            if ("tool_calls".equals(finish)) {
                out.add(new LlmEvent.ToolCallsReady(builders.values().stream().map(ToolCallBuilder::build).toList()));
            } else if (finish != null && !finish.isEmpty()) {
                out.add(new LlmEvent.Completed(content.toString()));
            }
            return out;
        }
    }
}
