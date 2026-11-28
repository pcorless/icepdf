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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A remote document's bytes, fetched in fixed-size chunks as the parser asks for them (GH-662).
 * <p>
 * The bytes live in a sparse temporary file the length of the document, mapped into memory. The parser reads the
 * mapping as it reads any document buffer; a range it hasn't fetched yet reads as zeros, so every reader asks for
 * its range first ({@link #ensure}, {@link #ensureUntil}, {@link #ensureAll}). Once a chunk is written it never
 * changes, so the rest of the engine's contract holds: read-only bytes, read lock-free on duplicates.
 * <p>
 * Thread safety: any number of threads may ask for ranges at once. Missing chunks next to each other are fetched in
 * one request; a thread that needs a chunk another thread is fetching waits for that fetch instead of making its
 * own. A chunk is marked present only after its bytes are written, and readers learn that through this object's
 * monitor or the fetch's future, so they see the bytes.
 */
public final class RangeCache {

    private static final Logger logger = Logger.getLogger(RangeCache.class.getName());

    public static final int DEFAULT_CHUNK_SIZE = 64 * 1024;

    private final RangeFetcher fetcher;
    private final int length;
    private final int chunkSize;
    private final int chunkCount;
    private final File file;
    private final RandomAccessFile randomAccessFile;
    private final MappedByteBuffer mapped;

    // guarded by this
    private final BitSet present;
    // guarded by this: the fetch each missing chunk is waiting on
    private final Map<Integer, CompletableFuture<Void>> inFlight = new HashMap<>();
    private volatile boolean complete;

    private final AtomicLong bytesFetched = new AtomicLong();
    private final AtomicLong requests = new AtomicLong();

    /**
     * @param fetcher   the source of the bytes; the cache closes it
     * @param chunkSize bytes per request unit
     */
    public RangeCache(RangeFetcher fetcher, int chunkSize) throws IOException {
        long total = fetcher.length();
        if (total > Integer.MAX_VALUE || total < 0) {
            throw new IOException("Document too large for a range source: " + total + " bytes");
        }
        this.fetcher = fetcher;
        this.length = (int) total;
        this.chunkSize = Math.max(1024, chunkSize);
        this.chunkCount = (int) ((length + (long) this.chunkSize - 1) / this.chunkSize);
        this.present = new BitSet(chunkCount);
        file = Files.createTempFile("icepdf-range", ".pdf").toFile();
        file.deleteOnExit();
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "rw");
            // a sparse file on the usual file systems: disk is used only by the chunks written
            raf.setLength(length);
            mapped = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, length);
            randomAccessFile = raf;
        } catch (IOException | RuntimeException e) {
            if (raf != null) {
                raf.close();
            }
            file.delete();
            throw e;
        }
        complete = length == 0;
    }

    /**
     * @return the whole document as a read-only buffer; read only the ranges asked for first
     */
    public ByteBuffer getBuffer() {
        return mapped.asReadOnlyBuffer();
    }

    public int length() {
        return length;
    }

    public boolean isComplete() {
        return complete;
    }

    /**
     * @return bytes fetched so far, and the requests that fetched them
     */
    public long getBytesFetched() {
        return bytesFetched.get();
    }

    public long getRequests() {
        return requests.get();
    }

    /**
     * Stores bytes fetched some other way, starting at offset 0 (the answer to the request that found the
     * document's length). Only whole chunks count, and a last chunk that ends the document.
     */
    public void seed(byte[] bytes) {
        int count = Math.min(bytes.length, length);
        ByteBuffer target = mapped.duplicate();
        target.put(bytes, 0, count);
        int chunks = count == length ? chunkCount : count / chunkSize;
        bytesFetched.addAndGet(count);
        requests.incrementAndGet();
        if (chunks > 0) {
            finish(new int[]{0, chunks - 1}, null);
        }
    }

    /**
     * Makes {@code [from, from + count)} readable, clipped to the document.
     */
    public void ensure(long from, long count) throws IOException {
        if (complete || count <= 0) {
            return;
        }
        long start = Math.max(0, from);
        long end = Math.min(length, from + count);
        if (start >= end) {
            return;
        }
        fetchChunks((int) (start / chunkSize), (int) ((end - 1) / chunkSize));
    }

    /**
     * Makes the bytes from {@code from} up to and including the first of the {@code markers} after it readable,
     * a chunk at a time, or to the end of the document when there's none.
     *
     * @return the offset just past the marker found, or the document's length when none was
     */
    public int ensureUntil(int from, byte[]... markers) throws IOException {
        int start = Math.max(0, from);
        int searched = start;
        int longest = 0;
        for (byte[] marker : markers) {
            longest = Math.max(longest, marker.length);
        }
        // first to the end of the chunk the start is in: a small object costs one chunk, not two
        int window = chunkSize - start % chunkSize;
        while (true) {
            int end = (int) Math.min(length, (long) start + window);
            ensure(start, end - start);
            int scanFrom = Math.max(start, searched - longest + 1);
            int found = -1;
            int foundLength = 0;
            for (byte[] marker : markers) {
                int at = indexOf(marker, scanFrom, end);
                if (at >= 0 && (found < 0 || at < found)) {
                    found = at;
                    foundLength = marker.length;
                }
            }
            if (found >= 0) {
                return found + foundLength;
            }
            if (end >= length) {
                return length;
            }
            searched = end;
            // grow the read-ahead: a big object usually means a big stream
            window = (int) Math.min(Math.max((long) window * 2, chunkSize), Integer.MAX_VALUE / 2);
        }
    }

    /**
     * Fetches whatever is still missing: for work that needs every byte (saving, signature digests, rebuilding
     * a damaged file's index).
     */
    public void ensureAll() throws IOException {
        if (!complete) {
            fetchChunks(0, chunkCount - 1);
        }
    }

    private int indexOf(byte[] marker, int from, int to) {
        ByteBuffer view = mapped.duplicate();
        outer:
        for (int i = from, last = to - marker.length; i <= last; i++) {
            for (int j = 0; j < marker.length; j++) {
                if (view.get(i + j) != marker[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private void fetchChunks(int first, int last) throws IOException {
        List<CompletableFuture<Void>> waits = new ArrayList<>();
        List<int[]> runs = new ArrayList<>();
        List<CompletableFuture<Void>> mine = new ArrayList<>();
        synchronized (this) {
            int runStart = -1;
            for (int c = first; c <= last + 1; c++) {
                boolean missing = c <= last && !present.get(c) && !inFlight.containsKey(c);
                if (c <= last && !present.get(c) && inFlight.containsKey(c)) {
                    CompletableFuture<Void> other = inFlight.get(c);
                    if (!waits.contains(other)) {
                        waits.add(other);
                    }
                }
                if (missing && runStart < 0) {
                    runStart = c;
                } else if (!missing && runStart >= 0) {
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    for (int r = runStart; r < c; r++) {
                        inFlight.put(r, future);
                    }
                    runs.add(new int[]{runStart, c - 1});
                    mine.add(future);
                    runStart = -1;
                }
            }
        }
        IOException failure = null;
        for (int i = 0; i < runs.size(); i++) {
            int[] run = runs.get(i);
            try {
                fetchRun(run[0], run[1]);
                finish(run, null);
                mine.get(i).complete(null);
            } catch (IOException | RuntimeException e) {
                finish(run, e);
                mine.get(i).completeExceptionally(e);
                if (failure == null) {
                    failure = e instanceof RangeFetchException ? (IOException) e
                            : new RangeFetchException("Could not fetch bytes " + run[0] * (long) chunkSize
                            + " on: " + e, e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
        for (CompletableFuture<Void> wait : waits) {
            try {
                // not interruptible, like the fetch itself (HttpRangeFetcher): the bytes are on their way
                wait.join();
            } catch (CompletionException e) {
                // the other thread's fetch failed; try once ourselves
                logger.log(Level.FINE, "A shared fetch failed, retrying", e.getCause());
                fetchChunks(first, last);
                return;
            }
        }
    }

    private void fetchRun(int firstChunk, int lastChunk) throws IOException {
        int from = firstChunk * chunkSize;
        int to = (int) Math.min(length, (long) (lastChunk + 1) * chunkSize);
        byte[] bytes = new byte[to - from];
        fetcher.fetch(from, bytes, 0, bytes.length);
        ByteBuffer target = mapped.duplicate();
        target.position(from);
        target.put(bytes);
        bytesFetched.addAndGet(bytes.length);
        requests.incrementAndGet();
    }

    private synchronized void finish(int[] run, Exception failure) {
        for (int c = run[0]; c <= run[1]; c++) {
            inFlight.remove(c);
            if (failure == null) {
                present.set(c);
            }
        }
        if (failure == null && present.cardinality() == chunkCount) {
            complete = true;
        }
    }

    /**
     * Releases the temporary file; the buffer must no longer be read.
     */
    public void close() {
        fetcher.close();
        try {
            randomAccessFile.close();
        } catch (IOException e) {
            logger.log(Level.FINE, "Error closing the range cache file", e);
        }
        if (!file.delete()) {
            logger.fine(() -> "Could not delete " + file + " now; it goes on exit");
        }
    }
}
