/*
 * Copyright 2026 Patrick Corless
 *
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
package org.icepdf.core.pobjects.graphics.images;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SampleReaderTest {

    private static byte[] bytes(int... values) {
        byte[] data = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            data[i] = (byte) values[i];
        }
        return data;
    }

    @Test
    public void readsMostSignificantBitsFirst() {
        SampleReader in = new SampleReader(bytes(0b1011_0010, 0xA5));
        assertEquals(1, in.getBits(1));
        assertEquals(0b01, in.getBits(2));
        assertEquals(0b1, in.getBits(1));
        assertEquals(0b0010, in.getBits(4));
        assertEquals(0xA, in.getBits(4));
        assertEquals(0x5, in.getBits(4));
    }

    @Test
    public void readsWholeAndDoubleBytes() {
        SampleReader in = new SampleReader(bytes(0x12, 0x34, 0x56, 0xff));
        assertEquals(0x12, in.getBits(8));
        assertEquals(0x3456, in.getBits(16));
        assertEquals(0xff, in.getBits(8));
    }

    @Test
    public void aRowStartsOnTheNextByte() {
        // three 2-bit samples, then two bits of padding
        SampleReader in = new SampleReader(bytes(0b01_10_11_00, 0b11_10_01_00));
        assertEquals(1, in.getBits(2));
        assertEquals(2, in.getBits(2));
        assertEquals(3, in.getBits(2));
        in.startRow();
        assertEquals(3, in.getBits(2));
        assertEquals(2, in.getBits(2));
        // a whole-byte read after a partial one still lines up
        in.startRow();
        assertEquals(0, in.getBits(8));
    }

    @Test
    public void readsZerosPastTheEnd() {
        SampleReader in = new SampleReader(bytes(0xf0));
        assertEquals(0xf, in.getBits(4));
        assertEquals(0, in.getBits(8));
        assertEquals(0, in.getBits(16));
    }
}
