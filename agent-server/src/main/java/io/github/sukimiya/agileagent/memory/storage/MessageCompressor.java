package io.github.sukimiya.agileagent.memory.storage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.github.luben.zstd.Zstd;

import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.storage.MessagePayloadCodec.Encoded;
import io.github.sukimiya.agileagent.memory.storage.MessagePayloadCodec.Field;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Tiered compression of stored original text (M2b Task 9, user decisions 2026-10-30): {@code chat_messages.payload}
 * and the {@code chat_tool_events} {@code arguments} / {@code result}.
 * <ul>
 * <li>Warm: plain text of a finished turn (any terminal status, also soft-deleted, forgotten or with deleted
 * items) older than {@code compress-after}, counted from message creation (tool start), is stored as zstd at
 * {@code warm-level}; text already older than {@code recompress-after} goes straight to {@code cold-level}.</li>
 * <li>Cold: warm data older than {@code recompress-after} is recompressed at {@code cold-level}; a shared blob goes
 * cold when its newest reference is that old.</li>
 * <li>Dedupe: text of at least {@code dedupe-min-bytes} (UTF-8) is stored once per tenant in
 * {@code message_blobs}, keyed by its SHA-256; rows keep only the hash. Never across tenants.</li>
 * <li>Turn records, chunks and the full-text index are never touched; nothing is ever physically deleted (soft
 * delete only, no blob collection).</li>
 * <li>Each row is written with a compare-and-set on its codec and {@code updated_at}, so a run is idempotent, can
 * stop anywhere and resume, and never overwrites a row that changed meanwhile.</li>
 * <li>Compression is CPU work and runs on the bounded-elastic scheduler.</li>
 * </ul>
 * Meters: {@code memory.storage.compressed{tier}} rows, {@code memory.storage.compressed.bytes{tier}} stored
 * bytes, {@code memory.storage.compression.ratio} plain/stored, {@code memory.storage.dedupe.hits}.
 */
@Component
public class MessageCompressor {

    private static final Logger log = LoggerFactory.getLogger(MessageCompressor.class);
    private static final String FINISHED = "t.status NOT IN ('ACCEPTED', 'RUNNING')";
    private static final int HEARTBEAT_EVERY = 10;

    /** A conversation with storage work. */
    public record Conversation(MemoryScope scope, String conversationId) {}

    private record Row(String id, LocalDateTime updatedAt, LocalDateTime created, boolean coldAge, String text) {}

    private record Inline(String id, String sha256, byte[] data) {}

    private record Shared(String hash, byte[] data) {}

    private static final class LeaseLost extends RuntimeException {
        LeaseLost() {
            super(null, null, false, false);
        }
    }

    private final DatabaseClient db;
    private final MemoryProperties props;
    private final MeterRegistry meters;
    private final Counter dedupeHits;
    private final DistributionSummary ratio;
    private volatile Runnable beforeWrite = () -> {};

    @Autowired
    public MessageCompressor(DatabaseClient db, MemoryProperties props, MeterRegistry meters) {
        this.db = db;
        this.props = props;
        this.meters = meters == null ? Metrics.globalRegistry : meters;
        props.getStorage().validate();
        loadNativeLibrary(props.getStorage().isEnabled());
        this.dedupeHits = Counter.builder("memory.storage.dedupe.hits")
                .description("Texts stored as a reference to an existing shared blob of the same tenant")
                .register(this.meters);
        this.ratio = DistributionSummary.builder("memory.storage.compression.ratio")
                .description("Plain bytes over stored compressed bytes per compressed text").register(this.meters);
    }

    /**
     * Loads the zstd native library at startup, off the Reactor threads: required when storage is enabled; when it
     * is off a failure is only logged (rows compressed earlier still need it to be read).
     */
    private static void loadNativeLibrary(boolean required) {
        try {
            Zstd.defaultCompressionLevel();
        } catch (Throwable e) { // UnsatisfiedLinkError on an unsupported platform (for example musl without gcompat)
            if (required) {
                throw new IllegalStateException("app.memory.storage.enabled=true but the zstd native library cannot"
                        + " be loaded on this platform (glibc is required, see docs/memory/operations.md)", e);
            }
            log.warn("memory.storage zstd native library not loadable ({}); compressed rows cannot be read",
                    e.getClass().getSimpleName());
        }
    }

    /** Test seam: runs before each row write. */
    void beforeWrite(Runnable hook) {
        this.beforeWrite = hook;
    }

    private static String cutoff(String param) {
        return "TIMESTAMPADD(MICROSECOND, -:" + param + ", CURRENT_TIMESTAMP(6))";
    }

    private long warmMicros() {
        return props.getStorage().getCompressAfter().toNanos() / 1_000;
    }

    private long coldMicros() {
        return props.getStorage().getRecompressAfter().toNanos() / 1_000;
    }

    private static String plainPending(Field f) {
        return "x." + f.column() + " IS NOT NULL AND x." + f.codecColumn() + " IS NULL";
    }

    private static String warmInline(Field f) {
        return "x." + f.codecColumn() + " = '" + MessagePayloadCodec.CODEC_ZSTD + "' AND x." + f.tierColumn() + " = '"
                + MessagePayloadCodec.TIER_WARM + "'";
    }

    // ---- candidates ----------------------------------------------------------------------------------------

    /**
     * Up to {@code limit} conversations with storage work; with {@code withoutOpenJob} only those without a
     * PENDING or RUNNING {@code COMPRESS_MESSAGES} job.
     */
    public Mono<List<Conversation>> candidates(int limit, boolean withoutOpenJob) {
        if (!props.getStorage().isEnabled()) {
            return Mono.just(List.of());
        }
        String noJob = withoutOpenJob ? " AND NOT EXISTS (SELECT 1 FROM memory_jobs j WHERE j.job_type ="
                + " 'COMPRESS_MESSAGES' AND j.status IN ('PENDING', 'RUNNING') AND j.conversation_id ="
                + " x.conversation_id AND j.tenant_id = x.tenant_id)" : "";
        var queries = new ArrayList<Mono<List<Conversation>>>();
        for (var f : Field.values()) {
            String where = props.getStorage().isDecompress()
                    ? "x." + f.codecColumn() + " IS NOT NULL"
                    : "((" + plainPending(f) + " AND " + FINISHED + " AND x." + f.createdColumn() + " <= "
                            + cutoff("warmMicros") + ") OR (" + warmInline(f) + " AND x." + f.createdColumn() + " <= "
                            + cutoff("coldMicros") + "))";
            queries.add(conversations("SELECT DISTINCT x.tenant_id, x.subject, x.conversation_id FROM " + f.table()
                    + " x JOIN chat_turns t ON t.id = x.turn_id WHERE " + where + noJob + " LIMIT :limit", limit));
            if (!props.getStorage().isDecompress()) {
                queries.add(conversations("SELECT DISTINCT x.tenant_id, x.subject, x.conversation_id FROM "
                        + f.table() + " x JOIN message_blobs b ON b.tenant_id = x.tenant_id AND b.content_hash = x."
                        + f.shaColumn() + " WHERE x." + f.codecColumn() + " = '" + MessagePayloadCodec.CODEC_REF
                        + "' AND b.tier = '" + MessagePayloadCodec.TIER_WARM + "' AND b.newest_ref_at <= "
                        + cutoff("coldMicros") + noJob + " LIMIT :limit", limit));
            }
        }
        return Flux.concat(queries).collectList().map(lists -> {
            var out = new LinkedHashSet<Conversation>();
            lists.forEach(out::addAll);
            return out.stream().limit(limit).toList();
        });
    }

    private Mono<List<Conversation>> conversations(String sql, int limit) {
        var spec = db.sql(sql).bind("limit", limit);
        if (sql.contains(":warmMicros")) {
            spec = spec.bind("warmMicros", warmMicros());
        }
        if (sql.contains(":coldMicros")) {
            spec = spec.bind("coldMicros", coldMicros());
        }
        return spec.map(row -> new Conversation(new MemoryScope(row.get("tenant_id", String.class),
                        row.get("subject", String.class)), row.get("conversation_id", String.class)))
                .all().collectList();
    }

    /** Works through every pending row of every conversation now (evaluation, tests); emits rows changed. */
    public Mono<Integer> compressAll() {
        int batch = props.getStorage().getBatch();
        Supplier<Mono<Integer>> round = () -> candidates(batch, false)
                .flatMapMany(Flux::fromIterable)
                .concatMap(c -> compressConversation(c.scope(), c.conversationId(), batch, () -> Mono.just(true)))
                .reduce(0, Integer::sum);
        return Mono.defer(round)
                .expand(n -> n > 0 ? Mono.defer(round) : Mono.empty())
                .reduce(0, Integer::sum);
    }

    // ---- one conversation ----------------------------------------------------------------------------------

    /**
     * Up to {@code limit} rows of one conversation: warm step, then cold step, then shared blobs going cold.
     * {@code heartbeat} is asked every few rows whether to go on (the job lease); emits rows changed.
     */
    public Mono<Integer> compressConversation(MemoryScope scope, String conversationId, int limit,
            Supplier<Mono<Boolean>> heartbeat) {
        if (!props.getStorage().isEnabled()) {
            return Mono.just(0);
        }
        var budget = new AtomicInteger(limit);
        var attempts = new AtomicInteger();
        var steps = new ArrayList<Supplier<Flux<Boolean>>>();
        for (var f : Field.values()) {
            steps.add(() -> warmStep(scope, conversationId, f, budget.get()));
        }
        for (var f : Field.values()) {
            steps.add(() -> coldStep(scope, conversationId, f, budget.get()));
        }
        steps.add(() -> coldBlobs(scope, conversationId, budget.get()));
        return Flux.fromIterable(steps)
                .concatMap(step -> budget.get() <= 0 ? Flux.<Boolean>empty() : step.get()
                        .concatMap(changed -> {
                            budget.decrementAndGet();
                            if (attempts.incrementAndGet() % HEARTBEAT_EVERY != 0) {
                                return Mono.just(changed);
                            }
                            return heartbeat.get().flatMap(held -> held ? Mono.just(changed)
                                    : Mono.error(new LeaseLost()));
                        }))
                .filter(Boolean::booleanValue)
                .count()
                .map(Long::intValue)
                .onErrorResume(LeaseLost.class, e -> Mono.just(0));
    }

    /** Selects up to {@code limit} rows; each element says whether that row was changed. */
    private Flux<Boolean> warmStep(MemoryScope scope, String c, Field f, int limit) {
        if (limit <= 0) {
            return Flux.empty();
        }
        return db.sql("SELECT x.id, x.updated_at, x." + f.createdColumn() + " AS created, CASE WHEN x."
                        + f.createdColumn() + " <= " + cutoff("coldMicros") + " THEN 1 ELSE 0 END AS cold_age, "
                        + MessagePayloadCodec.select(f, "x") + " FROM " + f.table()
                        + " x JOIN chat_turns t ON t.id = x.turn_id" + MessagePayloadCodec.join(f, "x")
                        + " WHERE x.tenant_id = :tenantId AND x.subject = :subject AND x.conversation_id = :c AND "
                        + plainPending(f) + " AND " + FINISHED + " AND x." + f.createdColumn() + " <= "
                        + cutoff("warmMicros") + " ORDER BY x." + f.createdColumn() + ", x.id LIMIT :limit")
                .bind("coldMicros", coldMicros()).bind("warmMicros", warmMicros())
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject()).bind("c", c)
                .bind("limit", limit)
                .map(row -> new Row(row.get("id", String.class), row.get("updated_at", LocalDateTime.class),
                        row.get("created", LocalDateTime.class), ((Number) row.get("cold_age")).intValue() == 1,
                        MessagePayloadCodec.text(row, f)))
                .all()
                .collectList()
                .flatMapMany(Flux::fromIterable)
                .concatMap(row -> compressRow(scope, f, row));
    }

    private Mono<Boolean> compressRow(MemoryScope scope, Field f, Row row) {
        var storage = props.getStorage();
        String tier = row.coldAge() ? MessagePayloadCodec.TIER_COLD : MessagePayloadCodec.TIER_WARM;
        int level = row.coldAge() ? storage.getColdLevel() : storage.getWarmLevel();
        return Mono.fromCallable(() -> MessagePayloadCodec.encode(row.text(), level))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(enc -> {
                    beforeWrite.run();
                    if (enc.plainBytes() >= storage.getDedupeMinBytes()) {
                        return share(scope, enc, tier, row.created())
                                .flatMap(inserted -> writeRef(f, row, enc.sha256())
                                        .doOnNext(ok -> {
                                            if (ok) {
                                                count(tier, inserted ? enc.data().length : 0, enc);
                                            }
                                        }));
                    }
                    return writeInline(f, row, tier, enc).doOnNext(ok -> {
                        if (ok) {
                            count(tier, enc.data().length, enc);
                        }
                    });
                });
    }

    private void count(String tier, long storedBytes, Encoded enc) {
        meters.counter("memory.storage.compressed", "tier", tier).increment();
        if (storedBytes > 0) {
            meters.counter("memory.storage.compressed.bytes", "tier", tier).increment(storedBytes);
        }
        ratio.record(enc.data().length == 0 ? 0 : (double) enc.plainBytes() / enc.data().length);
    }

    /** Stores the shared blob unless the tenant has it already; emits true when it was newly stored. */
    private Mono<Boolean> share(MemoryScope scope, Encoded enc, String tier, LocalDateTime referencedAt) {
        return db.sql("INSERT INTO message_blobs (tenant_id, content_hash, codec, tier, data, plain_bytes,"
                        + " newest_ref_at, created_at, updated_at) VALUES (:tenantId, :hash, '"
                        + MessagePayloadCodec.CODEC_ZSTD + "', :tier, :data, :plainBytes, :ref, CURRENT_TIMESTAMP(6),"
                        + " CURRENT_TIMESTAMP(6))")
                .bind("tenantId", scope.tenantId()).bind("hash", enc.sha256()).bind("tier", tier)
                .bind("data", enc.data()).bind("plainBytes", enc.plainBytes()).bind("ref", referencedAt)
                .fetch().rowsUpdated().map(n -> true)
                .onErrorResume(MessageCompressor::isDuplicateKey, e -> {
                    dedupeHits.increment();
                    return db.sql("UPDATE message_blobs SET newest_ref_at = :ref, updated_at = CURRENT_TIMESTAMP(6)"
                                    + " WHERE tenant_id = :tenantId AND content_hash = :hash AND newest_ref_at < :ref")
                            .bind("ref", referencedAt).bind("tenantId", scope.tenantId()).bind("hash", enc.sha256())
                            .fetch().rowsUpdated().thenReturn(false);
                });
    }

    private Mono<Boolean> writeInline(Field f, Row row, String tier, Encoded enc) {
        return db.sql("UPDATE " + f.table() + " SET " + f.column() + " = NULL, " + f.codecColumn() + " = '"
                        + MessagePayloadCodec.CODEC_ZSTD + "', " + f.tierColumn() + " = :tier, " + f.blobColumn()
                        + " = :data, " + f.shaColumn() + " = :sha WHERE id = :id AND " + f.codecColumn()
                        + " IS NULL AND updated_at = :updatedAt")
                .bind("tier", tier).bind("data", enc.data()).bind("sha", enc.sha256()).bind("id", row.id())
                .bind("updatedAt", row.updatedAt())
                .fetch().rowsUpdated().map(n -> n == 1);
    }

    private Mono<Boolean> writeRef(Field f, Row row, String sha) {
        return db.sql("UPDATE " + f.table() + " SET " + f.column() + " = NULL, " + f.codecColumn() + " = '"
                        + MessagePayloadCodec.CODEC_REF + "', " + f.tierColumn() + " = NULL, " + f.blobColumn()
                        + " = NULL, " + f.shaColumn() + " = :sha WHERE id = :id AND " + f.codecColumn()
                        + " IS NULL AND updated_at = :updatedAt")
                .bind("sha", sha).bind("id", row.id()).bind("updatedAt", row.updatedAt())
                .fetch().rowsUpdated().map(n -> n == 1);
    }

    private Flux<Boolean> coldStep(MemoryScope scope, String c, Field f, int limit) {
        if (limit <= 0) {
            return Flux.empty();
        }
        return db.sql("SELECT x.id, x." + f.shaColumn() + " AS sha, x." + f.blobColumn() + " AS data FROM "
                        + f.table() + " x WHERE x.tenant_id = :tenantId AND x.subject = :subject"
                        + " AND x.conversation_id = :c AND " + warmInline(f) + " AND x." + f.createdColumn() + " <= "
                        + cutoff("coldMicros") + " ORDER BY x." + f.createdColumn() + ", x.id LIMIT :limit")
                .bind("coldMicros", coldMicros())
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject()).bind("c", c)
                .bind("limit", limit)
                .map(row -> new Inline(row.get("id", String.class), row.get("sha", String.class),
                        MessagePayloadCodec.bytes(row.get("data"))))
                .all()
                .collectList()
                .flatMapMany(Flux::fromIterable)
                .concatMap(row -> recompress(f.column(),
                        () -> MessagePayloadCodec.decode(f, new MessagePayloadCodec.Stored(null,
                                MessagePayloadCodec.CODEC_ZSTD, row.data(), row.sha256(), null)),
                        enc -> db.sql("UPDATE " + f.table() + " SET " + f.blobColumn() + " = :data, " + f.tierColumn()
                                        + " = '" + MessagePayloadCodec.TIER_COLD + "' WHERE id = :id AND " + warmInline(f)
                                                .replace("x.", "") + " AND " + f.shaColumn() + " = :sha")
                                .bind("data", enc.data()).bind("id", row.id()).bind("sha", row.sha256())
                                .fetch().rowsUpdated().map(n -> n == 1),
                        row.id()));
    }

    private Flux<Boolean> coldBlobs(MemoryScope scope, String c, int limit) {
        if (limit <= 0) {
            return Flux.empty();
        }
        var refs = new ArrayList<String>();
        for (var f : Field.values()) {
            refs.add("EXISTS (SELECT 1 FROM " + f.table() + " x WHERE x.conversation_id = :c AND x.tenant_id ="
                    + " b.tenant_id AND x." + f.codecColumn() + " = '" + MessagePayloadCodec.CODEC_REF + "' AND x."
                    + f.shaColumn() + " = b.content_hash)");
        }
        return db.sql("SELECT b.content_hash, b.data FROM message_blobs b WHERE b.tenant_id = :tenantId AND b.tier = '"
                        + MessagePayloadCodec.TIER_WARM + "' AND b.newest_ref_at <= " + cutoff("coldMicros")
                        + " AND (" + String.join(" OR ", refs) + ") ORDER BY b.content_hash LIMIT :limit")
                .bind("coldMicros", coldMicros()).bind("tenantId", scope.tenantId()).bind("c", c)
                .bind("limit", limit)
                .map(row -> new Shared(row.get("content_hash", String.class),
                        MessagePayloadCodec.bytes(row.get("data"))))
                .all()
                .collectList()
                .flatMapMany(Flux::fromIterable)
                .concatMap(blob -> recompress("message_blobs",
                        () -> MessagePayloadCodec.decodeShared(blob.data(), blob.hash()),
                        enc -> db.sql("UPDATE message_blobs SET data = :data, tier = '" + MessagePayloadCodec.TIER_COLD
                                        + "', updated_at = CURRENT_TIMESTAMP(6) WHERE tenant_id = :tenantId"
                                        + " AND content_hash = :hash AND tier = '" + MessagePayloadCodec.TIER_WARM + "'")
                                .bind("data", enc.data()).bind("tenantId", scope.tenantId()).bind("hash", blob.hash())
                                .fetch().rowsUpdated().map(n -> n == 1),
                        blob.hash()));
    }

    /**
     * Verifies and recompresses one warm text at the cold level. Damaged data is left as it is (the read path
     * reports it); the row is skipped, never rewritten.
     */
    private Mono<Boolean> recompress(String what, Supplier<String> decode,
            java.util.function.Function<Encoded, Mono<Boolean>> write, String key) {
        int level = props.getStorage().getColdLevel();
        return Mono.fromCallable(() -> MessagePayloadCodec.encode(decode.get(), level))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(enc -> {
                    beforeWrite.run();
                    return write.apply(enc).doOnNext(ok -> {
                        if (ok) {
                            count(MessagePayloadCodec.TIER_COLD, enc.data().length, enc);
                        }
                    });
                })
                .onErrorResume(CorruptStoredPayloadException.class, e -> {
                    log.warn("memory.storage skipped damaged {} key={}: {}", what, key, e.getMessage());
                    return Mono.just(false);
                });
    }

    private static boolean isDuplicateKey(Throwable error) {
        for (var cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }
}
