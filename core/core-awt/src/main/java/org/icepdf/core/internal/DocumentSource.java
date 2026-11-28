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

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * The bytes of the document being read, and the lock that guards replacing them.
 * <p>
 * Internal: this package is not exported, and the class will change.  Today the whole document sits in one
 * {@link ByteBuffer}; a later release reads it lazily (HTTP range requests), so callers outside core must not
 * depend on the document being one contiguous in-memory buffer.
 * <p>
 * <b>Concurrency invariant:</b> object parsing reads the buffer lock-free from many threads at once, which is
 * only safe because the backing bytes are read-only and <em>no reader mutates the shared buffer's
 * position/limit</em>.  Any code that reads from it on a worker thread MUST work on its own
 * {@code getBuffer().duplicate()} (a duplicate shares the read-only bytes but has an independent
 * position/limit) - never reposition/slice the shared instance directly.  Code that actually replaces or
 * mutates the buffer (incremental save, signing, cross-reference rebuild) must hold {@link #getLock()} and must
 * not run concurrently with rendering.
 * <p>
 * <b>Range sources (GH-662):</b> a document opened over HTTP range requests is fetched as it's read. Its buffer
 * covers the whole file, but a range not yet fetched reads as zeros, so code that reads the bytes asks for its
 * range first: {@link #ensure}, {@link #ensureUntil}, and {@link #ensureAll} for work that needs every byte. For a
 * document read from a file or memory these do nothing.
 */
public final class DocumentSource {

    private final Object lock = new Object();
    private ByteBuffer buffer;
    // the remote bytes, when the document is a range source; null when the buffer holds the whole document
    private volatile RangeCache rangeCache;
    // the buffer's offset in the file: a header preceded by junk is sliced off (Header.parseHeader)
    private int base;

    /**
     * @return the shared, read-only document buffer; duplicate it before reading off-thread.
     */
    public ByteBuffer getBuffer() {
        return buffer;
    }

    public void setBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    /**
     * Makes the document a range source.
     *
     * @param rangeCache the remote bytes
     * @param base       where {@link #getBuffer()} starts in the file
     */
    public void setRangeCache(RangeCache rangeCache, int base) {
        this.base = base;
        this.rangeCache = rangeCache;
    }

    /**
     * @return the range cache of a document read over HTTP range requests, else null
     */
    public RangeCache getRangeCache() {
        return rangeCache;
    }

    /**
     * @return true when every byte of the document can be read now
     */
    public boolean isComplete() {
        RangeCache cache = rangeCache;
        return cache == null || cache.isComplete();
    }

    /**
     * Makes {@code count} bytes from buffer offset {@code position} readable.
     */
    public void ensure(long position, long count) throws IOException {
        RangeCache cache = rangeCache;
        if (cache != null && !cache.isComplete()) {
            cache.ensure(base + position, count);
        }
    }

    /**
     * Makes the bytes from buffer offset {@code position} through the first of the {@code markers} readable.
     */
    public void ensureUntil(int position, byte[]... markers) throws IOException {
        RangeCache cache = rangeCache;
        if (cache != null && !cache.isComplete()) {
            cache.ensureUntil(base + position, markers);
        }
    }

    /**
     * Fetches the start of the objects at these buffer offsets in parallel, for a range source; see
     * {@link RangeCache#prefetch}.
     */
    public void prefetch(int[] positions) {
        RangeCache cache = rangeCache;
        if (cache != null && !cache.isComplete()) {
            int[] shifted = new int[positions.length];
            for (int i = 0; i < positions.length; i++) {
                shifted[i] = positions[i] < 0 ? -1 : (int) Math.min(Integer.MAX_VALUE, base + positions[i]);
            }
            cache.prefetch(shifted);
        }
    }

    /**
     * Makes the whole document readable: fetches whatever of a range source is still missing.
     */
    public void ensureAll() throws IOException {
        RangeCache cache = rangeCache;
        if (cache != null) {
            cache.ensureAll();
        }
    }

    /**
     * Lock guarding <em>writes</em> to the document buffer (replacing it, or repositioning/slicing the shared
     * instance) - e.g. incremental save, signing, cross-reference rebuild.  Lock-free readers rely on no such
     * mutation happening concurrently; see {@link #getBuffer()}.
     */
    public Object getLock() {
        return lock;
    }
}
