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
 */
public final class DocumentSource {

    private final Object lock = new Object();
    private ByteBuffer buffer;

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
     * Lock guarding <em>writes</em> to the document buffer (replacing it, or repositioning/slicing the shared
     * instance) - e.g. incremental save, signing, cross-reference rebuild.  Lock-free readers rely on no such
     * mutation happening concurrently; see {@link #getBuffer()}.
     */
    public Object getLock() {
        return lock;
    }
}
