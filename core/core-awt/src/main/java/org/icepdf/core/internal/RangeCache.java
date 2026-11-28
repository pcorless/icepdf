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
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A remote document's bytes, fetched as the parser asks for them (GH-662).
 * <p>
 * The bytes live in a sparse temporary file the length of the document, mapped into memory. The parser reads the
 * mapping as it reads any document buffer; a range it hasn't fetched yet reads as zeros, so every reader asks for
 * its range first ({@link #ensure}, {@link #ensureUntil}, {@link #ensureAll}, {@link #prefetch}). Once a block is
 * written it never changes, so the rest of the engine's contract holds: read-only bytes, read lock-free on
 * duplicates.
 * <p>
 * Request sizes: the cache tracks small blocks, so an object read alone (a page's dictionary, say) costs a block or
 * two rather than a chunk. A stream's range reads a chunk ahead, and an object read next to bytes already here reads
 * on in the direction of that run, as far as the run is long, so a dense stretch of small objects soon arrives a
 * chunk a request. Missing runs a short gap apart share one request.
 * <p>
 * Server load: at most {@code maxConcurrent} requests are out at once, whichever threads make them.
 * <p>
 * Thread safety: any number of threads may ask for ranges at once. A thread that needs a block another thread is
 * fetching waits for that fetch instead of making its own. A block is marked present only after its bytes are
 * written, and readers learn that through this object's monitor or the fetch's future, so they see the bytes.
 */
public final class RangeCache {

    private static final Logger logger = Logger.getLogger(RangeCache.class.getName());

    public static final int DEFAULT_CHUNK_SIZE = 64 * 1024;
    /** The unit the cache tracks and fetches in. */
    public static final int BLOCK_SIZE = 2 * 1024;
    /** The first read of an object: most dictionaries end well inside it. */
    public static final int DEFAULT_PROBE_SIZE = 512;
    /** Requests a document has out at once, by default: what a browser allows a host. */
    public static final int DEFAULT_MAX_CONCURRENT = 4;
    /** Missing runs this many blocks apart, or fewer, come in one request. */
    private static final int MERGE_GAP = 2;

    private final RangeFetcher fetcher;
    private final int length;
    private final int chunkSize;
    private final int blockSize;
    private final int probeSize;
    private final int blockCount;
    private final int maxConcurrent;
    // the requests out at once, from every thread reading the document
    private final Semaphore permits;
    private final File file;
    private final RandomAccessFile randomAccessFile;
    private final MappedByteBuffer mapped;

    // guarded by this
    private final BitSet present;
    // guarded by this: the fetch each missing block is waiting on
    private final Map<Integer, CompletableFuture<Void>> inFlight = new HashMap<>();
    private volatile boolean complete;

    private final AtomicLong bytesFetched = new AtomicLong();
    private final AtomicLong requests = new AtomicLong();

    /**
     * @param fetcher   the source of the bytes; the cache closes it
     * @param chunkSize the read-ahead of a range read ({@link #ensure})
     */
    public RangeCache(RangeFetcher fetcher, int chunkSize) throws IOException {
        this(fetcher, chunkSize, BLOCK_SIZE, DEFAULT_PROBE_SIZE, DEFAULT_MAX_CONCURRENT);
    }

    /**
     * @param fetcher   the source of the bytes; the cache closes it
     * @param chunkSize the read-ahead of a range read ({@link #ensure}), and the second window of a marker search
     * @param blockSize the unit the cache tracks and fetches in, at most the chunk size
     * @param probeSize     the first window of a marker search ({@link #ensureUntil}): an object's dictionary
     * @param maxConcurrent the requests out at once, whichever threads make them
     */
    public RangeCache(RangeFetcher fetcher, int chunkSize, int blockSize, int probeSize, int maxConcurrent)
            throws IOException {
        long total = fetcher.length();
        if (total > Integer.MAX_VALUE || total < 0) {
            throw new IOException("Document too large for a range source: " + total + " bytes");
        }
        this.fetcher = fetcher;
        this.length = (int) total;
        this.chunkSize = Math.max(1024, chunkSize);
        this.blockSize = Math.max(256, Math.min(blockSize, this.chunkSize));
        this.probeSize = Math.max(1, Math.min(probeSize, this.chunkSize));
        this.blockCount = (int) ((length + (long) this.blockSize - 1) / this.blockSize);
        this.maxConcurrent = Math.max(1, maxConcurrent);
        this.permits = new Semaphore(this.maxConcurrent, true);
        this.present = new BitSet(blockCount);
        file = Files.createTempFile("icepdf-range", ".pdf").toFile();
        file.deleteOnExit();
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "rw");
            // a sparse file on the usual file systems: disk is used only by the blocks written
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
     * document's length). Only whole blocks count, and a last block that ends the document.
     */
    public void seed(byte[] bytes) {
        int count = Math.min(bytes.length, length);
        ByteBuffer target = mapped.duplicate();
        target.put(bytes, 0, count);
        int blocks = count == length ? blockCount : count / blockSize;
        bytesFetched.addAndGet(count);
        requests.incrementAndGet();
        if (blocks > 0) {
            finish(new int[]{0, blocks - 1}, null);
        }
    }

    /**
     * Makes {@code [from, from + count)} readable, clipped to the document. A fetch it makes reads ahead to a
     * chunk past {@code from}, over bytes not fetched yet: what follows a stream is often read next.
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
        int first = (int) (start / blockSize);
        int last = (int) ((end - 1) / blockSize);
        int readAhead = (int) ((Math.min(length, start + chunkSize) - 1) / blockSize);
        // a range that ends where fetched bytes begin is likely one of a run read backward: read behind too
        int after = presentFrom(last + 1, 1);
        int readBehind = after > 0 ? Math.max(0, first - after) : first;
        fetchBlocks(first, last, readBehind, readAhead);
    }

    /**
     * Makes {@code [from, to)} readable and nothing more.
     */
    private void ensureExactly(int from, int to) throws IOException {
        if (!complete && from < to) {
            int first = from / blockSize;
            int last = (to - 1) / blockSize;
            fetchBlocks(first, last, first, last);
        }
    }

    /**
     * Makes the bytes from {@code from} up to and including the first of the {@code markers} after it readable,
     * or to the end of the document when there's none. The first window is small, for a dictionary; if the
     * marker isn't in it, the object is likely a stream's, and the windows grow from a chunk.
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
        // first a probe, to the end of the block it ends in: a small object costs a block or two
        long probeEnd = ((long) start + probeSize + blockSize - 1) / blockSize * blockSize;
        int window = (int) (probeEnd - start);
        // next to bytes already here: likely one of a run of objects read in turn, so read on in the direction
        // the run is going, as far as the run is long (up to a chunk). Two objects that only happen to sit side
        // by side cost a block more; a dense run of them reaches a chunk a request in a few requests.
        int run = fetchedRun(start);
        while (true) {
            int end = (int) Math.min(length, (long) start + window);
            if (run != 0 && end > start && !complete) {
                int first = start / blockSize;
                int last = (end - 1) / blockSize;
                int behind = run > 0 ? Math.max(0, first - run) : first;
                int ahead = run < 0 ? Math.min(blockCount - 1, last - run) : last;
                fetchBlocks(first, last, behind, ahead);
                run = 0;
            } else {
                ensureExactly(start, end);
            }
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
            window = (int) Math.min((long) window * 4, Integer.MAX_VALUE / 2);
            // end on a block boundary, so the next window's start isn't a sliver
            window = (int) Math.min(Integer.MAX_VALUE / 2,
                    ((long) start + window + blockSize - 1) / blockSize * blockSize - start);
        }
    }

    /**
     * Fetches a window at each of the {@code offsets}, in parallel, up to the concurrency limit: the start of
     * objects about to be read, such as every page's dictionary. Windows close together share a request. What a
     * window doesn't hold is read as usual later; a failed window is left for then too.
     *
     * @param offsets where the objects start
     */
    public void prefetch(int[] offsets) {
        if (complete || offsets.length == 0) {
            return;
        }
        int[] sorted = offsets.clone();
        Arrays.sort(sorted);
        List<int[]> windows = new ArrayList<>();
        for (int offset : sorted) {
            if (offset < 0 || offset >= length) {
                continue;
            }
            int end = (int) Math.min(length, ((long) offset + probeSize + blockSize - 1) / blockSize * blockSize);
            int[] previous = windows.isEmpty() ? null : windows.get(windows.size() - 1);
            if (previous != null && offset <= previous[1] + MERGE_GAP * blockSize) {
                previous[1] = Math.max(previous[1], end);
            } else {
                windows.add(new int[]{offset, end});
            }
        }
        if (windows.size() == 1) {
            prefetchWindow(windows.get(0));
            return;
        }
        int threads = Math.min(maxConcurrent, windows.size());
        ExecutorService pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "icepdf-range-prefetch");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<CompletableFuture<Void>> pending = new ArrayList<>(windows.size());
            for (int[] window : windows) {
                pending.add(CompletableFuture.runAsync(() -> prefetchWindow(window), pool));
            }
            // not interruptible, like every wait here: the bytes are on their way
            CompletableFuture.allOf(pending.toArray(new CompletableFuture[0])).join();
        } finally {
            pool.shutdown();
        }
    }

    private void prefetchWindow(int[] window) {
        try {
            ensureExactly(window[0], window[1]);
        } catch (IOException | RuntimeException e) {
            logger.log(Level.FINE, "A prefetch failed; its objects are read when asked for", e);
        }
    }

    /**
     * Fetches whatever is still missing: for work that needs every byte (saving, signature digests, rebuilding
     * a damaged file's index).
     */
    public void ensureAll() throws IOException {
        if (!complete) {
            fetchBlocks(0, blockCount - 1, 0, blockCount - 1);
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

    /**
     * Fetches the missing blocks of {@code [first, last]}; a fetch that reaches {@code last} carries on to
     * {@code readAhead} while the blocks there are missing too, and one that starts at {@code first} back to
     * {@code readBehind}.
     */
    private void fetchBlocks(int first, int last, int readBehind, int readAhead) throws IOException {
        List<CompletableFuture<Void>> waits = new ArrayList<>();
        // each batch is one request: the runs of missing blocks it brings, gaps of a block or two between them included
        List<List<int[]>> batches = new ArrayList<>();
        List<CompletableFuture<Void>> mine = new ArrayList<>();
        synchronized (this) {
            int end = last;
            if (isMissing(last)) {
                while (end < readAhead && isMissing(end + 1)) {
                    end++;
                }
            }
            int begin = first;
            if (isMissing(first)) {
                while (begin > readBehind && isMissing(begin - 1)) {
                    begin--;
                }
            }
            int runStart = -1;
            for (int c = begin; c <= end + 1; c++) {
                boolean missing = c <= end && isMissing(c);
                if (c <= end && !present.get(c) && inFlight.containsKey(c)) {
                    CompletableFuture<Void> other = inFlight.get(c);
                    if (!waits.contains(other)) {
                        waits.add(other);
                    }
                }
                if (missing && runStart < 0) {
                    runStart = c;
                } else if (!missing && runStart >= 0) {
                    int[] run = {runStart, c - 1};
                    List<int[]> previous = batches.isEmpty() ? null : batches.get(batches.size() - 1);
                    if (previous != null && run[0] - previous.get(previous.size() - 1)[1] - 1 <= MERGE_GAP) {
                        // a short gap costs less than a request of its own
                        previous.add(run);
                    } else {
                        List<int[]> request = new ArrayList<>();
                        request.add(run);
                        batches.add(request);
                        mine.add(new CompletableFuture<>());
                    }
                    CompletableFuture<Void> future = mine.get(mine.size() - 1);
                    for (int r = run[0]; r <= run[1]; r++) {
                        inFlight.put(r, future);
                    }
                    runStart = -1;
                }
            }
        }
        IOException failure = null;
        for (int i = 0; i < batches.size(); i++) {
            List<int[]> request = batches.get(i);
            try {
                fetchRequest(request);
                for (int[] run : request) {
                    finish(run, null);
                }
                mine.get(i).complete(null);
            } catch (IOException | RuntimeException e) {
                for (int[] run : request) {
                    finish(run, e);
                }
                mine.get(i).completeExceptionally(e);
                if (failure == null) {
                    failure = e instanceof RangeFetchException ? (IOException) e
                            : new RangeFetchException("Could not fetch bytes "
                            + request.get(0)[0] * (long) blockSize + " on: " + e, e);
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
                fetchBlocks(first, last, first, last);
                return;
            }
        }
    }

    /**
     * @return the blocks fetched in a row just before {@code offset}'s block (or from it, when it's here),
     * negated: a run read forward; else those just after it: a run read backward; 0 for neither
     */
    private int fetchedRun(int offset) {
        int block = offset / blockSize;
        int before = presentFrom(block, -1);
        if (before == 0 && block > 0) {
            before = presentFrom(block - 1, -1);
        }
        return before > 0 ? -before : presentFrom(block + 1, 1);
    }

    /**
     * @return the blocks fetched in a row from {@code block} in {@code step}'s direction, at most a chunk's worth
     */
    private synchronized int presentFrom(int block, int step) {
        int most = Math.max(1, chunkSize / blockSize);
        int count = 0;
        for (int b = block; b >= 0 && b < blockCount && count < most && present.get(b); b += step) {
            count++;
        }
        return count;
    }

    // guarded by this
    private boolean isMissing(int block) {
        return block < blockCount && !present.get(block) && !inFlight.containsKey(block);
    }

    /**
     * Fetches the span of a request's runs in one go, and writes only the runs: the gaps between them are
     * already here, or another thread's to write.
     */
    private void fetchRequest(List<int[]> runs) throws IOException {
        int from = runs.get(0)[0] * blockSize;
        int to = (int) Math.min(length, (long) (runs.get(runs.size() - 1)[1] + 1) * blockSize);
        byte[] bytes = new byte[to - from];
        // not interruptible: the viewer interrupts the renders it cancels, and the bytes are wanted anyway
        permits.acquireUninterruptibly();
        try {
            fetcher.fetch(from, bytes, 0, bytes.length);
        } finally {
            permits.release();
        }
        ByteBuffer target = mapped.duplicate();
        for (int[] run : runs) {
            int start = run[0] * blockSize;
            int end = (int) Math.min(length, (long) (run[1] + 1) * blockSize);
            target.position(start);
            target.put(bytes, start - from, end - start);
        }
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
        if (failure == null && present.cardinality() == blockCount) {
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
