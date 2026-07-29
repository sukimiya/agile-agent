package com.jowo.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "smartlink.base-url=http://smartlink-test:9000"
})
class SmartLinkPropertiesTest {
    @Autowired SmartLinkProperties props;

    @Test
    void bindsBaseUrl() {
        assertThat(props.baseUrl()).isEqualTo("http://smartlink-test:9000");
    }
}
