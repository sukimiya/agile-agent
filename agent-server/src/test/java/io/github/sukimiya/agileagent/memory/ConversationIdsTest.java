package io.github.sukimiya.agileagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.sukimiya.agileagent.util.NonBlockingIds;

class ConversationIdsTest {

    private static final String VALID = "3f2c8a1e-9b4d-4c7a-8e21-5d6f7a8b9c0d";

    @Test
    void acceptsLowercaseCanonicalUuidUnchanged() {
        assertThat(ConversationIds.validate(VALID)).isSameAs(VALID);
        assertThat(ConversationIds.isValid(VALID)).isTrue();
    }

    @Test
    void serverGeneratedIdsFollowTheSameRule() {
        for (int i = 0; i < 100; i++) {
            var id = NonBlockingIds.uuid();
            assertThat(ConversationIds.validate(id)).isEqualTo(id);
        }
    }

    @Test
    void uppercaseVariantRejected() {
        assertRejected(VALID.toUpperCase());
        assertRejected("3F2c8a1e-9b4d-4c7a-8e21-5d6f7a8b9c0d");
    }

    @Test
    void dotDotIdRejected() {
        assertRejected("..");
        assertRejected("a..b");
        assertRejected("../" + VALID);
        assertRejected(VALID + "/..");
    }

    @Test
    void slashAndUnderscoreIdsRejected() {
        assertRejected("a/b");
        assertRejected("a\\b");
        assertRejected("a_b");
        assertRejected(VALID.replace('-', '_'));
        assertRejected(VALID.substring(0, 8) + "/" + VALID.substring(9));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "conv-1", " " + VALID, VALID + " ", VALID + "\n", "{" + VALID + "}",
            "3f2c8a1e9b4d4c7a8e215d6f7a8b9c0d", "3f2c8a1e-9b4d-4c7a-8e21-5d6f7a8b9c0", VALID + "0",
            "urn:uuid:3f2c8a1e-9b4d-4c7a-8e21-5d6f7a8b9c0d", "3f2c8a1e-9b4d-4c7a-8e21-5d6f7a8b9c0g",
            "3f2c8a1e-9b4d-4c7a-8e21-5d6f7a8b9c0\u0661"})
    void nonCanonicalValuesRejectedWithoutRewriting(String id) {
        assertRejected(id);
    }

    private static void assertRejected(String id) {
        assertThat(ConversationIds.isValid(id)).isFalse();
        assertThatThrownBy(() -> ConversationIds.validate(id))
                .isInstanceOfSatisfying(MemoryException.class,
                        e -> assertThat(e.code()).isEqualTo(MemoryException.ErrorCode.INVALID_CONVERSATION_ID));
    }
}
