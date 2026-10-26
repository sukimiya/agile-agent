package io.github.sukimiya.agileagent.memory.judgement;

import java.util.List;

/**
 * One structured judgement action from the model (M2b Task 3). Parsed from JSON; validated by
 * {@link JudgementValidator} before any write.
 */
public record JudgementAction(
        String action,
        String itemType,
        String content,
        String assertion,
        List<String> sourceMessageIds,
        List<String> evidenceExcerpts,
        String reason,
        Double importance,
        Double confidence,
        String suggestedValidUntil,
        String targetItemId,
        Long expectedRevision) {

    public enum Kind {
        REMEMBER, UPDATE, MERGE, SUPERSEDE, DEPRIORITIZE, HOLD, IGNORE, CONFLICT
    }
}
