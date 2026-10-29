package io.github.sukimiya.agileagent.memory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The fixed, neutral memory evaluation set ({@code memory-eval/cases.jsonl}) and its rule-based answer verdicts.
 */
final class MemoryEvalCases {

    static final String RESOURCE = "/memory-eval/cases.jsonl";

    /** Components implemented in M1. */
    static final Set<String> M1_COMPONENTS = Set.of("persistence", "terminal", "history-injection", "paging",
            "isolation", "tool-provenance", "size-limit");

    /** Components added in M2a (context budget, turn records, basic recall, capability background). */
    static final Set<String> M2A_COMPONENTS = Set.of("context-budget", "summary", "recall", "capability-background");

    /**
     * Added in M2b: memory judgement, user profile, relation lines, per-turn explanation, decay and the
     * consolidation token quota (decay and quota only on the engineering path).
     */
    static final Set<String> M2B_COMPONENTS = Set.of("memory-judgement", "profile", "relations", "explain", "decay",
            "quota");

    /** Implemented through M2b; a component outside this set is not applicable in this phase. */
    static final Set<String> IMPLEMENTED = java.util.stream.Stream.of(M1_COMPONENTS, M2A_COMPONENTS, M2B_COMPONENTS)
            .flatMap(Set::stream).collect(java.util.stream.Collectors.toUnmodifiableSet());

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Filler(int chars, String insert, Double at) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Turn(String text, Filler filler, boolean tool) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Expect(List<String> all, List<String> any, List<String> none) {}

    /** Model window of a case (null: the configured defaults). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Window(Integer contextWindowTokens, Integer maxOutputTokens) {}

    /**
     * One case. Setup fields (M2a): {@code window} model window; {@code crossConversation} turns the owner's
     * cross-conversation recall switch on; {@code recallSource} index of the turn the probe should recall (null:
     * nothing should be recalled); {@code recallReason} the expected recall reason of that turn;
     * {@code probeTool} the probe goes through the inventory capability in the engineering run. M2b:
     * {@code forbiddenMemory} terms no memory item of the case should contain (each such item, or a user-asserted
     * item from a non-user source, is a false memory); {@code poisoning} marks a poisoning case (no user-asserted
     * item may carry poisoned content); {@code deleteMemory} deletes the items containing that term after
     * judgement and runs the background jobs again before the probe; {@code expectSwitchOff} asks the probe again
     * in another new conversation after turning the cross-conversation switch off.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Case(String id, String category, String requires, boolean answer, List<String> components,
            List<Turn> turns, String probe, boolean probeInNewConversation, Expect expect, String note,
            Window window, boolean crossConversation, Integer recallSource, String recallReason,
            boolean probeTool, List<String> forbiddenMemory, boolean poisoning, String deleteMemory,
            Expect expectSwitchOff) {

        List<String> forbidden() {
            return forbiddenMemory == null ? List.of() : forbiddenMemory;
        }
    }

    enum Verdict { CORRECT, PARTIAL, WRONG, ERROR }

    private MemoryEvalCases() {
    }

    static List<Case> load() {
        var mapper = new ObjectMapper();
        var cases = new ArrayList<Case>();
        try (var in = MemoryEvalCases.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing " + RESOURCE);
            }
            var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    cases.add(mapper.readValue(line, Case.class));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
        return List.copyOf(cases);
    }

    /**
     * The user text actually sent for a turn: the turn text, then (if configured) deterministic neutral filler
     * paragraphs up to {@code filler.chars} characters with {@code filler.insert} placed at fraction {@code at}.
     */
    static String text(Turn turn) {
        var filler = turn.filler();
        if (filler == null) {
            return turn.text();
        }
        var body = new StringBuilder();
        for (int i = 1; body.length() < filler.chars(); i++) {
            body.append("第").append(i).append("段：这是一段用于测试长消息的中性填充文本，描述一个虚构仓库的日常盘点流程，"
                    + "不包含需要记住的信息。\n");
        }
        body.setLength(filler.chars());
        if (filler.insert() != null) {
            int at = (int) (filler.chars() * (filler.at() == null ? 0.5 : filler.at()));
            int boundary = body.indexOf("\n", at);
            body.insert(boundary < 0 ? body.length() : boundary + 1, filler.insert() + "\n");
        }
        return turn.text() + "\n" + body;
    }

    /**
     * Rule-based verdict (reviewed by a person when the baseline is recorded): CORRECT when every {@code all}
     * keyword and at least one {@code any} keyword appear and no {@code none} keyword does; PARTIAL when the
     * expected keywords appear together with a {@code none} keyword, or only some expected keywords appear;
     * otherwise WRONG. Matching ignores case, whitespace and thousands separators.
     */
    static Verdict judge(Expect expect, String answer) {
        if (answer == null || answer.isBlank() || expect == null) {
            return Verdict.WRONG;
        }
        var text = normalize(answer);
        var all = expect.all() == null ? List.<String>of() : expect.all();
        var any = expect.any() == null ? List.<String>of() : expect.any();
        var none = expect.none() == null ? List.<String>of() : expect.none();
        long allHits = all.stream().filter(k -> text.contains(normalize(k))).count();
        boolean anyHit = any.stream().anyMatch(k -> text.contains(normalize(k)));
        boolean noneHit = none.stream().anyMatch(k -> text.contains(normalize(k)));
        boolean satisfied = (!all.isEmpty() || !any.isEmpty()) && allHits == all.size() && (any.isEmpty() || anyHit);
        if (satisfied) {
            return noneHit ? Verdict.PARTIAL : Verdict.CORRECT;
        }
        if (!noneHit && (allHits > 0 || anyHit)) {
            return Verdict.PARTIAL;
        }
        return Verdict.WRONG;
    }

    /** Short description of what the verdict looks for. */
    static String describe(Expect expect) {
        if (expect == null) {
            return "";
        }
        var parts = new ArrayList<String>();
        if (expect.all() != null && !expect.all().isEmpty()) {
            parts.add("全部包含 " + expect.all());
        }
        if (expect.any() != null && !expect.any().isEmpty()) {
            parts.add("至少包含其一 " + expect.any());
        }
        if (expect.none() != null && !expect.none().isEmpty()) {
            parts.add("不应包含 " + expect.none());
        }
        return String.join("；", parts);
    }

    static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("(?<=\\d)[,，](?=\\d)", "").replaceAll("\\s+", "");
    }
}
