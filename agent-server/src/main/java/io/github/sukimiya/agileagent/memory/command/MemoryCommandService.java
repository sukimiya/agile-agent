package io.github.sukimiya.agileagent.memory.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;

import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.command.MemoryCommand.Kind;
import io.github.sukimiya.agileagent.memory.items.ItemStatus;
import io.github.sukimiya.agileagent.memory.items.MemoryItem;
import io.github.sukimiya.agileagent.memory.items.MemoryItemRepository;
import io.github.sukimiya.agileagent.memory.items.MemoryItemService;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import reactor.core.publisher.Mono;

/**
 * In-chat forget / restore (M2b T5 follow-up, user decision 2026-10-27), run by the orchestrator before routing.
 * <ul>
 * <li>Trigger: {@link MemoryCommand#parse} on the user's own current input only.</li>
 * <li>Candidates: valid items (ACTIVE to forget, FORGOTTEN to restore) in the same scope as item recall (user-level
 * items with cross-conversation recall on, otherwise the current conversation's), scored by
 * {@link MemoryCommand#score}; at least {@code app.memory.forget.min-score}, best first, at most
 * {@code max-candidates}.</li>
 * <li>One match is applied at once without confirmation; several are listed and kept as a pending pick (one per
 * conversation, {@code pending-ttl}); the next turn of the conversation consumes it: a number or "全部" applies the
 * pick, anything else (or a late reply) cancels it and is answered normally. No match changes nothing.</li>
 * <li>A handled turn is answered without a model call and marked ({@code chat_turns.memory_command}) so memory
 * judgement skips it.</li>
 * </ul>
 */
@Service
public class MemoryCommandService {

    private static final Logger log = LoggerFactory.getLogger(MemoryCommandService.class);

    static final String FORGET_ACTION = "FORGET_IN_CHAT";
    static final String RESTORE_ACTION = "RESTORE_IN_CHAT";

    private final DatabaseClient db;
    private final MemoryItemService itemService;
    private final MemoryItemRepository items;
    private final MemorySettingsService settings;
    private final MemoryProperties.Forget props;

    public MemoryCommandService(DatabaseClient db, MemoryItemService itemService, MemorySettingsService settings,
            MemoryProperties props) {
        this.db = db;
        this.itemService = itemService;
        this.items = new MemoryItemRepository(db);
        this.settings = settings;
        this.props = props.getForget();
    }

    private record Pending(String token, Kind kind, List<String> ids, boolean live) {}

    /** The fixed answer when {@code userInput} was a memory command (or a pick); empty to answer normally. */
    public Mono<Optional<String>> handle(TurnTicket ticket, String userInput) {
        return takePending(ticket).flatMap(pending -> {
            if (pending.isPresent() && pending.get().live()) {
                var picks = MemoryCommand.selection(userInput, pending.get().ids().size());
                if (picks.isPresent()) {
                    var ids = picks.get().stream().map(n -> pending.get().ids().get(n - 1)).toList();
                    return apply(ticket, pending.get().kind(), ids).map(Optional::of);
                }
            }
            return MemoryCommand.parse(userInput)
                    .map(command -> command(ticket, command).map(Optional::of))
                    .orElseGet(() -> Mono.just(Optional.empty()));
        });
    }

    private Mono<String> command(TurnTicket ticket, MemoryCommand command) {
        var scope = ticket.scope();
        var status = command.kind() == Kind.FORGET ? ItemStatus.ACTIVE : ItemStatus.FORGOTTEN;
        return settings.crossConversationEnabled(scope)
                .flatMap(cross -> items.findForCommand(scope, cross ? null : ticket.conversationId(), status,
                        props.getScanLimit()).collectList())
                .flatMap(found -> {
                    var candidates = rank(command.topic(), found);
                    log.info("memory.command kind={} candidates={}", command.kind(), candidates.size());
                    if (candidates.isEmpty()) {
                        return mark(ticket, command.kind()).thenReturn(noMatch(command));
                    }
                    if (candidates.size() == 1) {
                        return apply(ticket, command.kind(), List.of(candidates.getFirst().id()));
                    }
                    return savePending(ticket, command.kind(), candidates)
                            .then(mark(ticket, command.kind()))
                            .thenReturn(pickList(command.kind(), candidates));
                });
    }

    private List<MemoryItem> rank(String topic, List<MemoryItem> found) {
        record Scored(MemoryItem item, double score, int order) {}
        var scored = new ArrayList<Scored>();
        for (int i = 0; i < found.size(); i++) {
            double s = MemoryCommand.score(topic, found.get(i).content());
            if (s >= props.getMinScore()) {
                scored.add(new Scored(found.get(i), s, i));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed().thenComparingInt(Scored::order))
                .limit(Math.max(1, props.getMaxCandidates()))
                .map(Scored::item)
                .toList();
    }

    private Mono<String> apply(TurnTicket ticket, Kind kind, List<String> ids) {
        var scope = ticket.scope();
        Mono<List<MemoryItem>> changed = kind == Kind.FORGET
                ? itemService.forget(scope, ids, scope.subject(), FORGET_ACTION)
                : itemService.restoreAll(scope, ids, scope.subject(), RESTORE_ACTION);
        return changed.flatMap(list -> mark(ticket, kind).thenReturn(applied(kind, list)));
    }

    private Mono<Void> mark(TurnTicket ticket, Kind kind) {
        return db.sql("UPDATE chat_turns SET memory_command = :kind WHERE id = :id AND tenant_id = :tenantId"
                        + " AND subject = :subject")
                .bind("kind", kind.name()).bind("id", ticket.turnId())
                .bind("tenantId", ticket.scope().tenantId()).bind("subject", ticket.scope().subject())
                .fetch().rowsUpdated().then();
    }

    /** Reads and removes the conversation's open pick (consumed by this turn whatever it says). */
    private Mono<Optional<Pending>> takePending(TurnTicket ticket) {
        var scope = ticket.scope();
        return db.sql("SELECT token, command, candidate_ids,"
                        + " CASE WHEN expires_at > CURRENT_TIMESTAMP(6) THEN 1 ELSE 0 END AS live"
                        + " FROM memory_command_pending WHERE tenant_id = :tenantId AND subject = :subject"
                        + " AND conversation_id = :c")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("c", ticket.conversationId())
                .map((row, meta) -> new Pending(row.get("token", String.class),
                        Kind.valueOf(row.get("command", String.class)),
                        Arrays.asList(row.get("candidate_ids", String.class).split(",")),
                        ((Number) row.get("live")).intValue() == 1))
                .one()
                .flatMap(p -> db.sql("DELETE FROM memory_command_pending WHERE tenant_id = :tenantId"
                                + " AND subject = :subject AND conversation_id = :c AND token = :token")
                        .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                        .bind("c", ticket.conversationId()).bind("token", p.token())
                        .fetch().rowsUpdated()
                        .map(n -> n == 1 ? Optional.of(p) : Optional.<Pending>empty()))
                .defaultIfEmpty(Optional.empty());
    }

    private Mono<Void> savePending(TurnTicket ticket, Kind kind, List<MemoryItem> candidates) {
        var scope = ticket.scope();
        long ttlMicros = Math.max(1, props.getPendingTtl().toNanos() / 1_000);
        return db.sql("DELETE FROM memory_command_pending WHERE tenant_id = :tenantId AND subject = :subject"
                        + " AND conversation_id = :c")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("c", ticket.conversationId())
                .fetch().rowsUpdated()
                .then(db.sql("INSERT INTO memory_command_pending (tenant_id, subject, conversation_id, token, command,"
                                + " candidate_ids, expires_at, created_at) VALUES (:tenantId, :subject, :c, :token,"
                                + " :kind, :ids, TIMESTAMPADD(MICROSECOND, " + ttlMicros
                                + ", CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6))")
                        .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                        .bind("c", ticket.conversationId()).bind("token", NonBlockingIds.uuid())
                        .bind("kind", kind.name())
                        .bind("ids", String.join(",", candidates.stream().map(MemoryItem::id).toList()))
                        .fetch().rowsUpdated())
                .then();
    }

    // --- replies ----------------------------------------------------------------------------------------

    private static String quoted(List<MemoryItem> list) {
        return String.join("、", list.stream().map(i -> "“" + i.content() + "”").toList());
    }

    private static String applied(Kind kind, List<MemoryItem> list) {
        if (list.isEmpty()) {
            return "这些记忆已经发生了变化，未做任何修改。";
        }
        return kind == Kind.FORGET
                ? "好的，已忘记：" + quoted(list) + "。之后的回答不会再使用这些记忆；你仍可在记忆列表中查看，"
                        + "需要时可以说“记起来…”恢复。"
                : "好的，已记起：" + quoted(list) + "。之后的回答可以再次使用这些记忆。";
    }

    private static String noMatch(MemoryCommand command) {
        return command.kind() == Kind.FORGET
                ? "没有找到与“" + command.topic() + "”相符的记忆，未做任何修改。"
                : "没有找到与“" + command.topic() + "”相符的已忘记记忆，未做任何修改。";
    }

    private String pickList(Kind kind, List<MemoryItem> candidates) {
        var text = new StringBuilder("找到 ").append(candidates.size()).append(" 条相符的")
                .append(kind == Kind.FORGET ? "记忆" : "已忘记记忆").append("：\n");
        for (int i = 0; i < candidates.size(); i++) {
            text.append(i + 1).append(". ").append(candidates.get(i).content()).append('\n');
        }
        long minutes = Math.max(1, props.getPendingTtl().toMinutes());
        text.append("请回复编号（如“1”或“1、3”）选择要").append(kind == Kind.FORGET ? "忘记" : "记起")
                .append("的条目，或回复“全部”；其他回复会取消本次操作（").append(minutes).append(" 分钟内有效）。");
        return text.toString();
    }
}
