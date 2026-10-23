package io.github.sukimiya.agileagent;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.github.sukimiya.agileagent.memory.InstanceGuard;
import io.github.sukimiya.agileagent.memory.InstanceGuardHealthIndicator;

/** Only the health endpoint is exposed, without details; the instance guard contributes to it. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
    // R2DBC and Flyway use different embedded H2 databases in this context, so memory tables are absent.
    "app.memory.recovery-enabled=false",
    "app.memory.instance-guard-enabled=false"
})
class ActuatorHealthTest {

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", LocalRedisTestServer::port);
        registry.add("spring.data.redis.database", () -> 3);
    }

    @Autowired WebTestClient client;
    @Autowired HealthContributorRegistry registry;
    @MockitoBean InstanceGuard guard;

    @Test
    void healthIsExposedWithoutDetails() {
        client.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").exists()
                .jsonPath("$.components").doesNotExist()
                .jsonPath("$.details").doesNotExist();
    }

    @Test
    void instanceConflictTurnsHealthOutOfServiceWithoutLeakingDetails() {
        when(guard.conflict()).thenReturn(true);
        when(guard.peers()).thenReturn(1L);
        client.get().uri("/actuator/health").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo("OUT_OF_SERVICE")
                .jsonPath("$.components").doesNotExist();
    }

    @Test
    void instanceGuardContributesAndNoOtherEndpointIsExposed() {
        assertThat(registry.getContributor("memoryInstance")).isInstanceOf(InstanceGuardHealthIndicator.class);
        // Components are hidden (show-details/show-components never), so sub-paths are not reachable either.
        client.get().uri("/actuator/health/memoryInstance").exchange().expectStatus().isNotFound();
        for (var path : new String[] {"/actuator/env", "/actuator/metrics", "/actuator/beans", "/actuator/info"}) {
            client.get().uri(path).exchange().expectStatus().isNotFound();
        }
    }
}
