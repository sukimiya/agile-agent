package io.github.sukimiya.agileagent.memory;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health component {@code memoryInstance}: OUT_OF_SERVICE (HTTP 503 on {@code /actuator/health}) while another
 * live instance shares the deployment namespace, otherwise UP. Details are only shown if the deployment enables
 * {@code management.endpoint.health.show-details}.
 */
@Component("memoryInstance")
public class InstanceGuardHealthIndicator implements HealthIndicator {

    private final InstanceGuard guard;

    public InstanceGuardHealthIndicator(InstanceGuard guard) {
        this.guard = guard;
    }

    @Override
    public Health health() {
        var builder = guard.conflict() ? Health.outOfService().withDetail("code", "INSTANCE_CONFLICT")
                : Health.up();
        return builder.withDetail("instanceConflict", guard.conflict()).withDetail("peers", guard.peers()).build();
    }
}
