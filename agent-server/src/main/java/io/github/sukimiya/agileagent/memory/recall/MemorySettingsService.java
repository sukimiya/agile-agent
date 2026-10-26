package io.github.sukimiya.agileagent.memory.recall;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;

import io.github.sukimiya.agileagent.memory.MemoryScope;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Per-user memory switches in {@code user_memory_settings} (D1=A), keyed by {@code (tenantId, subject)}.
 * Cross-conversation recall is off until the user turns it on. Read from the database on every recall and never
 * cached, so a change applies to the very next question; every update increments {@code revision}.
 */
@Service
public class MemorySettingsService {

    /** The caller's settings; {@code revision} 0 means never written (defaults). */
    public record MemorySettings(boolean crossConversationEnabled, long revision) {}

    private static final MemorySettings DEFAULTS = new MemorySettings(false, 0);

    private final DatabaseClient db;

    public MemorySettingsService(DatabaseClient db) {
        this.db = db;
    }

    public Mono<MemorySettings> get(MemoryScope scope) {
        return db.sql("SELECT cross_conversation_enabled, revision FROM user_memory_settings"
                        + " WHERE tenant_id = :tenantId AND subject = :subject")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map(row -> new MemorySettings(Boolean.TRUE.equals(row.get("cross_conversation_enabled",
                        Boolean.class)), ((Number) row.get("revision")).longValue()))
                .one()
                .defaultIfEmpty(DEFAULTS);
    }

    public Mono<MemorySettings> update(MemoryScope scope, boolean crossConversationEnabled) {
        var change = db.sql("UPDATE user_memory_settings SET cross_conversation_enabled = :enabled,"
                        + " revision = revision + 1, updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE tenant_id = :tenantId AND subject = :subject")
                .bind("enabled", crossConversationEnabled).bind("tenantId", scope.tenantId())
                .bind("subject", scope.subject())
                .fetch().rowsUpdated()
                .flatMap(n -> n > 0 ? Mono.just(n) : db.sql("INSERT INTO user_memory_settings (tenant_id, subject,"
                                + " cross_conversation_enabled, revision, updated_at)"
                                + " VALUES (:tenantId, :subject, :enabled, 1, CURRENT_TIMESTAMP(6))")
                        .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                        .bind("enabled", crossConversationEnabled)
                        .fetch().rowsUpdated());
        // A concurrent first write wins the insert; the loser repeats as an update.
        return Mono.defer(() -> change).retryWhen(Retry.max(1).filter(DuplicateKeyException.class::isInstance))
                .then(get(scope));
    }

    public Mono<Boolean> crossConversationEnabled(MemoryScope scope) {
        return get(scope).map(MemorySettings::crossConversationEnabled);
    }
}
