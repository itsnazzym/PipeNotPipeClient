// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pipenotpipe.data.media;

import io.reactivex.rxjava3.core.Single;
import org.junit.Test;
import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.InfoItem.InfoType;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class InfoRepositoryTest {
    private final AtomicLong clock = new AtomicLong();
    private final InfoRepository repository = new InfoRepository(60, clock::get, service -> 100);

    private Info info(final int service, final String url) {
        return new Info(service, "id", url, url, "Test") { };
    }

    @Test public void hitsCacheWithoutNetwork() {
        final Info cached = info(0, "url");
        repository.put(0, "url", cached, InfoType.STREAM);
        repository.load(false, 0, "url", InfoType.STREAM,
                Single.error(new IOException("must not fetch"))).test().assertValue(cached);
    }

    @Test public void fetchesAndCachesMissingInfo() {
        final Info fetched = info(0, "canonical");
        repository.load(false, 0, "requested", InfoType.STREAM, Single.just(fetched))
                .test().assertValue(fetched);
        assertSame(fetched, repository.get(0, "requested", InfoType.STREAM));
        assertSame(fetched, repository.get(0, "canonical", InfoType.STREAM));
    }

    @Test public void forcedLoadsAreLazyAndRepeatForEachSubscription() {
        final Info cached = info(0, "url");
        final AtomicInteger requests = new AtomicInteger();
        repository.put(0, "url", cached, InfoType.STREAM);
        final Single<Info> load = repository.load(true, 0, "url", InfoType.STREAM,
                Single.fromCallable(() -> { requests.incrementAndGet(); return info(0, "url"); }));
        assertSame(cached, repository.get(0, "url", InfoType.STREAM));
        assertEquals(0, requests.get());
        load.test().assertComplete();
        load.test().assertComplete();
        assertEquals(2, requests.get());
    }

    @Test public void failedExtractionIsNotCached() {
        repository.load(false, 0, "url", InfoType.STREAM,
                Single.error(new IOException("offline"))).test().assertError(IOException.class);
        assertNull(repository.get(0, "url", InfoType.STREAM));
    }

    @Test public void expiredInfoIsFetchedAgain() {
        repository.put(0, "url", info(0, "url"), InfoType.STREAM);
        clock.set(100);
        final Info fresh = info(0, "url");
        repository.load(false, 0, "url", InfoType.STREAM, Single.just(fresh))
                .test().assertValue(fresh);
    }

    @Test public void servicesAndTypesHaveSeparateKeys() {
        final Info a = info(1, "23");
        final Info b = info(12, "3");
        repository.put(1, "23", a, InfoType.STREAM);
        repository.put(12, "3", b, InfoType.STREAM);
        assertSame(a, repository.get(1, "23", InfoType.STREAM));
        assertSame(b, repository.get(12, "3", InfoType.STREAM));
        assertNull(repository.get(1, "23", InfoType.CHANNEL));
    }

    @Test public void onlyStreamsGetCanonicalAliases() {
        final Info channel = info(0, "canonical");
        repository.cacheResult(0, "requested", channel, InfoType.CHANNEL);
        assertSame(channel, repository.get(0, "requested", InfoType.CHANNEL));
        assertNull(repository.get(0, "canonical", InfoType.CHANNEL));
    }

    @Test public void forceFailureInvalidatesRequestedEntry() {
        repository.put(0, "url", info(0, "url"), InfoType.STREAM);
        repository.load(true, 0, "url", InfoType.STREAM,
                Single.error(new IOException("offline"))).test().assertError(IOException.class);
        assertNull(repository.get(0, "url", InfoType.STREAM));
    }
}
