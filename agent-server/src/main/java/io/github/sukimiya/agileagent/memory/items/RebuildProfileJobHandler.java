package io.github.sukimiya.agileagent.memory.items;

import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.jobs.ClaimedJob;
import io.github.sukimiya.agileagent.memory.jobs.ConsolidationQuota;
import io.github.sukimiya.agileagent.memory.jobs.JobType;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import reactor.core.publisher.Mono;

/**
 * Handles {@link JobType#REBUILD_PROFILE}: rebuilds the user profile snapshot when cross-conversation is on.
 * Token usage is not observed (deterministic rebuild); quota settle is a no-op net-zero via the worker.
 */
@Component
public class RebuildProfileJobHandler {

    private final ProfileService profiles;
    private final ConsolidationQuota quota;
    private final MemoryProperties props;

    public RebuildProfileJobHandler(ProfileService profiles, MemoryJobWorker worker, ConsolidationQuota quota,
            MemoryProperties props) {
        this.profiles = profiles;
        this.quota = quota;
        this.props = props;
        worker.register(JobType.REBUILD_PROFILE, this::handle);
    }

    public static String dedupeKey(MemoryScope scope) {
        return "rebuild-profile:" + scope.tenantId() + ":" + scope.subject();
    }

    private Mono<Void> handle(ClaimedJob job) {
        // Deterministic rebuild: settle reserved quota as unused (net 0) when observed=0 via worker path;
        // mark observed zero explicitly so ConsolidationQuota does not leave the reserve.
        return profiles.rebuild(job.scope())
                .then(Mono.defer(() -> quota == null ? Mono.empty()
                        : quota.settle(job.scope().tenantId(), props.getConsolidation().getReserveTokens(),
                                new TokenUsage(true, 0, 0, 0))));
    }
}
