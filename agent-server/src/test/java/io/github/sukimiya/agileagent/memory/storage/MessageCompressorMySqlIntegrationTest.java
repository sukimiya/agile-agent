package io.github.sukimiya.agileagent.memory.storage;

import org.junit.jupiter.api.Tag;

import io.github.sukimiya.agileagent.memory.MemoryMySqlTestSupport;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

/** {@link MessageCompressorTest} on real MySQL 8.4. */
@Tag("mysql-it")
class MessageCompressorMySqlIntegrationTest extends MessageCompressorTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
