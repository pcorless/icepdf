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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConservativeSizingByteArrayOutputStreamTest {

    /** Records buffer allocation sizes; each reallocation copies everything written so far. */
    private static class RecordingStream extends ConservativeSizingByteArrayOutputStream {
        // not final: the super constructor allocates before this class's fields are initialized
        List<Integer> sizes;

        RecordingStream(int capacity) {
            super(capacity);
        }

        @Override
        protected byte[] allocateByteArray(int size) {
            if (sizes == null) {
                sizes = new ArrayList<>();
            }
            sizes.add(size);
            return super.allocateByteArray(size);
        }
    }

    @DisplayName("growth - a large stream grows geometrically, not by a fixed increment")
    @Test
    public void largeGrowthIsGeometric() {
        // Past 15MB the buffer used to grow a fixed 3MB at a time, so growing to n bytes copied O(n^2) bytes:
        // tens of seconds on a 1GB image stream.
        RecordingStream out = new RecordingStream(16);
        byte[] chunk = new byte[1024 * 1024];
        for (int i = 0; i < 64; i++) {
            out.write(chunk, 0, chunk.length);
        }
        assertEquals(64 * 1024 * 1024, out.size());
        for (int i = 1; i < out.sizes.size(); i++) {
            int previous = out.sizes.get(i - 1);
            if (previous > 16 * 1024 * 1024) {
                assertTrue(out.sizes.get(i) >= previous * 1.2,
                        "grew " + previous + " -> " + out.sizes.get(i) + ", expected at least x1.2");
            }
        }
    }

    @DisplayName("growth - the bytes written come back intact across resizes")
    @Test
    public void contentSurvivesResizing() {
        ConservativeSizingByteArrayOutputStream out = new ConservativeSizingByteArrayOutputStream(1);
        byte[] expected = new byte[20 * 1024 * 1024];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) (i * 31);
        }
        for (int off = 0; off < expected.length; off += 4099) {
            out.write(expected, off, Math.min(4099, expected.length - off));
        }
        byte[] actual = out.toByteArray();
        assertEquals(expected.length, actual.length);
        assertArrayEquals(expected, Arrays.copyOf(actual, actual.length));
    }
}
