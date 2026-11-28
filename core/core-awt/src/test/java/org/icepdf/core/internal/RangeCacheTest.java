/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.internal;

import org.icepdf.core.pobjects.FetchStats;
import org.icepdf.core.pobjects.NetworkThrottle;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-662: the range cache fetches each block once, whatever asks for it and on whichever thread, in requests sized
 * to what's read, at most a few at once; what it hands back is the document's bytes.
 */
public class RangeCacheTest {

    /** Serves a byte array, counting requests and the bytes and chunks they asked for. */
    static final class ArrayFetcher implements RangeFetcher {
        final byte[] data;
        final AtomicInteger requests = new AtomicInteger();
        final AtomicInteger live = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        final int[] timesFetched;
        final int chunk;
        volatile int failuresLeft;
        volatile long delayMillis;

        ArrayFetcher(byte[] data, int chunk) {
            this.data = data;
            this.chunk = chunk;
            timesFetched = new int[(data.length + chunk - 1) / chunk];
        }

        @Override
        public long length() {
            return data.length;
        }

        @Override
        public void fetch(long from, byte[] target, int offset, int length, FetchAttempt attempt)
                throws IOException {
            requests.incrementAndGet();
            peak.accumulateAndGet(live.incrementAndGet(), Math::max);
            try {
                fetchNow(from, target, offset, length);
                attempt.status = 206;
            } finally {
                live.decrementAndGet();
            }
        }

        private void fetchNow(long from, byte[] target, int offset, int length) throws IOException {
            if (failuresLeft > 0) {
                failuresLeft--;
                throw new IOException("planned failure");
            }
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            synchronized (timesFetched) {
                for (long c = from / chunk; c <= (from + length - 1) / chunk; c++) {
                    timesFetched[(int) c]++;
                }
            }
            System.arraycopy(data, (int) from, target, offset, length);
        }
    }

    private static byte[] randomBytes(int n) {
        byte[] data = new byte[n];
        new Random(662).nextBytes(data);
        // no zero bytes, so an unfetched (zero) byte can't pass for a fetched one
        for (int i = 0; i < n; i++) {
            if (data[i] == 0) data[i] = 1;
        }
        return data;
    }

    private static void assertSame(byte[] data, ByteBuffer buffer, int from, int to) {
        for (int i = from; i < to; i++) {
            assertEquals(data[i], buffer.get(i), "byte " + i);
        }
    }

    /** Chunks of 4 KB, blocks of 1 KB, a first read of 256 bytes. */
    private static RangeCache cache(ArrayFetcher fetcher, int maxConcurrent) throws IOException {
        return new RangeCache(fetcher, 4096, 1024, 256, maxConcurrent);
    }

    @Test
    public void aRangeReadReadsAheadAChunk() throws IOException {
        byte[] data = randomBytes(20_000);
        ArrayFetcher fetcher = new ArrayFetcher(data, 1024);
        RangeCache cache = cache(fetcher, 4);
        try {
            cache.ensure(2100, 100);
            // blocks 2 to 6: from the start, a chunk on
            assertEquals(1, fetcher.requests.get());
            assertEquals(5 * 1024, cache.getBytesFetched());
            assertSame(data, cache.getBuffer(), 2100, 2200);
            assertEquals(0, cache.getBuffer().get(0), "block 0 was not fetched");
            assertEquals(0, fetcher.timesFetched[7]);
            // asking again costs nothing
            cache.ensure(2048, 4096);
            assertEquals(1, fetcher.requests.get());
            assertFalse(cache.isComplete());
        } finally {
            cache.close();
        }
    }

    @Test
    public void missingRunsAShortGapApartComeInOneRequest() throws IOException {
        byte[] data = randomBytes(20_000);
        ArrayFetcher fetcher = new ArrayFetcher(data, 1024);
        RangeCache cache = new RangeCache(fetcher, 1024, 1024, 256, 4);
        try {
            cache.ensure(5000, 10);
            // blocks 0-3 and 5-19 around block 4: one request over the gap
            cache.ensureAll();
            assertEquals(2, fetcher.requests.get());
            assertTrue(cache.isComplete());
            assertSame(data, cache.getBuffer(), 0, data.length);
        } finally {
            cache.close();
        }
        fetcher = new ArrayFetcher(data, 1024);
        cache = new RangeCache(fetcher, 4096, 1024, 256, 4);
        try {
            cache.ensure(4096, 4096);
            // blocks 0-3 and 8-19, four blocks apart: two requests
            cache.ensureAll();
            assertEquals(3, fetcher.requests.get());
            assertSame(data, cache.getBuffer(), 0, data.length);
        } finally {
            cache.close();
        }
    }

    @Test
    public void aMarkerSearchStartsSmallAndGrows() throws IOException {
        byte[] data = randomBytes(40_000);
        byte[] marker = "endobj".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(marker, 0, data, 300, marker.length);
        System.arraycopy(marker, 0, data, 30_000, marker.length);
        ArrayFetcher fetcher = new ArrayFetcher(data, 1024);
        RangeCache cache = cache(fetcher, 4);
        try {
            // a small object, alone: one block
            assertEquals(306, cache.ensureUntil(100, marker));
            assertEquals(1, fetcher.requests.get());
            assertEquals(1024, cache.getBytesFetched());
            // a big one, far from anything fetched: windows growing fourfold, nothing far past the marker
            assertEquals(30_006, cache.ensureUntil(20_100, marker));
            assertSame(data, cache.getBuffer(), 20_100, 30_006);
            assertEquals(0, fetcher.timesFetched[37], "nothing past the window holding the marker");
            // no marker: to the end
            assertEquals(data.length, cache.ensureUntil(31_000, "nowhere".getBytes(StandardCharsets.US_ASCII)));
            assertSame(data, cache.getBuffer(), 31_000, data.length);
            assertFalse(cache.isComplete(), "the start was never asked for");
        } finally {
            cache.close();
        }
    }

    @Test
    public void aRunOfObjectsReadsOnAsFarAsTheRunIsLong() throws IOException {
        byte[] data = randomBytes(40_000);
        byte[] marker = "endobj".getBytes(StandardCharsets.US_ASCII);
        for (int at = 500; at < data.length; at += 1000) {
            System.arraycopy(marker, 0, data, at, marker.length);
        }
        ArrayFetcher fetcher = new ArrayFetcher(data, 1024);
        RangeCache cache = cache(fetcher, 4);
        try {
            cache.ensure(0, 3 * 1024);
            // blocks 0-3 present; the next object follows on: read ahead four blocks, the run's length
            int requests = fetcher.requests.get();
            cache.ensureUntil(4100, marker);
            assertEquals(requests + 1, fetcher.requests.get());
            assertEquals(1, fetcher.timesFetched[8]);
            assertEquals(0, fetcher.timesFetched[9]);
            // an object alone, whose search reaches blocks 19-21; then one just before it: a run read backward,
            // so read behind as far as the run is long
            cache.ensureUntil(20_100, marker);
            assertEquals(1, fetcher.timesFetched[21]);
            assertEquals(0, fetcher.timesFetched[18]);
            requests = fetcher.requests.get();
            cache.ensureUntil(19_100, marker);
            assertEquals(requests + 1, fetcher.requests.get());
            assertEquals(1, fetcher.timesFetched[15]);
            assertEquals(0, fetcher.timesFetched[14], "the run ahead is three blocks long");
        } finally {
            cache.close();
        }
    }

    @Test
    public void prefetchFetchesEachWindowInParallelWithinTheCap() throws IOException {
        byte[] data = randomBytes(300_000);
        ArrayFetcher fetcher = new ArrayFetcher(data, 1024);
        fetcher.delayMillis = 20;
        RangeCache cache = cache(fetcher, 3);
        try {
            int[] offsets = new int[64];
            for (int i = 0; i < offsets.length; i++) {
                offsets[i] = 100 + i * 4096;
            }
            long started = System.nanoTime();
            cache.prefetch(offsets);
            long millis = (System.nanoTime() - started) / 1_000_000;
            assertEquals(64, fetcher.requests.get(), "windows four blocks apart: a request each");
            assertTrue(fetcher.peak.get() <= 3, "at most 3 out at once, not " + fetcher.peak.get());
            assertTrue(fetcher.peak.get() >= 2, "in parallel");
            assertTrue(millis < 64 * 20, millis + " ms");
            for (int offset : offsets) {
                assertSame(data, cache.getBuffer(), offset, offset + 256);
            }
            // windows close together share a request
            fetcher.requests.set(0);
            cache.prefetch(new int[]{270_100, 271_300, 272_500});
            assertEquals(1, fetcher.requests.get());
        } finally {
            cache.close();
        }
    }

    @Test
    public void statsLogEachRequestWithWhatItWasFor() throws IOException {
        byte[] data = randomBytes(40_000);
        byte[] marker = "endobj".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(marker, 0, data, 20_300, marker.length);
        ArrayFetcher fetcher = new ArrayFetcher(data, 1024);
        RangeCache cache = cache(fetcher, 4);
        try {
            byte[] first = new byte[2048];
            System.arraycopy(data, 0, first, 0, first.length);
            long start = System.nanoTime();
            cache.seed(first, start, start + 5_000_000, 206);
            cache.ensureUntil(20_100, marker);
            cache.prefetch(new int[]{30_000});
            fetcher.failuresLeft = 1;
            assertThrows(IOException.class, () -> cache.ensure(10_000, 10));
            cache.ensureAll();
            FetchStats stats = cache.stats();
            List<FetchStats.Request> log = stats.getRecentRequests();
            assertEquals(List.of(FetchStats.Initiator.OPEN, FetchStats.Initiator.OBJECT,
                            FetchStats.Initiator.PREFETCH, FetchStats.Initiator.STREAM,
                            FetchStats.Initiator.WHOLE_FILE),
                    log.stream().map(FetchStats.Request::getInitiator).collect(Collectors.toList()));
            assertEquals(5, stats.getRequestCount());
            assertEquals(1, stats.getFailures());
            FetchStats.Request open = log.get(0);
            assertEquals(5, open.getDurationMillis());
            assertEquals(206, open.getStatus());
            FetchStats.Request object = log.get(1);
            assertEquals(19 * 1024, object.getFrom());
            assertEquals(1024, object.getLength());
            assertFalse(object.isPending());
            assertTrue(log.get(3).isFailed());
            assertEquals(Thread.currentThread().getName(), object.getThread());
            for (int i = 1; i < log.size(); i++) {
                assertEquals(log.get(i - 1).getId() + 1, log.get(i).getId());
            }
            assertTrue(stats.isComplete());
            assertEquals(data.length, stats.getBytesPresent());
            assertArrayEquals(new float[]{1f, 1f}, stats.getCoverage(2));
            assertEquals(0, stats.getInFlight());
        } finally {
            cache.close();
        }
    }

    @Test
    public void coverageShowsWhatsHere() throws IOException {
        byte[] data = randomBytes(8 * 1024);
        RangeCache cache = new RangeCache(new ArrayFetcher(data, 1024), 1024, 1024, 256, 4);
        try {
            cache.ensure(0, 2048);
            FetchStats stats = cache.stats();
            assertArrayEquals(new float[]{1f, 0f, 0f, 0f}, stats.getCoverage(4));
            assertArrayEquals(new float[]{0.5f, 0f}, stats.getCoverage(2));
            assertEquals(2048, stats.getBytesPresent());
            assertFalse(stats.isComplete());
        } finally {
            cache.close();
        }
    }

    @Test
    public void theLogKeepsTheLatest() throws IOException {
        int made = RangeCache.LOG_SIZE + 50;
        byte[] data = randomBytes(2 * made * 1024);
        RangeCache cache = new RangeCache(new ArrayFetcher(data, 1024), 1024, 1024, 256, 4);
        try {
            // every other block: a request each
            for (int block = 0; block < 2 * made; block += 2) {
                cache.ensure(block * 1024L, 10);
            }
            FetchStats stats = cache.stats();
            assertEquals(RangeCache.LOG_SIZE, stats.getRecentRequests().size());
            assertEquals(made, stats.getRequestCount());
            assertEquals(stats.getRequestCount(), stats.getRecentRequests().get(RangeCache.LOG_SIZE - 1).getId());
        } finally {
            cache.close();
        }
    }

    @Test
    public void aThrottledLinkWaitsTheLatencyAndSharesTheBandwidth() throws IOException {
        byte[] data = randomBytes(64 * 1024);
        NetworkThrottle throttle = new NetworkThrottle(Duration.ofMillis(50), 64 * 1024);
        ThrottledLink link = new ThrottledLink(throttle);
        RangeFetcher fetcher = link.wrap(new ArrayFetcher(data, 1024));
        byte[] target = new byte[16 * 1024];
        long started = System.nanoTime();
        // 50 ms, then 16 KB at 64 KB/s: 250 ms
        fetcher.fetch(0, target, 0, target.length, new FetchAttempt());
        long millis = (System.nanoTime() - started) / 1_000_000;
        assertTrue(millis >= 290 && millis < 600, millis + " ms");
        // turned off while in use: the next request is quick
        throttle.set(Duration.ZERO, 0);
        assertTrue(throttle.isOff());
        started = System.nanoTime();
        fetcher.fetch(0, target, 0, target.length, new FetchAttempt());
        assertTrue((System.nanoTime() - started) / 1_000_000 < 100);
        // two at once share the pipe: 2 x 16 KB at 64 KB/s is half a second, not a quarter
        throttle.set(Duration.ZERO, 64 * 1024);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            started = System.nanoTime();
            List<Future<?>> both = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                both.add(pool.submit(() -> {
                    fetcher.fetch(0, new byte[16 * 1024], 0, 16 * 1024, new FetchAttempt());
                    return null;
                }));
            }
            for (Future<?> f : both) {
                f.get();
            }
            millis = (System.nanoTime() - started) / 1_000_000;
            assertTrue(millis >= 480, millis + " ms");
        } catch (Exception e) {
            fail(e);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    public void seededBytesCountAsFetched() throws IOException {
        byte[] data = randomBytes(5000);
        ArrayFetcher fetcher = new ArrayFetcher(data, 1024);
        RangeCache cache = new RangeCache(fetcher, 1024);
        try {
            byte[] first = new byte[2048];
            System.arraycopy(data, 0, first, 0, first.length);
            cache.seed(first);
            cache.ensure(0, 2048);
            assertEquals(0, fetcher.requests.get());
            assertSame(data, cache.getBuffer(), 0, 2048);
        } finally {
            cache.close();
        }
    }

    @Test
    public void aFailedFetchCanBeRetried() throws IOException {
        byte[] data = randomBytes(5000);
        ArrayFetcher fetcher = new ArrayFetcher(data, 1024);
        fetcher.failuresLeft = 1;
        RangeCache cache = new RangeCache(fetcher, 1024);
        try {
            assertThrows(IOException.class, () -> cache.ensure(0, 10));
            cache.ensure(0, 10);
            assertSame(data, cache.getBuffer(), 0, 10);
        } finally {
            cache.close();
        }
    }

    @Test
    public void threadsShareFetchesAndSeeTheBytes() throws Exception {
        int chunk = 1024;
        byte[] data = randomBytes(256 * chunk + 100);
        ArrayFetcher fetcher = new ArrayFetcher(data, chunk);
        fetcher.delayMillis = 2;
        RangeCache cache = new RangeCache(fetcher, chunk, 1024, 256, 2);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                int seed = t;
                results.add(pool.submit(() -> {
                    Random random = new Random(seed);
                    ByteBuffer view = cache.getBuffer();
                    for (int i = 0; i < 300; i++) {
                        int from = random.nextInt(data.length);
                        int count = 1 + random.nextInt(4 * chunk);
                        cache.ensure(from, count);
                        int to = Math.min(data.length, from + count);
                        for (int b = from; b < to; b++) {
                            assertEquals(data[b], view.get(b), "byte " + b);
                        }
                    }
                    return null;
                }));
            }
            for (Future<?> result : results) {
                result.get();
            }
            // a block is fetched again only inside a short gap one request spans; never twice by racing threads
            long wire = 0;
            for (int c = 0; c < fetcher.timesFetched.length; c++) {
                wire += fetcher.timesFetched[c];
            }
            assertTrue(wire < fetcher.timesFetched.length * 3L / 2, wire + " blocks for " + fetcher.timesFetched.length);
            assertTrue(fetcher.peak.get() <= 2, "at most 2 out at once, not " + fetcher.peak.get());
        } finally {
            pool.shutdown();
            cache.close();
        }
    }
}
