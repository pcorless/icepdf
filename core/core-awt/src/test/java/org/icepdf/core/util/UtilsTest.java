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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the byte and text conversions in {@link Utils}.
 * <p>
 * These are small and used everywhere: the byte-string conversions are how a PDF string keeps one
 * byte per character on its way through Java's {@code String}, and the text-string decoding is what
 * turns the bytes of a bookmark title or an annotation's contents into readable text.  A conversion
 * that is lossy in one direction corrupts every string it touches, and the loss only shows up once
 * the document is written back out.
 */
public class UtilsTest {

    // ------------------------------------------------------------------
    // byte strings
    // ------------------------------------------------------------------

    @DisplayName("a byte string round trips without the platform encoding touching it")
    @Test
    public void byteStringRoundTrip() {
        // Every byte value has to survive, including the ones no charset maps to a character.
        byte[] all = new byte[256];
        for (int i = 0; i < 256; i++) {
            all[i] = (byte) i;
        }
        String asString = Utils.convertByteArrayToByteString(all);
        assertEquals(256, asString.length(), "one character per byte");
        assertArrayEquals(all, Utils.convertByteCharSequenceToByteArray(asString));
    }

    @DisplayName("the high bytes are kept as they are, not sign extended")
    @Test
    public void highBytes() {
        // 0x80..0xFF are negative as Java bytes; widening them without masking would produce
        // characters far outside the 0..255 a byte string is defined over.
        byte[] high = {(byte) 0x80, (byte) 0xFE, (byte) 0xFF};
        String asString = Utils.convertByteArrayToByteString(high);
        assertEquals(0x80, asString.charAt(0));
        assertEquals(0xFF, asString.charAt(2));
        assertArrayEquals(high, Utils.convertByteCharSequenceToByteArray(asString));
    }

    @DisplayName("an empty byte string converts both ways")
    @Test
    public void emptyByteString() {
        assertEquals("", Utils.convertByteArrayToByteString(new byte[0]));
        assertEquals(0, Utils.convertByteCharSequenceToByteArray("").length);
    }

    // ------------------------------------------------------------------
    // text strings
    // ------------------------------------------------------------------

    @DisplayName("a PDFDocEncoded text string decodes to its characters")
    @Test
    public void decodeTextString() {
        assertEquals("Hello", Utils.decodeTextString(new byte[]{'H', 'e', 'l', 'l', 'o'}));
        assertEquals("", Utils.decodeTextString(new byte[0]));
        assertNull(Utils.decodeTextString(null));
    }

    @DisplayName("the UTF-16BE marker is recognised, and only at the start")
    @Test
    public void isUtf16Be() {
        assertTrue(Utils.isUtf16Be(new byte[]{(byte) 0xFE, (byte) 0xFF, 0, 'A'}));
        assertFalse(Utils.isUtf16Be(new byte[]{'A', 'B'}), "no marker");
        assertFalse(Utils.isUtf16Be(new byte[]{(byte) 0xFF, (byte) 0xFE}), "that is the LE marker");
        assertFalse(Utils.isUtf16Be(new byte[]{(byte) 0xFE}), "too short to carry a marker");
        assertFalse(Utils.isUtf16Be(null));
    }

    @DisplayName("a marked UTF-16BE string decodes without its marker")
    @Test
    public void decodeUtf16() {
        byte[] marked = {(byte) 0xFE, (byte) 0xFF, 0, 'H', 0, 'i'};
        assertEquals("Hi", Utils.decodeUtf16Be(marked));
        assertEquals("Hi", Utils.decodeTextString(marked), "the marker selects the decoding");
    }

    @DisplayName("a UTF-16BE string carrying characters outside ASCII decodes to them")
    @Test
    public void decodeUtf16NonAscii() {
        // 0x00E9 is e-acute; this is why the encoding exists.
        byte[] marked = {(byte) 0xFE, (byte) 0xFF, 0x00, (byte) 0xE9};
        assertEquals("é", Utils.decodeUtf16Be(marked));
    }

    @DisplayName("a marker with nothing after it decodes to an empty string")
    @Test
    public void decodeUtf16Empty() {
        assertEquals("", Utils.decodeUtf16Be(new byte[]{(byte) 0xFE, (byte) 0xFF}));
    }

    @DisplayName("a trailing odd byte is kept rather than dropped")
    @Test
    public void decodeUtf16OddLength() {
        // The input is malformed either way; keeping the byte loses less than discarding it.
        byte[] odd = {(byte) 0xFE, (byte) 0xFF, 0, 'H', 'i'};
        assertEquals(2, Utils.decodeUtf16Be(odd).length());
    }

    // ------------------------------------------------------------------
    // numbers and bits
    // ------------------------------------------------------------------

    @DisplayName("numBytesToHoldBits rounds up to the whole byte")
    @Test
    public void numBytesToHoldBits() {
        // Sample tables and image rows are sized with this; rounding down truncates the last row.
        assertEquals(0, Utils.numBytesToHoldBits(0));
        assertEquals(1, Utils.numBytesToHoldBits(1));
        assertEquals(1, Utils.numBytesToHoldBits(8));
        assertEquals(2, Utils.numBytesToHoldBits(9));
        assertEquals(2, Utils.numBytesToHoldBits(16));
        assertEquals(3, Utils.numBytesToHoldBits(17));
    }

    @DisplayName("an int written big endian reads back the same")
    @Test
    public void intRoundTrip() {
        byte[] buffer = new byte[8];
        Utils.setIntIntoByteArrayBE(0x12345678, buffer, 0);
        assertEquals(0x12, buffer[0] & 0xFF, "the most significant byte comes first");
        assertEquals(0x78, buffer[3] & 0xFF);
        assertEquals(0x12345678,
                Utils.readIntWithVaryingBytesBE(ByteBuffer.wrap(buffer), 4));
    }

    @DisplayName("a short written big endian reads back the same")
    @Test
    public void shortRoundTrip() {
        byte[] buffer = new byte[4];
        Utils.setShortIntoByteArrayBE((short) 0x1234, buffer, 0);
        assertEquals(0x12, buffer[0] & 0xFF);
        assertEquals(0x1234, Utils.readIntWithVaryingBytesBE(ByteBuffer.wrap(buffer), 2));
    }

    @DisplayName("a cross-reference field of any width reads big endian")
    @Test
    public void varyingWidthFields() {
        // Cross-reference stream fields are one to eight bytes wide, whatever the /W array says.
        byte[] bytes = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};
        assertEquals(0x00, Utils.readIntWithVaryingBytesBE(ByteBuffer.wrap(bytes), 1));
        assertEquals(0x0001, Utils.readIntWithVaryingBytesBE(ByteBuffer.wrap(bytes), 2));
        assertEquals(0x000102, Utils.readIntWithVaryingBytesBE(ByteBuffer.wrap(bytes), 3));
        assertEquals(0x0001020304050607L,
                Utils.readLongWithVaryingBytesBE(ByteBuffer.wrap(bytes), 8));
    }

    @DisplayName("integers and longs written to a stream are big endian too")
    @Test
    public void writeToStream() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Utils.writeInteger(out, 0x12345678);
        assertArrayEquals(new byte[]{0x12, 0x34, 0x56, 0x78}, out.toByteArray());

        out = new ByteArrayOutputStream();
        Utils.writeLong(out, 0x0102030405060708L);
        assertEquals(8, out.toByteArray().length);
        assertEquals(0x01, out.toByteArray()[0] & 0xFF);
    }

    // ------------------------------------------------------------------
    // hex and lexing helpers
    // ------------------------------------------------------------------

    @DisplayName("bytes render as hex, with and without separators")
    @Test
    public void hexString() {
        byte[] bytes = {0x00, 0x0F, (byte) 0xA0, (byte) 0xFF};
        String plain = Utils.convertByteArrayToHexString(bytes, false);
        assertEquals("000FA0FF", plain.toUpperCase().replace(" ", ""));
        assertTrue(Utils.convertByteArrayToHexString(bytes, true).contains(" "));
    }

    @DisplayName("whitespace and delimiters are classified as the syntax defines them")
    @Test
    public void characterClasses() {
        // These two decide where every token in a PDF ends.
        for (char c : new char[]{' ', '\t', '\r', '\n', '\f', 0}) {
            assertTrue(Utils.isWhitespace(c), "whitespace: " + (int) c);
        }
        assertFalse(Utils.isWhitespace('a'));

        for (char c : new char[]{'(', ')', '<', '>', '[', ']', '{', '}', '/', '%'}) {
            assertTrue(Utils.isDelimiter(c), "delimiter: " + c);
        }
        assertFalse(Utils.isDelimiter('a'));
        assertFalse(Utils.isDelimiter(' '), "a space is whitespace, not a delimiter");
    }
}
