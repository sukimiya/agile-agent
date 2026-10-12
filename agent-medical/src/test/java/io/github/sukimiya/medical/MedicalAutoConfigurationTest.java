package io.github.sukimiya.medical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.r2dbc.spi.ConnectionFactory;

class MedicalAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MedicalAutoConfiguration.class));

    @Test
    void backsOffWhenNoConnectionFactoryBeanExists() {
        runner.run(context -> assertThat(context).doesNotHaveBean(MedicalAutoConfiguration.class));
    }

    @Test
    void activatesWhenConnectionFactoryBeanExists() {
        runner.withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .run(context -> assertThat(context).hasSingleBean(MedicalAutoConfiguration.class));
    }

    @Test
    void registersItselfViaAutoConfigurationImports() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(
                "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(in).as("AutoConfiguration.imports resource").isNotNull();
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("io.github.sukimiya.medical.MedicalAutoConfiguration");
        }
    }
}
