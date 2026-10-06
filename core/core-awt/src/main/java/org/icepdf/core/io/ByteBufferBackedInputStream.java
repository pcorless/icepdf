/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.io;

import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Adapts a {@link ByteBuffer} to an {@link InputStream} so stream filters (which all expect an InputStream) can
 * decode directly from a buffer view without first copying the bytes into a byte[].
 * <p>
 * The supplied buffer's position/limit are advanced as bytes are read, so callers must hand in a private view
 * (typically {@code someSharedBuffer.duplicate()}) rather than a buffer that other threads also read from.
 */
public class ByteBufferBackedInputStream extends InputStream {

    private final ByteBuffer buffer;

    public ByteBufferBackedInputStream(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public int read() {
        if (!buffer.hasRemaining()) {
            return -1;
        }
        return buffer.get() & 0xff;
    }

    @Override
    public int read(byte[] bytes, int off, int len) {
        if (!buffer.hasRemaining()) {
            return -1;
        }
        len = Math.min(len, buffer.remaining());
        buffer.get(bytes, off, len);
        return len;
    }

    @Override
    public int available() {
        return buffer.remaining();
    }
}