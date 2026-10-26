package io.github.sukimiya.agileagent.memory;

import org.junit.jupiter.api.Tag;

/** {@link M2bSchemaTest} on real MySQL 8.4. */
@Tag("mysql-it")
class M2bSchemaMySqlIntegrationTest extends M2bSchemaTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
