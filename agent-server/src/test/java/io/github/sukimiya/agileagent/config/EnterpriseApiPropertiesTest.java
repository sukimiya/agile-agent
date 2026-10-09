package io.github.sukimiya.agileagent.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "enterprise-api.services.MES.base-url=http://mes:8888",
    "enterprise-api.services.CRM.base-url=http://crm:8080"
})
class EnterpriseApiPropertiesTest {
    @Autowired EnterpriseApiProperties props;

    @Test
    void bindsPerServiceConfig() {
        assertThat(props.services()).containsKeys("MES", "CRM");
        assertThat(props.services().get("MES").baseUrl()).isEqualTo("http://mes:8888");
        assertThat(props.services().get("CRM").baseUrl()).isEqualTo("http://crm:8080");
    }
}
