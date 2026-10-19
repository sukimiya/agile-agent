package io.github.sukimiya.medical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.sukimiya.medical.hospital.HospitalCapabilityProvider;
import io.github.sukimiya.medical.hospital.HospitalPackageRepository;
import io.github.sukimiya.medical.hospital.HospitalPackageValidator;
import io.github.sukimiya.medical.lifecycle.HospitalPackageLifecycleService;
import io.github.sukimiya.medical.rules.ConversationGuard;
import io.github.sukimiya.medical.rules.EscalationPoller;
import io.github.sukimiya.medical.rules.EscalationService;
import io.github.sukimiya.medical.rules.MedicalRuleLifecycleService;
import io.github.sukimiya.medical.rules.MedicalRuleRepository;
import io.github.sukimiya.medical.rules.MedicalRuleValidator;
import io.github.sukimiya.medical.rules.MedicalTaskEventRepository;
import io.github.sukimiya.medical.rules.RuleEngine;
import io.github.sukimiya.medical.rules.SafetyPatternStore;
import io.github.sukimiya.medical.transport.TransportExecutor;
import io.github.sukimiya.medical.validation.EndpointScreeningPolicy;
import io.github.sukimiya.medical.validation.GatePipeline;
import io.github.sukimiya.medical.validation.TextSafetyScreen;
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
                    assertThat(context).hasSingleBean(HospitalPackageValidator.class);
                    assertThat(context).hasSingleBean(EndpointScreeningPolicy.class);
                    assertThat(context).hasSingleBean(GatePipeline.class);
                    assertThat(context).hasSingleBean(HospitalPackageLifecycleService.class);
                });
    }

    @Test
    void exposesNoClockBeanToTheHostApplication() {
        runner.withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(DatabaseClient.class, () -> mock(DatabaseClient.class))
                .withBean(WebClient.Builder.class, WebClient::builder)
                .run(context -> assertThat(context).doesNotHaveBean(java.time.Clock.class));
    }

    @Test
    void bindsEndpointAllowlistFromProperties() {
        runner.withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(DatabaseClient.class, () -> mock(DatabaseClient.class))
                .withBean(WebClient.Builder.class, WebClient::builder)
                .withPropertyValues("medical.hospital.endpoint-allowlist=127.0.0.1,localhost")
                .run(context -> {
                    assertThat(context.getBean(MedicalHospitalProperties.class).getEndpointAllowlist())
                            .containsExactly("127.0.0.1", "localhost");
                    assertThat(context).hasSingleBean(EndpointScreeningPolicy.class);
                });
    }

    @Test
    void wiresTheRuleEngineStack() {
        runner.withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(DatabaseClient.class, () -> mock(DatabaseClient.class))
                .withBean(WebClient.Builder.class, WebClient::builder)
                .run(context -> {
                    assertThat(context).hasSingleBean(MedicalRuleRepository.class);
                    assertThat(context).hasSingleBean(MedicalTaskEventRepository.class);
                    assertThat(context).hasSingleBean(SafetyPatternStore.class);
                    assertThat(context).hasSingleBean(MedicalRuleValidator.class);
                    assertThat(context).hasSingleBean(MedicalRuleLifecycleService.class);
                    assertThat(context).hasSingleBean(EscalationService.class);
                    assertThat(context).hasSingleBean(ConversationGuard.class);
                    assertThat(context).hasSingleBean(RuleEngine.class);
                    assertThat(context).hasSingleBean(EscalationPoller.class);
                    assertThat(context).hasSingleBean(TextSafetyScreen.class);
                });
    }

    @Test
    void escalationPollerIsSwitchable() {
        runner.withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(DatabaseClient.class, () -> mock(DatabaseClient.class))
                .withBean(WebClient.Builder.class, WebClient::builder)
                .withPropertyValues("medical.rules.escalation-poller-enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(EscalationPoller.class);
                    assertThat(context).hasSingleBean(EscalationService.class);
                });
    }

    @Test
    void bindsRulesProperties() {
        runner.withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(DatabaseClient.class, () -> mock(DatabaseClient.class))
                .withBean(WebClient.Builder.class, WebClient::builder)
                .withPropertyValues(
                        "medical.rules.escalation-poll-interval=5s",
                        "medical.rules.stale-claim-seconds=60",
                        "medical.rules.stale-claim-release-enabled=false")
                .run(context -> {
                    var properties = context.getBean(MedicalRulesProperties.class);
                    assertThat(properties.getEscalationPollInterval()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(properties.getStaleClaimSeconds()).isEqualTo(60L);
                    assertThat(properties.isStaleClaimReleaseEnabled()).isFalse();
                    assertThat(properties.isEscalationPollerEnabled()).isTrue();
                });
    }

    @Test
    void rulesPropertiesDefaultToSpecValues() {
        runner.withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(DatabaseClient.class, () -> mock(DatabaseClient.class))
                .withBean(WebClient.Builder.class, WebClient::builder)
                .run(context -> {
                    var properties = context.getBean(MedicalRulesProperties.class);
                    assertThat(properties.getEscalationPollInterval()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(properties.isEscalationPollerEnabled()).isTrue();
                    assertThat(properties.getStaleClaimSeconds()).isEqualTo(1800L);
                    assertThat(properties.isStaleClaimReleaseEnabled()).isTrue();
                });
    }
}
