package io.github.sukimiya.agileagent.memory.recall;

import java.time.LocalDateTime;
import java.util.List;

import io.github.sukimiya.agileagent.memory.SourceType;

/**
 * One recalled turn, after merging all hits of the turn (highest score wins) and expanding it to its original
 * text with the owner re-checked. {@code content} is the original turn ("用户：…\n助手：…"); {@code excerpt} is the
 * matching chunk of a long turn (null for a record-only hit), used when the whole turn does not fit.
 * {@code reason}: {@code ENTITY_EXACT}, {@code RECORD_FULLTEXT}, {@code CHUNK_FULLTEXT} or {@code RECENT_GAP}.
 * M2b Task 5 consumes {@code reason} and {@code score} for recall explanations.
 */
public record RecallCandidate(String turnId, String chunkId, String conversationId, long fromSequence,
        long toSequence, List<SourceType> sources, String reason, double score, boolean crossConversation,
        LocalDateTime finishedAt, String content, String excerpt) {

    public RecallCandidate {
        sources = List.copyOf(sources);
    }
}
