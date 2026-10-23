package io.github.sukimiya.agileagent.memory.summary;

/**
 * A model-generated summary of the messages {@code [fromSequence, toSequence]} of one conversation (source type
 * {@code MODEL_SUMMARY}). It never replaces the originals, which stay in {@code chat_messages}.
 */
public record Summary(String id, long fromSequence, long toSequence, String fromMessageId, String toMessageId,
        String content, String generatorModel, String promptVersion, int version) {}
