// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pipenotpipe.core.cache;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class ExpiringLruCacheTest {
    private final AtomicLong time = new AtomicLong();
    private final ExpiringLruCache<String, String> cache = new ExpiringLruCache<>(2, time::get);

    @Test public void expiresAtDeadline() {
        cache.put("a", "value", 100);
        time.set(99);
        assertEquals("value", cache.get("a"));
        time.set(100);
        assertNull(cache.get("a"));
        assertEquals(0, cache.size());
    }

    @Test public void evictsLeastRecentlyRead() {
        cache.put("a", "first", 100);
        cache.put("b", "second", 100);
        cache.get("a");
        cache.put("c", "third", 100);
        assertNull(cache.get("b"));
        assertEquals("first", cache.get("a"));
        assertEquals("third", cache.get("c"));
    }

    @Test public void staleEntriesDoNotEvictLiveEntries() {
        cache.put("stale", "value", 10);
        cache.put("live", "value", 100);
        cache.get("stale");
        time.set(10);
        cache.put("new", "value", 100);
        assertEquals("value", cache.get("live"));
        assertEquals(2, cache.size());
    }

    @Test public void replacementRefreshesLifetime() {
        cache.put("a", "first", 10);
        time.set(9);
        cache.put("a", "second", 20);
        time.set(10);
        assertEquals("second", cache.get("a"));
        time.set(29);
        assertNull(cache.get("a"));
    }

    @Test public void supportsTrimRemoveAndClear() {
        cache.put("a", "first", 100);
        cache.put("b", "second", 100);
        cache.trimToSize(1);
        assertNull(cache.get("a"));
        cache.remove("b");
        assertEquals(0, cache.size());
        cache.put("a", "first", 100);
        cache.clear();
        assertNull(cache.get("a"));
    }

    @Test public void nonPositiveLifetimeInvalidatesExistingValue() {
        cache.put("a", "first", 100);
        cache.put("a", "second", 0);
        assertNull(cache.get("a"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidCapacity() {
        new ExpiringLruCache<>(0, time::get);
    }
}
