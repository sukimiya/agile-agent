package io.github.sukimiya.agileagent.llmconfig;

import java.time.LocalDateTime;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class LlmProviderPresetRepository {

    private final DatabaseClient db;

    public LlmProviderPresetRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Void> insert(LlmProviderPresetRow row) {
        var spec = db.sql("""
                INSERT INTO llm_provider_preset
                    (id, name, base_url, model, balance_path, created_by_subject, created_at, updated_at,
                     context_window_tokens, max_output_tokens)
                VALUES (:id, :name, :baseUrl, :model, :balancePath, :createdBySubject, :createdAt, :updatedAt,
                        :contextWindowTokens, :maxOutputTokens)
                """)
                .bind("id", row.id())
                .bind("name", row.name())
                .bind("baseUrl", row.baseUrl())
                .bind("model", row.model())
                .bind("createdBySubject", row.createdBySubject())
                .bind("createdAt", row.createdAt())
                .bind("updatedAt", row.updatedAt());
        var bound = bindWindow(row.balancePath() == null
                ? spec.bindNull("balancePath", String.class)
                : spec.bind("balancePath", row.balancePath()), row);
        return bound.fetch().rowsUpdated().then();
    }

    public Mono<LlmProviderPresetRow> findById(String id) {
        return db.sql("""
                SELECT id, name, base_url, model, balance_path, created_by_subject, created_at, updated_at,
                    context_window_tokens, max_output_tokens
                FROM llm_provider_preset WHERE id = :id
                """)
                .bind("id", id)
                .map(LlmProviderPresetRepository::mapRow)
                .one();
    }

    public Flux<LlmProviderPresetRow> findAll() {
        return db.sql("""
                SELECT id, name, base_url, model, balance_path, created_by_subject, created_at, updated_at,
                    context_window_tokens, max_output_tokens
                FROM llm_provider_preset ORDER BY name, id
                """)
                .map(LlmProviderPresetRepository::mapRow)
                .all();
    }

    public Mono<Long> update(LlmProviderPresetRow row) {
        var spec = db.sql("""
                UPDATE llm_provider_preset
                SET name = :name, base_url = :baseUrl, model = :model, balance_path = :balancePath,
                    created_by_subject = :createdBySubject, updated_at = :updatedAt,
                    context_window_tokens = :contextWindowTokens, max_output_tokens = :maxOutputTokens
                WHERE id = :id
                """)
                .bind("name", row.name())
                .bind("baseUrl", row.baseUrl())
                .bind("model", row.model())
                .bind("createdBySubject", row.createdBySubject())
                .bind("updatedAt", row.updatedAt())
                .bind("id", row.id());
        var bound = bindWindow(row.balancePath() == null
                ? spec.bindNull("balancePath", String.class)
                : spec.bind("balancePath", row.balancePath()), row);
        return bound.fetch().rowsUpdated();
    }

    public Mono<Long> deleteById(String id) {
        return db.sql("DELETE FROM llm_provider_preset WHERE id = :id")
                .bind("id", id)
                .fetch().rowsUpdated();
    }

    private static DatabaseClient.GenericExecuteSpec bindWindow(DatabaseClient.GenericExecuteSpec spec,
            LlmProviderPresetRow row) {
        spec = row.contextWindowTokens() == null ? spec.bindNull("contextWindowTokens", Integer.class)
                : spec.bind("contextWindowTokens", row.contextWindowTokens());
        return row.maxOutputTokens() == null ? spec.bindNull("maxOutputTokens", Integer.class)
                : spec.bind("maxOutputTokens", row.maxOutputTokens());
    }

    private static LlmProviderPresetRow mapRow(Readable row) {
        return new LlmProviderPresetRow(
                row.get("id", String.class),
                row.get("name", String.class),
                row.get("base_url", String.class),
                row.get("model", String.class),
                row.get("balance_path", String.class),
                row.get("created_by_subject", String.class),
                row.get("created_at", LocalDateTime.class),
                row.get("updated_at", LocalDateTime.class),
                row.get("context_window_tokens", Integer.class),
                row.get("max_output_tokens", Integer.class));
    }

    public record LlmProviderPresetRow(
            String id,
            String name,
            String baseUrl,
            String model,
            String balancePath,
            String createdBySubject,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Integer contextWindowTokens,
            Integer maxOutputTokens) {

        public LlmProviderPresetRow(String id, String name, String baseUrl, String model, String balancePath,
                String createdBySubject, LocalDateTime createdAt, LocalDateTime updatedAt) {
            this(id, name, baseUrl, model, balancePath, createdBySubject, createdAt, updatedAt, null, null);
        }
    }
}
