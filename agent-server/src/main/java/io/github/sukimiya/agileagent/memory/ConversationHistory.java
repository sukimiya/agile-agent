package io.github.sukimiya.agileagent.memory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;

import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryModel.HistoryPage;
import io.github.sukimiya.agileagent.memory.MemoryModel.HistoryWindow;
import io.github.sukimiya.agileagent.memory.MemoryModel.StoredMessage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.repository.SessionRepository;
import io.github.sukimiya.agileagent.repository.SessionRepository.SessionRow;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.r2dbc.spi.Readable;
import reactor.core.publisher.Mono;

/**
 * Reads conversation history from the database, the source of truth. Every read first resolves the live,
 * scope-owned session (so deleted, foreign or legacy conversations are 404 before any cache access) and uses its
 * revision to validate {@link HotMemoryCache}. Only the newest page is cached, and only when every message in the
 * window belongs to a terminal turn (in-progress checkpoints change without a revision bump).
 */
@Service
public class ConversationHistory {

    public static final int MAX_PAGE = 200;

    private static final String SELECT = "SELECT m.id, m.sequence, m.payload, m.source_type, m.created_at, t.status"
            + " FROM chat_messages m JOIN chat_turns t ON t.id = m.turn_id"
            + " WHERE m.conversation_id = :c AND m.tenant_id = :tenantId AND m.subject = :subject";

    private final DatabaseClient db;
    private final SessionRepository sessions;
    private final ConversationRepository conversations;
    private final HotMemoryCache cache;
    private final MemoryProperties props;
    private final MemoryMetrics metrics;

    public ConversationHistory(DatabaseClient db, SessionRepository sessions, ConversationRepository conversations,
            HotMemoryCache cache, MemoryProperties props, MemoryMetrics metrics) {
        this.metrics = metrics;
        this.db = db;
        this.sessions = sessions;
        this.conversations = conversations;
        this.cache = cache;
        this.props = props;
    }

    /**
     * Original messages in ascending sequence: the newest {@code limit} when {@code beforeSequence} is null,
     * otherwise the {@code limit} messages just before it. {@code nextBeforeSequence} pages further back.
     */
    public Mono<HistoryPage> page(MemoryScope scope, String conversationId, Long beforeSequence, int limit) {
        if (limit < 1 || limit > MAX_PAGE) {
            return Mono.error(new MemoryException(ErrorCode.INVALID_ARGUMENT));
        }
        return ownedSession(scope, conversationId).flatMap(session -> beforeSequence != null
                ? newestBefore(scope, conversationId, beforeSequence, limit).map(rows -> toPage(rows, limit))
                : firstPage(scope, session, limit));
    }

    /**
     * Prompt history for a turn: messages of COMPLETED turns only, excluding the turn itself, in order. Records the
     * injected-history metrics (M1 path, used when the context budget is off or by the unbudgeted orchestrator).
     */
    public Mono<List<LlmClient.Message>> contextBefore(TurnTicket ticket) {
        return completedTurnsBefore(ticket)
                .map(turns -> turns.stream().flatMap(turn -> turn.messages().stream())
                        .map(HistoryMessage::message).toList())
                .doOnNext(metrics::historyInjected);
    }

    /** One stored message of a completed turn, as it goes into a prompt. */
    public record HistoryMessage(String id, LlmClient.Message message) {}

    /** The messages of one completed turn in sequence order; a turn is the unit history is kept or dropped by. */
    public record HistoryTurn(String turnId, List<HistoryMessage> messages) {}

    /**
     * The same history as {@link #contextBefore}, grouped by turn (oldest first), without recording metrics. The
     * session is checked first: deleted, foreign or legacy conversations fail with CONVERSATION_NOT_FOUND.
     */
    public Mono<List<HistoryTurn>> completedTurnsBefore(TurnTicket ticket) {
        return ownedSession(ticket.scope(), ticket.conversationId())
                .flatMap(session -> db.sql("SELECT m.id, m.turn_id, m.payload FROM chat_messages m"
                                + " JOIN chat_turns t ON t.id = m.turn_id"
                                + " WHERE m.conversation_id = :c AND m.tenant_id = :tenantId AND m.subject = :subject"
                                + " AND t.status = 'COMPLETED' AND m.turn_id <> :turnId ORDER BY m.sequence")
                        .bind("c", ticket.conversationId())
                        .bind("tenantId", ticket.scope().tenantId())
                        .bind("subject", ticket.scope().subject())
                        .bind("turnId", ticket.turnId())
                        .map(row -> new String[] {row.get("id", String.class), row.get("turn_id", String.class),
                                row.get("payload", String.class)})
                        .all()
                        .collectList())
                .map(this::groupByTurn);
    }

    private List<HistoryTurn> groupByTurn(List<String[]> rows) {
        var turns = new ArrayList<HistoryTurn>();
        String current = null;
        List<HistoryMessage> messages = null;
        for (var row : rows) {
            if (!row[1].equals(current)) {
                current = row[1];
                messages = new ArrayList<>();
                turns.add(new HistoryTurn(current, messages));
            }
            messages.add(new HistoryMessage(row[0], conversations.deserialize(row[2])));
        }
        return turns.stream().map(t -> new HistoryTurn(t.turnId(), List.copyOf(t.messages()))).toList();
    }

    /** Records history injected into an answer call (once per turn), as {@link #contextBefore} does for M1. */
    public void recordInjected(List<LlmClient.Message> injected) {
        metrics.historyInjected(injected);
    }

    /** The turn's own user message text (the current question), read from durable storage. */
    public Mono<String> userInput(TurnTicket ticket) {
        return db.sql("SELECT payload FROM chat_messages WHERE turn_id = :turnId AND role = 'user'"
                        + " AND tenant_id = :tenantId AND subject = :subject")
                .bind("turnId", ticket.turnId())
                .bind("tenantId", ticket.scope().tenantId())
                .bind("subject", ticket.scope().subject())
                .map(row -> row.get("payload", String.class))
                .one()
                .map(payload -> conversations.deserialize(payload).content())
                .switchIfEmpty(Mono.error(() -> new IllegalStateException("accepted turn has no user message")));
    }

    private Mono<SessionRow> ownedSession(MemoryScope scope, String conversationId) {
        return sessions.findScoped(conversationId, scope)
                .switchIfEmpty(Mono.error(() -> new MemoryException(ErrorCode.CONVERSATION_NOT_FOUND)));
    }

    private Mono<HistoryPage> firstPage(MemoryScope scope, SessionRow session, int limit) {
        var conversationId = session.id();
        return cache.get(scope, conversationId, session.revision())
                .filter(window -> window.items().size() >= limit || !window.hasMore())
                .map(window -> {
                    metrics.cacheHit();
                    return fromWindow(window, limit);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    metrics.cacheMiss();
                    var windowSize = props.getCacheMaxMessages();
                    return newestBefore(scope, conversationId, null, Math.max(limit, windowSize)).flatMap(rows -> {
                        var page = toPage(rows, limit);
                        // rows holds at least windowSize + 1 rows whenever older messages exist beyond the window.
                        var windowHasMore = rows.size() > windowSize;
                        var windowRows = windowHasMore ? rows.subList(rows.size() - windowSize, rows.size()) : rows;
                        if (!windowRows.stream().allMatch(m -> m.status() != null && m.status().terminal())) {
                            return Mono.just(page);
                        }
                        var window = new HistoryWindow(windowRows, windowHasMore, session.revision());
                        return cache.put(scope, conversationId, session.revision(), window).thenReturn(page);
                    });
                }));
    }

    /**
     * Reads up to {@code limit + 1} newest rows (before {@code beforeSequence} if set) in ascending order; the
     * extra row only signals that more exist.
     */
    private Mono<List<StoredMessage>> newestBefore(MemoryScope scope, String conversationId, Long beforeSequence,
            int limit) {
        var spec = db.sql(SELECT + (beforeSequence == null ? "" : " AND m.sequence < :before")
                        + " ORDER BY m.sequence DESC LIMIT :limit")
                .bind("c", conversationId)
                .bind("tenantId", scope.tenantId())
                .bind("subject", scope.subject())
                .bind("limit", limit + 1);
        if (beforeSequence != null) {
            spec = spec.bind("before", beforeSequence);
        }
        return spec.map(this::mapRow).all().collectList().map(desc -> {
            var asc = new ArrayList<>(desc);
            java.util.Collections.reverse(asc);
            return (List<StoredMessage>) asc;
        });
    }

    /** {@code rows} holds up to limit+1 ascending rows; the first is the "more exist" sentinel when over limit. */
    private static HistoryPage toPage(List<StoredMessage> rows, int limit) {
        var hasMore = rows.size() > limit;
        var items = hasMore ? rows.subList(rows.size() - limit, rows.size()) : rows;
        return new HistoryPage(items, hasMore ? items.get(0).sequence() : null, hasMore);
    }

    private static HistoryPage fromWindow(HistoryWindow window, int limit) {
        var items = window.items();
        var hasMore = items.size() > limit || window.hasMore();
        var slice = items.size() > limit ? items.subList(items.size() - limit, items.size()) : items;
        return new HistoryPage(slice, hasMore && !slice.isEmpty() ? slice.get(0).sequence() : null, hasMore);
    }

    private StoredMessage mapRow(Readable row) {
        var created = row.get("created_at", LocalDateTime.class);
        var status = row.get("status", String.class);
        return new StoredMessage(
                row.get("id", String.class),
                TurnRepository.number(row.get("sequence")),
                conversations.deserialize(row.get("payload", String.class)),
                SourceType.valueOf(row.get("source_type", String.class)),
                status == null ? null : TurnStatus.valueOf(status),
                created == null ? null : created.atZone(ZoneId.systemDefault()).toInstant());
    }
}
