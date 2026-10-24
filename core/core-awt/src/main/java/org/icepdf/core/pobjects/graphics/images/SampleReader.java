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
package org.icepdf.core.pobjects.graphics.images;

/**
 * Reads an image's samples most significant bit first from its decoded bytes, each row starting on a byte
 * boundary (8.9.3).  Past the end of the data it reads zeros.
 *
 * @since 7.5.0
 */
final class SampleReader {
    private final byte[] data;
    private int pos;
    private int bits;
    private int bitsLeft;

    SampleReader(byte[] data) {
        this.data = data;
    }

    /**
     * Drops the padding bits left in the previous row's last byte.
     */
    void startRow() {
        bitsLeft = 0;
    }

    /**
     * @param count bits in the sample, 1 to 16.
     * @return the next sample.
     */
    int getBits(int count) {
        if (count == 8 && bitsLeft == 0) {
            return pos < data.length ? data[pos++] & 0xff : 0;
        }
        while (bitsLeft < count) {
            bits = bits << 8 | (pos < data.length ? data[pos++] & 0xff : 0);
            bitsLeft += 8;
        }
        bitsLeft -= count;
        return (bits >>> bitsLeft) & ((1 << count) - 1);
    }
}
