package io.github.sukimiya.agileagent.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/** A small synchronized cache whose entries expire after a fixed duration. */
public final class TtlCache<K, V> {

    private final Duration ttl;
    private final Clock clock;
    private final Map<K, Entry<V>> entries = new HashMap<>();

    public TtlCache(Duration ttl, Clock clock) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Cache TTL must be positive");
        }
        this.ttl = ttl;
        this.clock = clock;
    }

    public synchronized Optional<V> get(K key) {
        scavengeExpired();
        var entry = entries.get(key);
        return entry == null ? Optional.empty() : Optional.of(entry.value());
    }

    public synchronized void put(K key, V value) {
        scavengeExpired();
        entries.put(key, new Entry<>(value, clock.instant().plus(ttl)));
    }

    /** Adds a value only when no live value is present and returns the winning value. */
    public synchronized V putIfAbsent(K key, V value) {
        scavengeExpired();
        var current = entries.get(key);
        if (current != null) {
            return current.value();
        }
        entries.put(key, new Entry<>(value, clock.instant().plus(ttl)));
        return value;
    }

    public synchronized void evict(K key) {
        scavengeExpired();
        entries.remove(key);
    }

    public synchronized void evictAll() {
        entries.clear();
    }

    /** Package-visible observability for tests and diagnostics. */
    synchronized int size() {
        scavengeExpired();
        return entries.size();
    }

    private void scavengeExpired() {
        Instant now = clock.instant();
        Iterator<Map.Entry<K, Entry<V>>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!now.isBefore(iterator.next().getValue().expiresAt())) {
                iterator.remove();
            }
        }
    }

    private record Entry<V>(V value, Instant expiresAt) { }
}
