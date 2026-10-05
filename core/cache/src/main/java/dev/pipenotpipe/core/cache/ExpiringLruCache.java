// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pipenotpipe.core.cache;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Bounded, thread-safe, access-ordered cache. Time is elapsed milliseconds. */
public final class ExpiringLruCache<K, V> {
    private final int capacity;
    private final LongSupplier clock;
    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);

    public ExpiringLruCache(final int capacity, final LongSupplier clock) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized V get(final K key) {
        final Entry<V> entry = entries.get(Objects.requireNonNull(key));
        if (entry == null) {
            return null;
        }
        if (expired(entry, clock.getAsLong())) {
            entries.remove(key);
            return null;
        }
        return entry.value;
    }

    public synchronized void put(final K key, final V value, final long ttlMillis) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (ttlMillis <= 0) {
            entries.remove(key);
            return;
        }
        removeExpired();
        entries.put(key, new Entry<>(value, clock.getAsLong(), ttlMillis));
        trimEntries(capacity);
    }

    public synchronized void remove(final K key) {
        entries.remove(Objects.requireNonNull(key));
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized void trimToSize(final int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        removeExpired();
        trimEntries(size);
    }

    public synchronized int size() {
        removeExpired();
        return entries.size();
    }

    private void removeExpired() {
        final long now = clock.getAsLong();
        entries.values().removeIf(entry -> expired(entry, now));
    }

    private boolean expired(final Entry<V> entry, final long now) {
        return now - entry.createdAt >= entry.ttlMillis;
    }

    private void trimEntries(final int size) {
        final Iterator<Map.Entry<K, Entry<V>>> iterator = entries.entrySet().iterator();
        while (entries.size() > size && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static final class Entry<V> {
        private final V value;
        private final long createdAt;
        private final long ttlMillis;

        private Entry(final V value, final long createdAt, final long ttlMillis) {
            this.value = value;
            this.createdAt = createdAt;
            this.ttlMillis = ttlMillis;
        }
    }
}
