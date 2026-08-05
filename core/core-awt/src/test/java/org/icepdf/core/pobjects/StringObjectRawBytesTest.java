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
package org.icepdf.core.pobjects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link StringObject#getRawBytes()} is the input to character-code splitting, so it must hand back
 * exactly the bytes the file held - no hex-decoding artefacts and no charset interpretation.
 */
public class StringObjectRawBytesTest {

    @DisplayName("hex string - raw bytes are the digit pairs, high byte first")
    @Test
    public void hexRawBytes() {
        assertArrayEquals(new byte[]{0x04, (byte) 0xB9, 0x08, 0x6E, 0x08, (byte) 0xCC},
                new HexStringObject("04B9086E08CC").getRawBytes());
    }

    @DisplayName("hex string - a zero byte is a byte, not an absence")
    @Test
    public void hexRawBytesKeepsZeros() {
        assertArrayEquals(new byte[]{0x00, 0x0E, 0x00, 0x01},
                new HexStringObject("000E0001").getRawBytes());
    }

    @DisplayName("hex string - an odd digit count pads with a trailing zero (7.3.4.3), not a leading one")
    @Test
    public void hexRawBytesOddLength() {
        // <041> is 0x04 0x10, never 0x00 0x41: a leading pad would shift every code that follows.
        assertArrayEquals(new byte[]{0x04, 0x10}, new HexStringObject("041").getRawBytes());
    }

    @DisplayName("hex string - whitespace between digits is ignored")
    @Test
    public void hexRawBytesIgnoresWhitespace() {
        assertArrayEquals(new byte[]{0x04, (byte) 0xB9}, new HexStringObject("04 B9").getRawBytes());
    }

    @DisplayName("hex string - empty string yields no bytes")
    @Test
    public void hexRawBytesEmpty() {
        assertEquals(0, new HexStringObject("").getRawBytes().length);
    }

    @DisplayName("literal string - raw bytes are the char values, high bit preserved")
    @Test
    public void literalRawBytes() {
        String data = new String(new char[]{0x04, 0xB9, 0x00, 0x6E});
        assertArrayEquals(new byte[]{0x04, (byte) 0xB9, 0x00, 0x6E},
                new LiteralStringObject(data).getRawBytes());
    }
}
