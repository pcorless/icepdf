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
package org.icepdf.core.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link ByteBufferBackedInputStream}, the adapter the stream
 * decode path uses to read directly from a document-buffer view without first
 * copying the bytes into a byte[].
 */
public class ByteBufferBackedInputStreamTest {

    @Test
    @DisplayName("read() returns unsigned bytes in order, then -1 at end of buffer")
    public void singleByteReadIsUnsignedAndStopsAtEnd() {
        // 0x80/0xFF would come back negative if the & 0xff masking were dropped.
        byte[] data = {0x00, 0x7f, (byte) 0x80, (byte) 0xff};
        ByteBufferBackedInputStream in = new ByteBufferBackedInputStream(ByteBuffer.wrap(data));

        assertEquals(0x00, in.read());
        assertEquals(0x7f, in.read());
        assertEquals(0x80, in.read());
        assertEquals(0xff, in.read());
        assertEquals(-1, in.read(), "read() past the limit must return -1");
        assertEquals(-1, in.read(), "repeated read() at end stays -1");
    }

    @Test
    @DisplayName("read(byte[],off,len) honours offset, caps at remaining, and reports -1 at end")
    public void bulkReadHonoursOffsetAndRemaining() {
        byte[] data = {1, 2, 3, 4, 5};
        ByteBufferBackedInputStream in = new ByteBufferBackedInputStream(ByteBuffer.wrap(data));

        byte[] dest = new byte[6];
        // ask for more than is available into the middle of dest
        int read = in.read(dest, 1, 5);
        assertEquals(5, read, "should read all five available bytes");
        assertArrayEquals(new byte[]{0, 1, 2, 3, 4, 5}, dest, "bytes land at the requested offset");
        assertEquals(-1, in.read(dest, 0, 1), "bulk read past the limit returns -1");
    }

    @Test
    @DisplayName("a bulk read larger than the buffer returns only the available bytes")
    public void bulkReadCapsAtRemaining() {
        byte[] data = {10, 20, 30};
        ByteBufferBackedInputStream in = new ByteBufferBackedInputStream(ByteBuffer.wrap(data));

        byte[] dest = new byte[100];
        assertEquals(3, in.read(dest, 0, 100));
        assertEquals(-1, in.read(dest, 0, 100));
    }

    @Test
    @DisplayName("available() tracks remaining bytes and the stream only exposes its buffer window")
    public void availableTracksRemainingWithinSlice() {
        // mimic ByteBufferUtil.sliceObjectStream: a window inside a larger buffer.
        byte[] backing = {99, 99, 7, 8, 9, 99, 99};
        ByteBuffer big = ByteBuffer.wrap(backing);
        big.position(2);
        big.limit(5);
        ByteBuffer view = big.slice(); // position 0, limit 3 -> bytes {7, 8, 9}

        ByteBufferBackedInputStream in = new ByteBufferBackedInputStream(view);
        assertEquals(3, in.available(), "available() reflects the slice window, not the backing array");
        assertEquals(7, in.read());
        assertEquals(2, in.available());

        byte[] rest = new byte[2];
        assertEquals(2, in.read(rest, 0, 2));
        assertArrayEquals(new byte[]{8, 9}, rest, "must not read past the slice limit into the backing array");
        assertEquals(0, in.available());
        assertEquals(-1, in.read());
    }
}