package io.github.sukimiya.agileagent.memory.judgement;

import java.time.LocalDateTime;
import java.util.List;

import io.github.sukimiya.agileagent.memory.SourceType;
import io.github.sukimiya.agileagent.memory.items.ItemStatus;
import io.github.sukimiya.agileagent.memory.items.MemoryItem;
import io.github.sukimiya.agileagent.memory.items.RelationSpec;

/**
 * A {@link JudgementAction} that passed {@link JudgementValidator}; ready to apply. {@code relations} are the
 * relations that passed their own checks, {@code null} when the action carried no {@code relations} field (UPDATE /
 * MERGE then keep the item's set); {@code relationRejects} are the reason codes of the dropped ones.
 */
public record ValidatedAction(
        JudgementAction.Kind kind,
        String itemType,
        String content,
        MemoryItem.Assertion assertion,
        ItemStatus status,
        List<SourceRef> sources,
        String reason,
        Double importance,
        Double confidence,
        LocalDateTime validUntil,
        String targetItemId,
        Long expectedRevision,
        List<RelationSpec> relations,
        List<String> relationRejects) {

    public record SourceRef(String messageId, String conversationId, SourceType sourceType, String evidenceExcerpt) {}
}
