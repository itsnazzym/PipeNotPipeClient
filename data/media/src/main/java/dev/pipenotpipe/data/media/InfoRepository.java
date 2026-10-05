// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pipenotpipe.data.media;

import dev.pipenotpipe.core.cache.ExpiringLruCache;
import io.reactivex.rxjava3.core.Single;
import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.InfoItem.InfoType;

import java.util.Objects;
import java.util.function.IntToLongFunction;
import java.util.function.LongSupplier;

/** Shared extraction cache and cache-first loading, independent of Android and UI. */
public final class InfoRepository {
    private final ExpiringLruCache<Key, Info> cache;
    private final IntToLongFunction expiration;

    public InfoRepository(final int capacity, final LongSupplier clock,
                          final IntToLongFunction expiration) {
        cache = new ExpiringLruCache<>(capacity, clock);
        this.expiration = Objects.requireNonNull(expiration);
    }

    public Info get(final int serviceId, final String url, final InfoType type) {
        return cache.get(new Key(serviceId, url, type));
    }

    public void put(final int serviceId, final String url, final Info info, final InfoType type) {
        cache.put(new Key(serviceId, url, type), info,
                expiration.applyAsLong(info.getServiceId()));
    }

    public void cacheResult(final int serviceId, final String requestedUrl,
                            final Info info, final InfoType type) {
        put(serviceId, requestedUrl, info, type);
        // A stream's canonical URL is also used by the player and download queue.
        if (type == InfoType.STREAM && info.getUrl() != null
                && !requestedUrl.equals(info.getUrl())) {
            put(serviceId, info.getUrl(), info, type);
        }
    }

    /** Lazy per subscription. Errors propagate; failed extractions are never cached. */
    public <I extends Info> Single<I> load(final boolean forceLoad, final int serviceId,
                                          final String url, final InfoType type,
                                          final Single<I> network) {
        Objects.requireNonNull(network);
        return Single.defer(() -> {
            if (forceLoad) {
                remove(serviceId, url, type);
            } else {
                @SuppressWarnings("unchecked")
                final I existing = (I) get(serviceId, url, type);
                if (existing != null) {
                    return Single.just(existing);
                }
            }
            return network.doOnSuccess(info -> cacheResult(serviceId, url, info, type));
        });
    }

    public void remove(final int serviceId, final String url, final InfoType type) {
        cache.remove(new Key(serviceId, url, type));
    }

    public void clear() {
        cache.clear();
    }

    public void trimToSize(final int size) {
        cache.trimToSize(size);
    }

    public int size() {
        return cache.size();
    }

    private record Key(int serviceId, String url, InfoType type) {
        private Key {
            Objects.requireNonNull(url);
            Objects.requireNonNull(type);
        }
    }
}
