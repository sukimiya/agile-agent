package io.github.sukimiya.agileagent.memory.judgement;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.context.TokenEstimator;
import io.github.sukimiya.agileagent.memory.items.DecayPolicy;
import io.github.sukimiya.agileagent.memory.recall.EntityTerms;
import reactor.core.publisher.Mono;

/**
 * The existing items sent with one turn's memory judgement (user decision 2026-10-30), so that a correction can
 * UPDATE / SUPERSEDE an item instead of only adding a new one.
 * <ul>
 * <li>Scope as recall: user-level items when cross-conversation memory is on, otherwise this conversation's
 * session items. Only valid ACTIVE (not expired), CANDIDATE and CONFLICT items.</li>
 * <li>The union of up to {@code existing-recent-items} newest items of this conversation (with cross on: user-level
 * items with a source message in this conversation), no literal match needed, and items whose content contains
 * one of the turn's codes or keywords, filling up to {@code existing-max-items}. Deduplicated, ranked by the
 * decayed score ({@link DecayPolicy#score}).</li>
 * <li>Fields as in Rules replay: id, revision, itemType, content, assertion, status. Over
 * {@code existing-max-tokens} (estimated) the lowest-ranked items are dropped first; this budget is separate from
 * {@code max-source-tokens}.</li>
 * </ul>
 * Read again before every turn, so items written for an earlier turn of the same job are visible.
 */
final class ExistingItems {

    private static final ObjectMapper JSON = new ObjectMapper();

    private record Row(String id, long revision, String itemType, String content, String assertion, String status,
            LocalDateTime createdAt, boolean here, double score) {}

    private final DatabaseClient db;
    private final MemoryProperties.Judgement props;
    private final DecayPolicy decay;

    ExistingItems(DatabaseClient db, MemoryProperties props) {
        this.db = db;
        this.props = props.getJudgement();
        this.decay = new DecayPolicy(props.getDecay());
    }

    Mono<String> json(MemoryScope scope, String conversationId, boolean cross, String turnText) {
        int max = props.getExistingMaxItems();
        if (max == 0) {
            return Mono.just("[]");
        }
        var sql = "SELECT i.id, i.revision, i.item_type, i.content, i.assertion, i.status, i.importance, i.created_at,"
                + " TIMESTAMPDIFF(SECOND, COALESCE(i.last_recalled_at, i.created_at), CURRENT_TIMESTAMP(6)) AS age_s,"
                + " CASE WHEN EXISTS (SELECT 1 FROM memory_item_sources s WHERE s.item_id = i.id"
                + " AND s.conversation_id = :c AND s.is_valid = 1) THEN 1 ELSE 0 END AS here"
                + " FROM memory_items i WHERE i.tenant_id = :tenantId AND i.subject = :subject AND i.is_valid = 1"
                + " AND i.status IN ('ACTIVE', 'CANDIDATE', 'CONFLICT')"
                + " AND (i.status <> 'ACTIVE' OR i.valid_until IS NULL OR i.valid_until > CURRENT_TIMESTAMP(6))"
                + (cross ? " AND i.conversation_id IS NULL" : " AND i.conversation_id = :c");
        return db.sql(sql).bind("c", conversationId).bind("tenantId", scope.tenantId())
                .bind("subject", scope.subject())
                .map((row, meta) -> {
                    var age = row.get("age_s");
                    var importance = row.get("importance");
                    return new Row(row.get("id", String.class), ((Number) row.get("revision")).longValue(),
                            row.get("item_type", String.class), row.get("content", String.class),
                            row.get("assertion", String.class), row.get("status", String.class),
                            row.get("created_at", LocalDateTime.class), ((Number) row.get("here")).intValue() == 1,
                            decay.score(importance == null ? null : ((Number) importance).doubleValue(),
                                    age == null ? 0 : ((Number) age).longValue()));
                })
                .all()
                .collectList()
                .map(rows -> render(select(rows, turnText, max)));
    }

    private List<Row> select(List<Row> rows, String turnText, int max) {
        var byScore = Comparator.comparingDouble(Row::score).reversed()
                .thenComparing(Row::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Row::id);
        var chosen = new LinkedHashMap<String, Row>();
        rows.stream().filter(Row::here)
                .sorted(Comparator.comparing(Row::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Row::id, Comparator.reverseOrder()))
                .limit(Math.min(props.getExistingRecentItems(), max))
                .forEach(r -> chosen.put(r.id(), r));
        var needles = new ArrayList<String>();
        needles.addAll(EntityTerms.codes(turnText));
        needles.addAll(EntityTerms.terms(turnText));
        var terms = needles.stream().filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim().toLowerCase(Locale.ROOT)).distinct().toList();
        if (!terms.isEmpty()) {
            rows.stream().filter(r -> !chosen.containsKey(r.id()))
                    .filter(r -> {
                        var lower = r.content() == null ? "" : r.content().toLowerCase(Locale.ROOT);
                        return terms.stream().anyMatch(lower::contains);
                    })
                    .sorted(byScore)
                    .limit(Math.max(0, max - chosen.size()))
                    .forEach(r -> chosen.put(r.id(), r));
        }
        var ranked = new ArrayList<>(chosen.values());
        ranked.sort(byScore);
        return ranked;
    }

    private String render(List<Row> ranked) {
        var list = new ArrayList<>(ranked);
        while (true) {
            var arr = JSON.createArrayNode();
            for (var r : list) {
                var o = arr.addObject();
                o.put("id", r.id());
                o.put("revision", r.revision());
                o.put("itemType", r.itemType());
                o.put("content", r.content());
                o.put("assertion", r.assertion());
                o.put("status", r.status());
            }
            var text = arr.toString();
            if (list.isEmpty() || TokenEstimator.estimate(text) <= props.getExistingMaxTokens()) {
                return text;
            }
            list.removeLast();
        }
    }
}
