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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-662: the range cache fetches each chunk once, whatever asks for it and on whichever thread, and what it
 * hands back is the document's bytes.
 */
public class RangeCacheTest {

    /** Serves a byte array, counting requests and the bytes and chunks they asked for. */
    static final class ArrayFetcher implements RangeFetcher {
        final byte[] data;
        final AtomicInteger requests = new AtomicInteger();
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
        public void fetch(long from, byte[] target, int offset, int length) throws IOException {
            requests.incrementAndGet();
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

    @Test
    public void fetchesOnlyTheChunksAskedFor() throws IOException {
        byte[] data = randomBytes(10_000);
        ArrayFetcher fetcher = new ArrayFetcher(data, 1024);
        RangeCache cache = new RangeCache(fetcher, 1024);
        try {
            cache.ensure(2100, 100);
            assertEquals(1, fetcher.requests.get());
            assertEquals(1024, cache.getBytesFetched());
            assertSame(data, cache.getBuffer(), 2100, 2200);
            assertEquals(0, cache.getBuffer().get(0), "chunk 0 was not fetched");
            // asking again costs nothing
            cache.ensure(2048, 1024);
            assertEquals(1, fetcher.requests.get());
            assertFalse(cache.isComplete());
        } finally {
            cache.close();
        }
    }

    @Test
    public void adjacentMissingChunksComeInOneRequest() throws IOException {
        byte[] data = randomBytes(10_000);
        ArrayFetcher fetcher = new ArrayFetcher(data, 1024);
        RangeCache cache = new RangeCache(fetcher, 1024);
        try {
            cache.ensure(5000, 10);
            // chunks 0-3 then 5-9: two runs around the chunk already present
            cache.ensureAll();
            assertEquals(3, fetcher.requests.get());
            assertTrue(cache.isComplete());
            assertSame(data, cache.getBuffer(), 0, data.length);
        } finally {
            cache.close();
        }
    }

    @Test
    public void ensureUntilReadsThroughTheMarker() throws IOException {
        byte[] data = randomBytes(20_000);
        byte[] marker = "endobj".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(marker, 0, data, 7000, marker.length);
        ArrayFetcher fetcher = new ArrayFetcher(data, 1024);
        RangeCache cache = new RangeCache(fetcher, 1024);
        try {
            assertEquals(7006, cache.ensureUntil(100, marker));
            assertSame(data, cache.getBuffer(), 100, 7006);
            // the read-ahead doubles (1 KB, 2 KB, 4 KB...), so it stops within a window of the marker
            assertEquals(0, fetcher.timesFetched[9], "nothing past the window holding the marker");
            // no marker: to the end
            assertEquals(data.length, cache.ensureUntil(8000, "nowhere".getBytes(StandardCharsets.US_ASCII)));
            assertTrue(cache.isComplete());
        } finally {
            cache.close();
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
        RangeCache cache = new RangeCache(fetcher, chunk);
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
            for (int c = 0; c < fetcher.timesFetched.length; c++) {
                assertTrue(fetcher.timesFetched[c] <= 1, "chunk " + c + " fetched " + fetcher.timesFetched[c] + " times");
            }
        } finally {
            pool.shutdown();
            cache.close();
        }
    }
}
