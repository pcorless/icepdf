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
package org.icepdf.core.util;

import org.icepdf.core.pobjects.HexStringObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The PDF text string rule, 32000-1 7.9.2.2: UTF-16BE when the bytes open FE FF, PDFDocEncoding
 * otherwise.  It used to be written out three times on three different representations of the same
 * bytes; these pin the single copy.
 */
public class TextStringDecodeTest {

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }

    @DisplayName("marker detection - needs both bytes, and only at the front")
    @Test
    public void markerDetection() {
        assertTrue(Utils.isUtf16Be(bytes(0xFE, 0xFF)));
        assertTrue(Utils.isUtf16Be(bytes(0xFE, 0xFF, 0x00, 0x41)));
        assertFalse(Utils.isUtf16Be(bytes(0xFE)));
        assertFalse(Utils.isUtf16Be(bytes(0xFF, 0xFE)));       // little endian is not a PDF text string
        assertFalse(Utils.isUtf16Be(bytes(0x00, 0xFE, 0xFF)));
        assertFalse(Utils.isUtf16Be(new byte[0]));
        assertFalse(Utils.isUtf16Be(null));
    }

    @DisplayName("utf-16 decode - skips the marker, big endian, keeps a trailing odd byte")
    @Test
    public void utf16Decode() {
        assertEquals("Hi", Utils.decodeUtf16Be(bytes(0xFE, 0xFF, 0x00, 0x48, 0x00, 0x69)));
        assertEquals("", Utils.decodeUtf16Be(bytes(0xFE, 0xFF)));
        // high byte first: 0x4E2D is a CJK character, 0x2D4E would be something else entirely
        assertEquals("中", Utils.decodeUtf16Be(bytes(0xFE, 0xFF, 0x4E, 0x2D)));
        // malformed, one byte short of a code unit; keeping it loses less than dropping it
        assertEquals("HA", Utils.decodeUtf16Be(bytes(0xFE, 0xFF, 0x00, 0x48, 0x41)));
    }

    @DisplayName("text string - marked bytes decode as utf-16, unmarked as PDFDocEncoding")
    @Test
    public void textStringPicksTheRightRule() {
        assertEquals("Hi", Utils.decodeTextString(bytes(0xFE, 0xFF, 0x00, 0x48, 0x00, 0x69)));
        assertEquals("Hi", Utils.decodeTextString(bytes(0x48, 0x69)));
        assertNull(Utils.decodeTextString(null));
        assertEquals("", Utils.decodeTextString(new byte[0]));
    }

    @DisplayName("hex string decoding agrees with the shared rule")
    @Test
    public void hexStringUsesTheSameMarkerRule() {
        // the accessor is a byte string for unmarked data, NOT PDFDocEncoding, so only compare the
        // marked case against decodeTextString
        assertEquals(Utils.decodeTextString(bytes(0xFE, 0xFF, 0x00, 0x48, 0x00, 0x69)),
                new HexStringObject("FEFF00480069").getLiteralString());
        assertEquals("Hi", new HexStringObject("FEFF00480069").getLiteralString());
        // unmarked stays one character per byte
        assertEquals("þð", new HexStringObject("FEF0").getLiteralString());
    }
}
