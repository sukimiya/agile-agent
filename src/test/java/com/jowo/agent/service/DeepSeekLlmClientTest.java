package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class DeepSeekLlmClientTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void buildRequestBodyCarriesModelMessagesAndTools() throws Exception {
        var messages = List.of(
                LlmClient.Message.system("sys"),
                LlmClient.Message.user("hello"),
                LlmClient.Message.assistantToolCalls(List.of(new LlmClient.ToolCall("c1", "getApiDetail", "{\"a\":1}"))),
                LlmClient.Message.toolResult("c1", "detail text"));
        var tools = List.of(new LlmClient.ToolDefinition("getApiDetail", "desc",
                Map.of("type", "object", "properties", Map.of())));

        var body = DeepSeekLlmClient.buildRequestBody("deepseek-chat", messages, tools);
        String asJson = json.writeValueAsString(body);
        JsonNode n = json.readTree(asJson);

        assertThat(n.path("model").asText()).isEqualTo("deepseek-chat");
        assertThat(n.path("messages")).hasSize(4);
        assertThat(n.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(n.path("messages").get(2).path("tool_calls").get(0).path("function").path("name").asText())
                .isEqualTo("getApiDetail");
        assertThat(n.path("messages").get(3).path("tool_call_id").asText()).isEqualTo("c1");
        assertThat(n.path("tools").get(0).path("function").path("name").asText()).isEqualTo("getApiDetail");
        assertThat(n.path("tool_choice").asText()).isEqualTo("auto");
    }
}
