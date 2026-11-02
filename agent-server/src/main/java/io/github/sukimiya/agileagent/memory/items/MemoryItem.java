package io.github.sukimiya.agileagent.memory.items;

import java.time.LocalDateTime;

import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.util.NonBlockingIds;

/**
 * One long-term memory item ({@code memory_items}). Written by judgement (Task 3); Task 1 only stores and loads
 * by owner scope. {@code conversationId} null means user-level (cross-conversation).
 */
public record MemoryItem(String id, MemoryScope scope, String conversationId, String itemType, String content,
        ItemStatus status, Assertion assertion, Double importance, Double confidence, LocalDateTime validUntil,
        LocalDateTime lastRecalledAt, String supersededBy, int rulesVersion, String judgeModel, String reason,
        long revision, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public enum Assertion {
        USER_CONFIRMED,
        USER_STATED,
        INFERRED
    }

    /** A new item ready to insert (revision 1, timestamps left null for the repository to fill). */
    public static MemoryItem newItem(MemoryScope scope, String conversationId, String itemType, String content,
            ItemStatus status, Assertion assertion, Double importance, Double confidence, int rulesVersion,
            String judgeModel, String reason) {
        return new MemoryItem(NonBlockingIds.uuid(), scope, conversationId, itemType, content, status, assertion,
                importance, confidence, null, null, null, rulesVersion, judgeModel, reason, 1, null, null);
    }
}
