package io.github.sukimiya.agileagent.memory.judgement;

import java.time.LocalDateTime;
import java.util.List;

import io.github.sukimiya.agileagent.memory.SourceType;
import io.github.sukimiya.agileagent.memory.items.ItemStatus;
import io.github.sukimiya.agileagent.memory.items.MemoryItem;

/**
 * A {@link JudgementAction} that passed {@link JudgementValidator}; ready to apply.
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
        Long expectedRevision) {

    public record SourceRef(String messageId, String conversationId, SourceType sourceType, String evidenceExcerpt) {}
}
