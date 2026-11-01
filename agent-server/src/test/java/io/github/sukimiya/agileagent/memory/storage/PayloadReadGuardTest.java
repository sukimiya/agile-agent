package io.github.sukimiya.agileagent.memory.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guard of user decision 2026-10-30 point 4: every read of {@code chat_messages.payload} and of the
 * {@code chat_tool_events} {@code arguments} / {@code result} goes through {@link MessagePayloadCodec}. Scans the
 * production sources for SQL SELECT statements (string literal chains) that name the plain column without the
 * codec, and for direct {@code row.get("payload")} reads.
 */
class PayloadReadGuardTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Pattern PAYLOAD = Pattern.compile("(?<![\\w])(\\w+\\.)?payload(?![\\w])");
    private static final Pattern TOOL_TEXT = Pattern.compile("(?<![\\w])(\\w+\\.)?(arguments|result)(?![\\w])");
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");

    @Test
    void everyPayloadReadUsesTheCodec() throws IOException {
        var violations = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (var file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                violations.addAll(violations(file.toString(), Files.readString(file, StandardCharsets.UTF_8)));
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void theScannerFindsPlainReads() {
        assertThat(violations("A.java", """
                db.sql("SELECT m.id, m.payload FROM chat_messages m"
                        + " WHERE m.turn_id = :t AND " + Excl.turn("t") + " ORDER BY m.sequence")
                """)).hasSize(1);
        assertThat(violations("B.java", "db.sql(\"SELECT payload FROM chat_messages WHERE id = :id\")")).hasSize(1);
        assertThat(violations("C.java", "db.sql(\"SELECT e.result FROM chat_tool_events e\")")).hasSize(1);
        assertThat(violations("D.java", "var p = row.get(\"payload\", String.class); // chat_messages")).hasSize(1);
        // Writes, the codec fragments and other tables' payload are fine.
        assertThat(violations("E.java", """
                db.sql("INSERT INTO chat_messages (id, payload) SELECT :id, :payload FROM dual");
                db.sql("UPDATE chat_messages SET payload = :payload WHERE id = :id");
                db.sql("SELECT m.id, " + MessagePayloadCodec.select(F, "m") + " FROM chat_messages m");
                db.sql("SELECT id, payload FROM memory_jobs WHERE status = 'PENDING'");
                throw new IllegalArgumentException("JUDGE_MEMORY payload without throughSequence");
                """)).isEmpty();
    }

    static List<String> violations(String name, String source) {
        var out = new ArrayList<String>();
        boolean scoped = source.contains("chat_messages") || source.contains("chat_tool_events");
        if (!scoped) {
            return out;
        }
        if (!name.endsWith("MessagePayloadCodec.java") && !name.endsWith("MemoryJobWorker.java")
                && source.contains("row.get(\"payload\"")) {
            out.add(name + ": row.get(\"payload\") outside MessagePayloadCodec");
        }
        for (var chain : chains(source)) {
            var upper = chain.strip().toUpperCase(Locale.ROOT);
            if (!upper.contains("SELECT") || upper.startsWith("INSERT") || upper.startsWith("UPDATE")
                    || upper.startsWith("DELETE") || chain.contains("memory_jobs")) {
                continue;
            }
            if (PAYLOAD.matcher(chain).find()) {
                out.add(name + ": plain payload read: " + chain);
            }
            if (chain.contains("chat_tool_events") && TOOL_TEXT.matcher(chain).find()) {
                out.add(name + ": plain tool text read: " + chain);
            }
        }
        return out;
    }

    /**
     * String literal chains: consecutive literals joined when only {@code +} expressions (no statement end or
     * block) stand between them, so a SELECT split over lines or around helper calls is one statement.
     */
    static List<String> chains(String source) {
        var out = new ArrayList<String>();
        var m = LITERAL.matcher(source);
        StringBuilder current = null;
        int lastEnd = -1;
        while (m.find()) {
            if (current != null) {
                var between = source.substring(lastEnd, m.start());
                boolean joined = between.indexOf(';') < 0 && between.indexOf('{') < 0 && between.indexOf('}') < 0
                        && between.contains("+");
                if (!joined) {
                    out.add(current.toString());
                    current = null;
                }
            }
            if (current == null) {
                current = new StringBuilder();
            }
            current.append(m.group(1)).append(' ');
            lastEnd = m.end();
        }
        if (current != null) {
            out.add(current.toString());
        }
        return out;
    }
}
