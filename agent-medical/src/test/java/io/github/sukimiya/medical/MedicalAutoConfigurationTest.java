package io.github.sukimiya.medical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.sukimiya.medical.hospital.HospitalCapabilityProvider;
import io.github.sukimiya.medical.hospital.HospitalPackageRepository;
import io.github.sukimiya.medical.transport.TransportExecutor;
import io.r2dbc.spi.ConnectionFactory;

class MedicalAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MedicalAutoConfiguration.class));

    @Test
    void backsOffWhenNoConnectionFactoryBeanExists() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(MedicalAutoConfiguration.class);
            assertThat(context).doesNotHaveBean(HospitalCapabilityProvider.class);
        });
    }

    @Test
    void activatesWhenConnectionFactoryBeanExists() {
        // 注：Task 7 之后配置类有 7 个 @Bean，激活的上下文还需 DatabaseClient 与 WebClient.Builder
        // 才能启动（Task 1 的空壳版本只需 ConnectionFactory）。
        runner.withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(DatabaseClient.class, () -> mock(DatabaseClient.class))
                .withBean(WebClient.Builder.class, WebClient::builder)
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

    @Test
    void wiresTheFullHospitalCapabilityStack() {
        runner.withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(DatabaseClient.class, () -> mock(DatabaseClient.class))
                .withBean(WebClient.Builder.class, WebClient::builder)
                .run(context -> {
                    assertThat(context).hasSingleBean(HospitalCapabilityProvider.class);
                    assertThat(context).hasSingleBean(HospitalPackageRepository.class);
                    assertThat(context.getBeansOfType(TransportExecutor.class)).hasSize(2);
                    assertThat(context.getBean(HospitalCapabilityProvider.class).code())
                            .isEqualTo("HOSPITAL");
                });
    }

    @Test
    void exposesNoClockBeanToTheHostApplication() {
        runner.withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(DatabaseClient.class, () -> mock(DatabaseClient.class))
                .withBean(WebClient.Builder.class, WebClient::builder)
                .run(context -> assertThat(context).doesNotHaveBean(java.time.Clock.class));
    }
}
