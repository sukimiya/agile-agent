package io.github.sukimiya.agileagent.memory.items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.context.TokenEstimator;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService;
import io.github.sukimiya.agileagent.memory.summary.SummaryJobHandler;
import reactor.core.publisher.Mono;

/**
 * User memory profile snapshot (M2b Task 4, D14): derived from ACTIVE user-level items when cross-conversation
 * recall is enabled. Rebuild is length-capped ({@code app.memory.profile.max-tokens}, default 800) and cached
 * in Redis for {@code cache-ttl} (default 1h) with version checks.
 */
@Component
public class ProfileService {

    private final DatabaseClient db;
    private final MemoryItemRepository items;
    private final MemorySettingsService settings;
    private final ProfileCache cache;
    private final MemoryProperties props;

    public ProfileService(DatabaseClient db, MemoryItemRepository items, MemorySettingsService settings,
            ProfileCache cache, MemoryProperties props) {
        this.db = db;
        this.items = items;
        this.settings = settings;
        this.cache = cache;
        this.props = props;
    }

    /** Empty when cross-conversation is off (no read). */
    public Mono<Optional<Profile>> current(MemoryScope scope) {
        return settings.crossConversationEnabled(scope).flatMap(on -> {
            if (!on) {
                return Mono.just(Optional.empty());
            }
            return loadRow(scope).flatMap(opt -> {
                if (opt.isEmpty()) {
                    return Mono.just(Optional.empty());
                }
                var row = opt.get();
                return cache.get(scope, row.version())
                        .map(Optional::of)
                        .switchIfEmpty(Mono.defer(() -> cache.put(row).thenReturn(Optional.of(row))));
            });
        });
    }

    /**
     * Rebuilds the profile from ACTIVE user-level items. No-op empty content when cross-conversation is off
     * (does not write). Enabling never scans past conversations — only existing user-level items are used.
     */
    public Mono<Profile> rebuild(MemoryScope scope) {
        return settings.crossConversationEnabled(scope).flatMap(on -> {
            if (!on) {
                return Mono.just(new Profile(scope, "", 0, 0));
            }
            return items.findActiveUserLevel(scope).collectList().flatMap(list -> {
                String content = render(list, props.getProfile().getMaxTokens());
                long builtThrough = list.stream().mapToLong(MemoryItem::revision).sum();
                return upsert(scope, content, builtThrough)
                        .flatMap(profile -> cache.invalidate(scope).then(cache.put(profile)).thenReturn(profile));
            });
        });
    }

    /**
     * After a user deleted an item: when a snapshot exists it is re-rendered from the remaining ACTIVE user-level
     * items even if cross-conversation recall is now off, so deleted content never survives in a kept snapshot
     * (this only removes content; no new aggregation happens while the switch is off — the snapshot is not read
     * then). Without a snapshot only the cache is invalidated.
     */
    public Mono<Void> refreshSnapshot(MemoryScope scope) {
        return loadRow(scope).flatMap(opt -> {
            if (opt.isEmpty()) {
                return cache.invalidate(scope);
            }
            return items.findActiveUserLevel(scope).collectList()
                    .flatMap(list -> upsert(scope, render(list, props.getProfile().getMaxTokens()),
                            list.stream().mapToLong(MemoryItem::revision).sum()))
                    .flatMap(profile -> cache.invalidate(scope).then(cache.put(profile)));
        });
    }

    /** Removes the snapshot row and its cache entry (user "delete my profile"). */
    public Mono<Void> purge(MemoryScope scope) {
        return db.sql("DELETE FROM user_memory_profiles WHERE tenant_id = :tenantId AND subject = :subject")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .fetch().rowsUpdated()
                .then(cache.invalidate(scope));
    }

    public Mono<Void> invalidateCache(MemoryScope scope) {
        return cache.invalidate(scope);
    }

    private Mono<Optional<Profile>> loadRow(MemoryScope scope) {
        return db.sql("SELECT content, version, built_through FROM user_memory_profiles"
                        + " WHERE tenant_id = :tenantId AND subject = :subject")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map((row, meta) -> new Profile(scope, row.get("content", String.class),
                        ((Number) row.get("version")).longValue(),
                        ((Number) row.get("built_through")).longValue()))
                .one()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    private Mono<Profile> upsert(MemoryScope scope, String content, long builtThrough) {
        return loadRow(scope).flatMap(opt -> {
            if (opt.isEmpty()) {
                return db.sql("INSERT INTO user_memory_profiles (tenant_id, subject, content, version, built_through,"
                                + " updated_at) VALUES (:tenantId, :subject, :content, 1, :built, CURRENT_TIMESTAMP(6))")
                        .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                        .bind("content", content).bind("built", builtThrough)
                        .fetch().rowsUpdated()
                        .thenReturn(new Profile(scope, content, 1, builtThrough));
            }
            long next = opt.get().version() + 1;
            return db.sql("UPDATE user_memory_profiles SET content = :content, version = :version,"
                            + " built_through = :built, updated_at = CURRENT_TIMESTAMP(6)"
                            + " WHERE tenant_id = :tenantId AND subject = :subject")
                    .bind("content", content).bind("version", next).bind("built", builtThrough)
                    .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                    .fetch().rowsUpdated()
                    .thenReturn(new Profile(scope, content, next, builtThrough));
        });
    }

    /** Segmented profile text (偏好 / 身份 / 进行中 / 约束 / 其他), truncated to {@code maxTokens}. */
    static String render(List<MemoryItem> items, int maxTokens) {
        var sections = new LinkedHashMap<String, List<String>>();
        sections.put("偏好", new ArrayList<>());
        sections.put("身份", new ArrayList<>());
        sections.put("进行中", new ArrayList<>());
        sections.put("约束", new ArrayList<>());
        sections.put("其他", new ArrayList<>());
        for (var item : items) {
            String section = sectionFor(item.itemType());
            sections.get(section).add("- " + item.content().strip());
        }
        var sb = new StringBuilder();
        for (var e : sections.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append('[').append(e.getKey()).append("]\n");
            for (var line : e.getValue()) {
                sb.append(line).append('\n');
            }
        }
        String text = sb.toString().strip();
        if (text.isEmpty()) {
            return "";
        }
        if (TokenEstimator.estimate(text) <= maxTokens) {
            return text;
        }
        return SummaryJobHandler.truncate(text, maxTokens);
    }

    private static String sectionFor(String itemType) {
        String t = itemType == null ? "" : itemType.trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "PREFERENCE", "PREF" -> "偏好";
            case "IDENTITY", "ROLE", "PERSONA" -> "身份";
            case "IN_PROGRESS", "TASK", "PROJECT" -> "进行中";
            case "CONSTRAINT", "RULE", "LIMIT" -> "约束";
            default -> "其他";
        };
    }
}
