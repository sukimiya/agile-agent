package io.github.sukimiya.agileagent.memory.storage;

import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.memory.InstanceGuard;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.jobs.ClaimedJob;
import io.github.sukimiya.agileagent.memory.jobs.JobType;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobQueue;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Schedules the {@link JobType#COMPRESS_MESSAGES} jobs (M2b Task 9). Every {@code app.memory.storage.interval}
 * (default 10m) it finds up to {@code batch} conversations with storage work and no open compress job, and
 * enqueues one job each. A job handles up to {@code batch} rows of its conversation via {@link MessageCompressor},
 * renewing its lease as it goes; what is left is picked up by the next scan. The persistent job worker supplies
 * lease, backoff and the single-instance pause. Runs only with {@code app.memory.storage.enabled=true} (default
 * off) and background work on.
 */
@Component
public class MessageStorageScanner implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MessageStorageScanner.class);

    private final MemoryJobQueue queue;
    private final MemoryJobWorker worker;
    private final MessageCompressor compressor;
    private final MemoryProperties props;
    private final BooleanSupplier conflict;
    private volatile Disposable schedule;

    @Autowired
    public MessageStorageScanner(DatabaseClient db, MemoryJobQueue queue, MemoryJobWorker worker,
            MessageCompressor compressor, MemoryProperties props, InstanceGuard guard) {
        this(db, queue, worker, compressor, props, guard::conflict);
    }

    /** {@code conflict} answers whether scanning must pause (another live instance in the namespace). */
    public MessageStorageScanner(DatabaseClient db, MemoryJobQueue queue, MemoryJobWorker worker,
            MessageCompressor compressor, MemoryProperties props, BooleanSupplier conflict) {
        this.queue = queue;
        this.worker = worker;
        this.compressor = compressor;
        this.props = props;
        this.conflict = conflict;
        worker.register(JobType.COMPRESS_MESSAGES, this::handle);
    }

    private Mono<Void> handle(ClaimedJob job) {
        if (job.conversationId() == null) {
            return Mono.empty();
        }
        return compressor.compressConversation(job.scope(), job.conversationId(), props.getStorage().getBatch(),
                () -> worker.renewLease(job)).then();
    }

    /** One scan; emits how many jobs were enqueued. */
    public Mono<Integer> scan() {
        return Mono.defer(() -> {
            if (!props.getStorage().isEnabled() || conflict.getAsBoolean()) {
                return Mono.just(0);
            }
            return compressor.candidates(props.getStorage().getBatch(), true)
                    .flatMapMany(Flux::fromIterable)
                    .concatMap(c -> queue.enqueue(c.scope(), c.conversationId(), JobType.COMPRESS_MESSAGES, null,
                            null))
                    .filter(Boolean::booleanValue)
                    .count()
                    .map(Long::intValue);
        });
    }

    @Override
    public void start() {
        var every = props.getStorage().getInterval();
        schedule = Flux.interval(every, every)
                .onBackpressureDrop()
                .concatMap(tick -> scan().onErrorResume(e -> {
                    log.warn("memory.storage scan failed: {}",
                            NestedExceptionUtils.getMostSpecificCause(e).toString());
                    return Mono.just(0);
                }))
                .subscribe(count -> {
                    if (count > 0) {
                        log.info("memory.storage scan enqueued {} conversation(s)", count);
                    }
                });
    }

    @Override
    public void stop() {
        var current = schedule;
        if (current != null) {
            current.dispose();
        }
        schedule = null;
    }

    @Override
    public boolean isRunning() {
        var current = schedule;
        return current != null && !current.isDisposed();
    }

    /** Scheduled only when storage compression is enabled and background work is on. */
    @Override
    public boolean isAutoStartup() {
        return props.isRecoveryEnabled() && props.getStorage().isEnabled();
    }
}
