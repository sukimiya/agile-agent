package io.github.sukimiya.agileagent.memory;

import org.junit.jupiter.api.Tag;

/** The eval framework and M1 component checks on real MySQL 8.4. */
@Tag("mysql-it")
class MemoryEvalBaselineMySqlIntegrationTest extends MemoryEvalBaselineTest {

    @Override
    protected MemoryTestStack newStack() {
        return MemoryTestStack.of(MemoryMySqlTestSupport.freshDatabase());
    }

    @Override
    protected String storeLabel() {
        return "mysql";
    }
}
