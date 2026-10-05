/* Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Derived from NewPipe's InfoCache; retained as the Android compatibility adapter.
 */
package org.schabi.newpipe.util;

import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import dev.pipenotpipe.data.media.InfoRepository;
import io.reactivex.rxjava3.core.Single;
import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.InfoItem;

/** Keeps existing callers compatible while data:media owns cache and load policy. */
public final class InfoCache {
    private static final InfoCache INSTANCE = new InfoCache();
    private final InfoRepository repository = new InfoRepository(
            60, SystemClock::elapsedRealtime, ServiceHelper::getCacheExpirationMillis);

    private InfoCache() { }

    public static InfoCache getInstance() { return INSTANCE; }

    @Nullable
    public Info getFromKey(final int serviceId, @NonNull final String url,
                           @NonNull final InfoItem.InfoType type) {
        return repository.get(serviceId, url, type);
    }

    public void putInfo(final int serviceId, @NonNull final String url, @NonNull final Info info,
                        @NonNull final InfoItem.InfoType type) {
        repository.put(serviceId, url, info, type);
    }

    public void cacheResult(final int serviceId, @NonNull final String url,
                            @NonNull final Info info, @NonNull final InfoItem.InfoType type) {
        repository.cacheResult(serviceId, url, info, type);
    }

    public <I extends Info> Single<I> load(final boolean forceLoad, final int serviceId,
                                          final String url, final InfoItem.InfoType type,
                                          final Single<I> network) {
        return repository.load(forceLoad, serviceId, url, type, network);
    }

    public void removeInfo(final int serviceId, @NonNull final String url,
                           @NonNull final InfoItem.InfoType type) {
        repository.remove(serviceId, url, type);
    }

    public void clearCache() { repository.clear(); }
    public void trimCache() { repository.trimToSize(30); }
    public long getSize() { return repository.size(); }
}
