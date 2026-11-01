package io.github.sukimiya.agileagent.memory.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

/**
 * V17 (M2b Task 9): compressed-payload columns, nullable plain columns and the tenant-scoped shared blob table, on
 * H2 ({@code MODE=MySQL}); {@link StorageSchemaMySqlIntegrationTest} runs the same on MySQL 8.4.
 */
class StorageSchemaTest {

    protected MemoryTestFixture fixture;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        fixture = newFixture();
    }

    @Test
    void v17AddsCompressedColumnsAndSharedBlobs() {
        var applied = java.util.Arrays.stream(Flyway.configure().dataSource(fixture.dataSource()).load().info()
                        .applied())
                .filter(m -> m.getState().isApplied() && !m.getState().isFailed())
                .map(m -> m.getVersion().getVersion()).toList();
        assertThat(applied).contains("17");
        var jdbc = fixture.jdbc();
        var blob = MessagePayloadCodec.encode("{\"content\":\"hello\"}", 3);

        jdbc.update("INSERT INTO chat_messages (id, tenant_id, subject, conversation_id, turn_id, sequence, role,"
                + " source_type, payload, payload_codec, payload_tier, payload_blob, payload_sha256, created_at,"
                + " updated_at) VALUES ('m1', 't', 'alice', 'c1', 'turn-1', 1, 'user', 'USER_INPUT', NULL, 'zstd',"
                + " 'WARM', ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))", blob.data(), blob.sha256());
        assertThat(jdbc.queryForObject("SELECT payload_blob FROM chat_messages WHERE id = 'm1'", byte[].class))
                .isEqualTo(blob.data());

        jdbc.update("INSERT INTO chat_tool_events (id, tenant_id, subject, conversation_id, turn_id, call_id,"
                + " capability, tool_name, status, source_type, arguments, result, arguments_codec, arguments_tier,"
                + " arguments_blob, arguments_sha256, result_codec, result_tier, result_blob, result_sha256,"
                + " started_at, updated_at) VALUES ('e1', 't', 'alice', 'c1', 'turn-1', 'call-1', 'cap', 'tool',"
                + " 'SUCCEEDED', 'TOOL_RESULT', NULL, NULL, 'zstd', 'COLD', ?, ?, 'ref', NULL, NULL, ?,"
                + " CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))", blob.data(), blob.sha256(), blob.sha256());

        jdbc.update("INSERT INTO message_blobs (tenant_id, content_hash, codec, tier, data, plain_bytes, newest_ref_at,"
                + " created_at, updated_at) VALUES ('t', ?, 'zstd', 'WARM', ?, 19, CURRENT_TIMESTAMP(6),"
                + " CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))", blob.sha256(), blob.data());
        jdbc.update("INSERT INTO message_blobs (tenant_id, content_hash, codec, tier, data, plain_bytes, newest_ref_at,"
                + " created_at, updated_at) VALUES ('t2', ?, 'zstd', 'WARM', ?, 19, CURRENT_TIMESTAMP(6),"
                + " CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))", blob.sha256(), blob.data());
        assertThatThrownBy(() -> jdbc.update("INSERT INTO message_blobs (tenant_id, content_hash, codec, tier, data,"
                + " plain_bytes, newest_ref_at, created_at, updated_at) VALUES ('t', ?, 'zstd', 'WARM', ?, 19,"
                + " CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))", blob.sha256(), blob.data()))
                .as("one shared blob per tenant and hash").isInstanceOf(DataIntegrityViolationException.class);
    }
}
