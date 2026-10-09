package com.jowo.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "deepseek.base-url=https://api.deepseek.com",
    "deepseek.api-key=sk-test",
    "deepseek.model=deepseek-chat"
})
class DeepSeekPropertiesTest {
    @Autowired DeepSeekProperties props;

    @Test
    void bindsAllFields() {
        assertThat(props.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(props.apiKey()).isEqualTo("sk-test");
        assertThat(props.model()).isEqualTo("deepseek-chat");
    }
}
