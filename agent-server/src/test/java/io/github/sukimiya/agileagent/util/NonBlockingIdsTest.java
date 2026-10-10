package io.github.sukimiya.agileagent.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class NonBlockingIdsTest {

    @Test
    void uuidProducesCanonicalVersionFourValuesWithDistinctIds() {
        List<String> ids = IntStream.range(0, 4_096)
                .<String>mapToObj(ignored -> NonBlockingIds.uuid())
                .toList();

        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids).allSatisfy(id -> {
            assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
            assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        });
    }

    @Test
    void shortUuidPreservesEightHexCharacterTraceFormat() {
        assertThat(NonBlockingIds.shortUuid()).matches("[0-9a-f]{8}");
    }
}
