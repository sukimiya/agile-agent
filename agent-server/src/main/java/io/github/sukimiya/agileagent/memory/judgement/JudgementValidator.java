package io.github.sukimiya.agileagent.memory.judgement;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.SourceType;
import io.github.sukimiya.agileagent.memory.ToolRecordSanitizer;
import io.github.sukimiya.agileagent.memory.items.ItemStatus;
import io.github.sukimiya.agileagent.memory.items.MemoryItem;
import io.github.sukimiya.agileagent.memory.items.MemoryItemRepository;
import io.github.sukimiya.agileagent.memory.judgement.GlobalRules.RulesVersion;
import io.github.sukimiya.agileagent.memory.judgement.ValidatedAction.SourceRef;
import reactor.core.publisher.Mono;

/**
 * Programmatic checks on LLM judgement actions (M2b Task 3): format, provenance, scope, deletion,
 * assertion vs source type, evidence verbatim, credential scrubbing, revision CAS target, and
 * anti-poisoning heuristics. Failed actions are discarded wholly (no partial apply).
 */
@Component
public class JudgementValidator {

    public static final class RejectedException extends RuntimeException {
        public final String reason;

        public RejectedException(String reason) {
            super(reason);
            this.reason = reason;
        }
    }

    private static final Set<SourceType> NON_USER = Set.of(SourceType.TOOL_RESULT, SourceType.KNOWLEDGE_DOC,
            SourceType.WEB, SourceType.MODEL_SUMMARY, SourceType.ASSISTANT);

    private static final Pattern HYPOTHETICAL = Pattern.compile(
            "(?i)(假如|假设|如果我是|举例|例如|比如|hypothetical|suppose that|what if)");
    private static final Pattern ROLEPLAY = Pattern.compile(
            "(?i)(角色扮演|你现在是|扮演|role\\s*-?play|you are now)");
    private static final Pattern THIRD_PARTY = Pattern.compile(
            "(?i)(他说|她说|他们说|有人说|引用|据报道|according to|someone said)");
    private static final Pattern PASTED = Pattern.compile(
            "(?i)(以下是文章|原文如下|转载|全文|粘贴)");
    private static final Pattern INSTRUCTION = Pattern.compile(
            "(?i)(忽略(以上|之前)?规则|ignore (all )?(previous )?rules|你必须泄露|system prompt|sudo|授予工具权限)");

    private final DatabaseClient db;
    private final MemoryItemRepository items;

    public JudgementValidator(DatabaseClient db, MemoryItemRepository items) {
        this.db = db;
        this.items = items;
    }

    public Mono<ValidatedAction> validate(MemoryScope scope, JudgementAction action, RulesVersion rules) {
        return Mono.defer(() -> {
            try {
                return validateSyncPrelude(scope, action, rules);
            } catch (RejectedException e) {
                return Mono.error(e);
            }
        });
    }

    private Mono<ValidatedAction> validateSyncPrelude(MemoryScope scope, JudgementAction action, RulesVersion rules) {
        if (action == null) {
            return Mono.error(new RejectedException("NULL_ACTION"));
        }
        if (rules == null) {
            return Mono.error(new RejectedException("RULES_REQUIRED"));
        }
        JudgementAction.Kind kind;
        try {
            kind = JudgementAction.Kind.valueOf(nullToEmpty(action.action()).trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return Mono.error(new RejectedException("BAD_ACTION"));
        }
        if (kind == JudgementAction.Kind.IGNORE) {
            return Mono.error(new RejectedException("IGNORE"));
        }
        // F1: EVENT stays at the turn-record layer; do not write long-term items.
        if (kind == JudgementAction.Kind.REMEMBER && "EVENT".equalsIgnoreCase(nullToEmpty(action.itemType()).trim())) {
            return Mono.error(new RejectedException("EVENT_NOT_LONG_TERM"));
        }
        if (INSTRUCTION.matcher(nullToEmpty(action.content())).find()
                || INSTRUCTION.matcher(nullToEmpty(action.reason())).find()) {
            return Mono.error(new RejectedException("INSTRUCTION_IN_MATERIAL"));
        }
        if (kind == JudgementAction.Kind.HOLD) {
            return Mono.just(new ValidatedAction(kind, safeType(action.itemType()), scrub(action.content()),
                    MemoryItem.Assertion.INFERRED, ItemStatus.CANDIDATE, List.of(), scrub(action.reason()),
                    action.importance(), action.confidence(), null, null, null));
        }
        if (kind == JudgementAction.Kind.DEPRIORITIZE || kind == JudgementAction.Kind.CONFLICT
                || kind == JudgementAction.Kind.UPDATE || kind == JudgementAction.Kind.MERGE
                || kind == JudgementAction.Kind.SUPERSEDE) {
            if (action.targetItemId() == null || action.targetItemId().isBlank()) {
                return Mono.error(new RejectedException("TARGET_REQUIRED"));
            }
            if (action.expectedRevision() == null) {
                return Mono.error(new RejectedException("REVISION_REQUIRED"));
            }
        }
        var ids = action.sourceMessageIds() == null ? List.<String>of() : action.sourceMessageIds().stream()
                .filter(s -> s != null && !s.isBlank()).distinct().toList();
        var excerpts = action.evidenceExcerpts() == null ? List.<String>of() : action.evidenceExcerpts();
        if (kind == JudgementAction.Kind.REMEMBER || kind == JudgementAction.Kind.UPDATE
                || kind == JudgementAction.Kind.MERGE || kind == JudgementAction.Kind.SUPERSEDE) {
            if (ids.isEmpty()) {
                return Mono.error(new RejectedException("SOURCES_REQUIRED"));
            }
            if (nullToEmpty(action.content()).isBlank()) {
                return Mono.error(new RejectedException("CONTENT_REQUIRED"));
            }
        }
        MemoryItem.Assertion assertion;
        try {
            assertion = MemoryItem.Assertion.valueOf(nullToEmpty(action.assertion()).trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return Mono.error(new RejectedException("BAD_ASSERTION"));
        }
        String content = scrub(action.content());
        if (WriteGovernance.containsBlockedPii(action.content()) || WriteGovernance.containsBlockedPii(content)) {
            return Mono.error(new RejectedException("PII_OR_CREDENTIAL"));
        }
        // Poisoning heuristics: these must not become USER_STATED / USER_CONFIRMED facts.
        boolean poisonShape = HYPOTHETICAL.matcher(nullToEmpty(action.content())).find()
                || ROLEPLAY.matcher(nullToEmpty(action.content())).find()
                || THIRD_PARTY.matcher(nullToEmpty(action.content())).find()
                || PASTED.matcher(nullToEmpty(action.content())).find();
        if (poisonShape && assertion != MemoryItem.Assertion.INFERRED) {
            return Mono.error(new RejectedException("POISON_ASSERTION"));
        }

        ItemStatus status = switch (kind) {
            case CONFLICT -> ItemStatus.CONFLICT;
            case HOLD -> ItemStatus.CANDIDATE;
            case DEPRIORITIZE -> ItemStatus.INACTIVE;
            default -> (assertion == MemoryItem.Assertion.INFERRED && (action.confidence() == null
                    || action.confidence() < 0.5) || kind == JudgementAction.Kind.HOLD)
                            ? ItemStatus.CANDIDATE
                            : ItemStatus.ACTIVE;
        };
        // Ambiguous → CANDIDATE
        if (action.confidence() != null && action.confidence() < 0.4 && kind == JudgementAction.Kind.REMEMBER) {
            status = ItemStatus.CANDIDATE;
        }

        LocalDateTime validUntil = parseValidUntil(action.suggestedValidUntil());
        JudgementAction.Kind finalKind = kind;
        MemoryItem.Assertion finalAssertion = assertion;
        ItemStatus finalStatus = status;

        return loadSources(scope, ids, excerpts).flatMap(sources -> {
            if (sources.size() != ids.size()) {
                return Mono.error(new RejectedException("SOURCE_MISSING_OR_FOREIGN"));
            }
            for (var src : sources) {
                if (NON_USER.contains(src.sourceType())
                        && (finalAssertion == MemoryItem.Assertion.USER_STATED
                                || finalAssertion == MemoryItem.Assertion.USER_CONFIRMED)) {
                    return Mono.error(new RejectedException("NON_USER_CLAIMED_USER_SAID"));
                }
            }
            boolean allUser = sources.stream().allMatch(s -> s.sourceType() == SourceType.USER_INPUT);
            if ((finalAssertion == MemoryItem.Assertion.USER_STATED
                    || finalAssertion == MemoryItem.Assertion.USER_CONFIRMED) && !allUser) {
                return Mono.error(new RejectedException("USER_ASSERTION_REQUIRES_USER_INPUT"));
            }
            Mono<Void> targetCheck = Mono.empty();
            if (action.targetItemId() != null && !action.targetItemId().isBlank()) {
                targetCheck = items.findById(scope, action.targetItemId()).flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return Mono.error(new RejectedException("TARGET_NOT_FOUND"));
                    }
                    if (action.expectedRevision() != null
                            && opt.get().revision() != action.expectedRevision()) {
                        return Mono.error(new RejectedException("STALE_REVISION"));
                    }
                    return Mono.empty();
                });
            }
            Double confidence = WriteGovernance.capConfidence(finalAssertion, action.confidence());
            ValidatedAction validated = new ValidatedAction(finalKind, safeType(action.itemType()), content,
                    finalAssertion, finalStatus, sources, scrub(action.reason()), action.importance(),
                    confidence, validUntil, action.targetItemId(), action.expectedRevision());
            return targetCheck.thenReturn(validated);
        });
    }

    private Mono<List<SourceRef>> loadSources(MemoryScope scope, List<String> ids, List<String> excerpts) {
        if (ids.isEmpty()) {
            return Mono.just(List.of());
        }
        // Load each message; must belong to scope and live conversation.
        return reactor.core.publisher.Flux.fromIterable(ids)
                .index()
                .concatMap(tuple -> {
                    long idx = tuple.getT1();
                    String id = tuple.getT2();
                    String wantExcerpt = idx < excerpts.size() ? excerpts.get((int) idx) : null;
                    return db.sql("SELECT m.id, m.conversation_id, m.source_type, m.payload, se.is_valid AS session_valid,"
                                    + " m.is_valid AS message_valid"
                                    + " FROM chat_messages m JOIN sessions se ON se.id = m.conversation_id"
                                    + " AND se.tenant_id = m.tenant_id AND se.user_id = m.subject"
                                    + " WHERE m.id = :id AND m.tenant_id = :tenantId AND m.subject = :subject")
                            .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                            .map((row, meta) -> {
                                if (((Number) row.get("session_valid")).intValue() != 1
                                        || ((Number) row.get("message_valid")).intValue() != 1) {
                                    throw new RejectedException("DELETED_SOURCE");
                                }
                                SourceType type = SourceType.valueOf(row.get("source_type", String.class));
                                String payload = row.get("payload", String.class);
                                String body = payloadBody(payload);
                                String excerpt = wantExcerpt == null ? "" : wantExcerpt;
                                if (!excerpt.isBlank() && !body.contains(excerpt)) {
                                    throw new RejectedException("EVIDENCE_NOT_IN_SOURCE");
                                }
                                String safeExcerpt = scrub(excerpt.isBlank()
                                        ? body.substring(0, Math.min(body.length(), 200))
                                        : excerpt);
                                return new SourceRef(row.get("id", String.class),
                                        row.get("conversation_id", String.class), type, safeExcerpt);
                            })
                            .one()
                            .switchIfEmpty(Mono.error(new RejectedException("SOURCE_MISSING_OR_FOREIGN")));
                })
                .collectList()
                .onErrorMap(e -> e instanceof RejectedException ? e
                        : e.getCause() instanceof RejectedException r ? r : e);
    }

    private static String payloadBody(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return "";
        }
        // StoredMessage payload is LlmClient.Message JSON with "content".
        var marker = "\"content\":";
        int i = payloadJson.indexOf(marker);
        if (i < 0) {
            return payloadJson;
        }
        int start = payloadJson.indexOf('"', i + marker.length());
        if (start < 0) {
            return payloadJson;
        }
        StringBuilder sb = new StringBuilder();
        for (int p = start + 1; p < payloadJson.length(); p++) {
            char c = payloadJson.charAt(p);
            if (c == '\\' && p + 1 < payloadJson.length()) {
                sb.append(payloadJson.charAt(++p));
                continue;
            }
            if (c == '"') {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String scrub(String text) {
        return text == null ? null : ToolRecordSanitizer.sanitize(text);
    }

    private static String safeType(String itemType) {
        return itemType == null || itemType.isBlank() ? "FACT" : itemType.trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static LocalDateTime parseValidUntil(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(raw.trim() + "T00:00:00");
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }
}
