package io.github.sukimiya.agileagent.memory;

import java.util.regex.Pattern;

import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;

/**
 * The single conversation id rule shared by both chat entries, attachment upload/download and conversation
 * deletion: a lowercase canonical UUID string (36 characters, 8-4-4-4-12 hex digits). Values are accepted
 * exactly as given or rejected; nothing is trimmed, lower-cased or replaced, so two different inputs can never
 * name the same conversation or the same attachment directory (lowercase only also rules out collisions on
 * case-insensitive file systems). Server-generated ids ({@code NonBlockingIds.uuid()}) follow the same rule.
 */
public final class ConversationIds {

    private static final Pattern CANONICAL =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private ConversationIds() {}

    /** Returns {@code id} unchanged, or fails with {@code INVALID_CONVERSATION_ID} (400). */
    public static String validate(String id) {
        if (!isValid(id)) {
            throw new MemoryException(ErrorCode.INVALID_CONVERSATION_ID);
        }
        return id;
    }

    public static boolean isValid(String id) {
        return id != null && id.length() == 36 && CANONICAL.matcher(id).matches();
    }
}
