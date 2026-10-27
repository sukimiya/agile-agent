package io.github.sukimiya.agileagent.memory.recall;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.r2dbc.core.DatabaseClient;

import io.github.sukimiya.agileagent.memory.MemoryScope;
import reactor.core.publisher.Mono;

/**
 * Turns whose raw text must not be recalled after an in-chat forget (M2b, user decision 2026-10-27): every turn
 * answered by a memory command (forget / restore instruction, numbered pick; marked in
 * {@code chat_turns.memory_command}, permanently), and every turn that contains a source message of an item that
 * is currently FORGOTTEN (dynamic: restoring the item makes the turn recallable again). Applied to all raw recall
 * paths (chunks, turn records, exact codes, entity names, the unindexed recent gap) before ranking.
 */
public final class RecallExclusions {

    /**
     * SQL condition true when turn {@code t} (alias) is excluded: a memory command turn, or a turn holding a
     * source message of a currently FORGOTTEN item of the same owner. Shared by recall and the recent context
     * window ({@code ConversationHistory#completedTurnsBefore}).
     */
    public static String excludedTurn(String t) {
        return "(" + t + ".memory_command IS NOT NULL OR EXISTS (SELECT 1"
                + " FROM chat_messages fm JOIN memory_item_sources fs ON fs.message_id = fm.id"
                + " JOIN memory_items fi ON fi.id = fs.item_id WHERE fm.turn_id = " + t + ".id"
                + " AND fi.status = 'FORGOTTEN' AND fi.tenant_id = " + t + ".tenant_id AND fi.subject = "
                + t + ".subject))";
    }

    private final DatabaseClient db;

    public RecallExclusions(DatabaseClient db) {
        this.db = db;
    }

    /** The excluded turns among {@code turnIds} of the owner. */
    public Mono<Set<String>> excluded(MemoryScope scope, Collection<String> turnIds) {
        if (turnIds.isEmpty()) {
            return Mono.just(Set.of());
        }
        return db.sql("SELECT t.id FROM chat_turns t WHERE t.id IN (:ids) AND t.tenant_id = :tenantId"
                        + " AND t.subject = :subject AND " + excludedTurn("t"))
                .bind("ids", List.copyOf(Set.copyOf(turnIds)))
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map((row, meta) -> row.get("id", String.class))
                .all()
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
