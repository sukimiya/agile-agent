package io.github.sukimiya.agileagent.memory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ToolRecordSanitizerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void redactsSensitiveJsonFieldsAtAnyDepthAndKeepsOrdinaryOnes() throws Exception {
        var input = """
                {"query":"stage api","access_token":"tok-SECRET-1","max_tokens":256,
                 "auth":{"password":"pw-SECRET-2","apiKey":"key-SECRET-3","user":"alice"},
                 "headers":[{"Authorization":"Bearer bearer-SECRET-4"},{"Cookie":"sid=SECRET-5"}],
                 "client_secret":"SECRET-6","refreshToken":"SECRET-7","prompt_tokens":3}
                """;

        var output = ToolRecordSanitizer.sanitize(input);

        assertThat(output).doesNotContain("SECRET");
        var node = JSON.readTree(output);
        assertThat(node.path("query").asText()).isEqualTo("stage api");
        assertThat(node.path("max_tokens").asInt()).isEqualTo(256);
        assertThat(node.path("prompt_tokens").asInt()).isEqualTo(3);
        assertThat(node.path("auth").path("user").asText()).isEqualTo("alice");
        assertThat(node.path("access_token").asText()).isEqualTo(ToolRecordSanitizer.REDACTED);
    }

    @Test
    void redactsStructuredCredentialsInsidePlainText() {
        var input = """
                Authorization: Bearer abc.SECRETa.def
                call with token=SECRETb&page=2 and "api_key": "SECRETc"
                jwt eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJTRUNSRVQifQ.SECRETdSignature123
                openai sk-SECRETe0123456789abcdef github ghp_SECRETf0123456789abcdefghij
                aws AKIAIOSFODNN7SECRETG
                -----BEGIN RSA PRIVATE KEY-----
                MIIEowSECREThAAKCAQEA
                -----END RSA PRIVATE KEY-----
                tokens: 42 and max_tokens=100 stay
                """;

        var output = ToolRecordSanitizer.sanitize(input);

        assertThat(output).doesNotContain("SECRET");
        assertThat(output).contains("tokens: 42").contains("max_tokens=100").contains("page=2");
    }

    @Test
    void secretValuesNestedAsTextInsideJsonStringsAreRedacted() {
        var output = ToolRecordSanitizer.sanitize("{\"note\":\"use Bearer xyzSECRET1 then password=SECRET2\"}");

        assertThat(output).doesNotContain("SECRET").contains("note");
    }

    @Test
    void nullAndOrdinaryTextPassThrough() {
        assertThat(ToolRecordSanitizer.sanitize(null)).isNull();
        assertThat(ToolRecordSanitizer.sanitize("共 48 条记录")).isEqualTo("共 48 条记录");
        assertThat(ToolRecordSanitizer.sanitize("[1,2,3]")).isEqualTo("[1,2,3]");
    }
}
