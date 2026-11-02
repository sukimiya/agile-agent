package io.github.sukimiya.agileagent.memory.items;

import org.junit.jupiter.api.Tag;

import io.github.sukimiya.agileagent.memory.MemoryMySqlTestSupport;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

/** Re-runs the user memory API service cases on MySQL 8.4. */
@Tag("mysql-it")
class MemoryItemMySqlIntegrationTest extends MemoryItemServiceTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
