package io.github.sukimiya.agileagent.memory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Re-runs every {@link MemorySchemaTest} assertion against a real MySQL 8.4 (Flyway, identity comparison and
 * unique constraints), plus MySQL-only collation checks. Opt-in: {@code mvn -Pmemory-mysql-it ...}.
 */
@Tag("mysql-it")
class MemoryMySqlIntegrationTest extends MemorySchemaTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }

    @Test
    void identityColumnsUseBinaryCollation() {
        var nonBinary = fixture.jdbc().queryForList("""
                SELECT CONCAT(table_name, '.', column_name) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name IN ('sessions', 'chat_turns', 'chat_messages', 'chat_tool_events',
                                     'memory_jobs', 'service_instances')
                  AND column_name IN ('id', 'tenant_id', 'user_id', 'subject', 'conversation_id', 'turn_id',
                                      'request_id', 'active_conversation_id', 'call_id', 'instance_id', 'namespace')
                  AND (collation_name IS NULL OR collation_name <> 'utf8mb4_bin')
                """, String.class);

        assertThat(nonBinary).isEmpty();
    }

    @Test
    void longTextColumnsAreMediumText() {
        var types = fixture.jdbc().queryForList("""
                SELECT CONCAT(table_name, '.', column_name, ':', data_type) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND ((table_name = 'chat_messages' AND column_name = 'payload')
                    OR (table_name = 'chat_tool_events' AND column_name IN ('arguments', 'result')))
                """, String.class);

        assertThat(types).containsExactlyInAnyOrder("chat_messages.payload:mediumtext",
                "chat_tool_events.arguments:mediumtext", "chat_tool_events.result:mediumtext");
    }
}
