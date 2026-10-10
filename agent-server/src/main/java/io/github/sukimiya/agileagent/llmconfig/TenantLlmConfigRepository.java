package io.github.sukimiya.agileagent.llmconfig;

import java.sql.SQLException;
import java.time.LocalDateTime;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import io.r2dbc.spi.Readable;
import io.r2dbc.spi.R2dbcException;
import reactor.core.publisher.Mono;

@Repository
public class TenantLlmConfigRepository {

    private final DatabaseClient db;

    public TenantLlmConfigRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Void> upsert(TenantLlmConfigRow row) {
        return findByTenantId(row.tenantId()).hasElement().flatMap(exists -> exists
                ? updateAndVerify(row)
                : insert(row).onErrorResume(insertError -> recoverDuplicateInsert(row, insertError)));
    }

    private Mono<Void> recoverDuplicateInsert(TenantLlmConfigRow row, Throwable insertError) {
        if (!isDuplicateKey(insertError)) {
            return Mono.error(insertError);
        }
        return findByTenantId(row.tenantId()).hasElement().flatMap(exists -> exists
                ? updateAndVerify(row)
                : Mono.error(insertError));
    }

    private Mono<Void> updateAndVerify(TenantLlmConfigRow row) {
        return updateByTenantId(row).then(verifyTenantExists(row.tenantId()));
    }

    private Mono<Void> verifyTenantExists(String tenantId) {
        return findByTenantId(tenantId).hasElement().flatMap(exists -> exists
                ? Mono.empty()
                : Mono.error(new IllegalStateException("Tenant LLM config disappeared during upsert")));
    }

    static boolean isDuplicateKey(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
            if (cause instanceof R2dbcException r2dbcException
                    && ("23505".equals(r2dbcException.getSqlState()) || r2dbcException.getErrorCode() == 1062)) {
                return true;
            }
            if (cause instanceof SQLException sqlException
                    && ("23505".equals(sqlException.getSQLState()) || sqlException.getErrorCode() == 1062)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Long> updateByTenantId(TenantLlmConfigRow row) {
        return db.sql("""
                UPDATE tenant_llm_config
                SET preset_id = :presetId, api_key_encrypted = :apiKeyEncrypted, updated_at = :updatedAt
                WHERE tenant_id = :tenantId
                """)
                .bind("presetId", row.presetId())
                .bind("apiKeyEncrypted", row.apiKeyEncrypted())
                .bind("updatedAt", row.updatedAt())
                .bind("tenantId", row.tenantId())
                .fetch().rowsUpdated();
    }

    private Mono<Void> insert(TenantLlmConfigRow row) {
        return db.sql("""
                INSERT INTO tenant_llm_config
                    (id, tenant_id, preset_id, api_key_encrypted, created_at, updated_at)
                VALUES (:id, :tenantId, :presetId, :apiKeyEncrypted, :createdAt, :updatedAt)
                """)
                .bind("id", row.id())
                .bind("tenantId", row.tenantId())
                .bind("presetId", row.presetId())
                .bind("apiKeyEncrypted", row.apiKeyEncrypted())
                .bind("createdAt", row.createdAt())
                .bind("updatedAt", row.updatedAt())
                .fetch().rowsUpdated().then();
    }

    public Mono<TenantLlmConfigRow> findByTenantId(String tenantId) {
        return db.sql("""
                SELECT id, tenant_id, preset_id, api_key_encrypted, created_at, updated_at
                FROM tenant_llm_config WHERE tenant_id = :tenantId
                """)
                .bind("tenantId", tenantId)
                .map(TenantLlmConfigRepository::mapRow)
                .one();
    }

    public Mono<Long> deleteByTenantId(String tenantId) {
        return db.sql("DELETE FROM tenant_llm_config WHERE tenant_id = :tenantId")
                .bind("tenantId", tenantId)
                .fetch().rowsUpdated();
    }

    private static TenantLlmConfigRow mapRow(Readable row) {
        return new TenantLlmConfigRow(
                row.get("id", String.class),
                row.get("tenant_id", String.class),
                row.get("preset_id", String.class),
                row.get("api_key_encrypted", String.class),
                row.get("created_at", LocalDateTime.class),
                row.get("updated_at", LocalDateTime.class));
    }

    public record TenantLlmConfigRow(
            String id,
            String tenantId,
            String presetId,
            String apiKeyEncrypted,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
