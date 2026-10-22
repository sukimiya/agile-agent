package io.github.sukimiya.agileagent.memory;

import org.junit.jupiter.api.Tag;

/**
 * Re-runs every {@link TurnStreamingIntegrationTest} case (both chat entry points, replay, 409, disconnect,
 * failure events, paging) against a real MySQL 8.4. Opt-in: {@code mvn -Pmemory-mysql-it ...}.
 */
@Tag("mysql-it")
class TurnStreamingMySqlIntegrationTest extends TurnStreamingIntegrationTest {

    @Override
    protected MemoryTestStack newStack() {
        return MemoryTestStack.of(MemoryMySqlTestSupport.freshDatabase());
    }
}
