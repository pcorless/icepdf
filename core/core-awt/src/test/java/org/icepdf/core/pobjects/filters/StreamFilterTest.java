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
package org.icepdf.core.pobjects.filters;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the stream filters: bytes in, bytes out.
 * <p>
 * Every byte of a PDF that is not plain text comes through one of these, so a filter that decodes
 * almost correctly corrupts content streams, images and embedded fonts alike, in ways that surface
 * far from the cause.  The awkward cases are all about how a stream ends - a partial group at the
 * end of an ASCII85 stream, a run that stops exactly on the buffer boundary - which is why the
 * lengths asserted below are deliberately not round numbers.
 */
public class StreamFilterTest {

    private static InputStream bytesOf(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String textOf(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    // ------------------------------------------------------------------
    // ASCIIHexDecode
    // ------------------------------------------------------------------

    @DisplayName("hex - pairs of digits become bytes")
    @Test
    public void asciiHex() throws IOException {
        assertEquals("Hello",
                textOf(new ASCIIHexDecode(bytesOf("48656C6C6F>")).readAllBytes()));
    }

    @DisplayName("hex - whitespace between digits is ignored")
    @Test
    public void asciiHexWhitespace() throws IOException {
        // Producers wrap hex streams at 80 columns, so the line breaks have to be skipped.
        assertEquals("Hello",
                textOf(new ASCIIHexDecode(bytesOf("48 65\n6C\r\n6C 6F>")).readAllBytes()));
    }

    @DisplayName("hex - lower case digits decode the same as upper case")
    @Test
    public void asciiHexCase() throws IOException {
        assertEquals("Hello",
                textOf(new ASCIIHexDecode(bytesOf("48656c6c6f>")).readAllBytes()));
    }

    @DisplayName("hex - an odd final digit is padded with a zero")
    @Test
    public void asciiHexOddLength() throws IOException {
        // "4" at the end means the byte 0x40, not a dropped nibble.
        byte[] decoded = new ASCIIHexDecode(bytesOf("484>")).readAllBytes();
        assertArrayEquals(new byte[]{0x48, 0x40}, decoded);
    }

    @DisplayName("hex - an empty stream decodes to nothing")
    @Test
    public void asciiHexEmpty() throws IOException {
        assertEquals(0, new ASCIIHexDecode(bytesOf(">")).readAllBytes().length);
    }

    // ------------------------------------------------------------------
    // ASCII85Decode
    // ------------------------------------------------------------------

    @DisplayName("ascii85 - a full group of five characters becomes four bytes")
    @Test
    public void ascii85() throws IOException {
        assertEquals("Hello World!",
                textOf(new ASCII85Decode(bytesOf("87cURD]i,\"Ebo80~>")).readAllBytes()));
    }

    @DisplayName("ascii85 - z stands for four zero bytes")
    @Test
    public void ascii85ZeroGroup() throws IOException {
        byte[] decoded = new ASCII85Decode(bytesOf("z~>")).readAllBytes();
        assertArrayEquals(new byte[]{0, 0, 0, 0}, decoded);
    }

    @DisplayName("ascii85 - a partial final group decodes to fewer than four bytes")
    @Test
    public void ascii85PartialGroup() throws IOException {
        // The tail is where this filter goes wrong: n characters yield n-1 bytes, and the value
        // has to be padded with the high 'u' characters before it is unpacked.
        assertEquals("Hell", textOf(new ASCII85Decode(bytesOf("87cUR~>")).readAllBytes()));
        assertEquals(1, new ASCII85Decode(bytesOf("87~>")).readAllBytes().length);
        assertEquals(2, new ASCII85Decode(bytesOf("87c~>")).readAllBytes().length);
        assertEquals(3, new ASCII85Decode(bytesOf("87cU~>")).readAllBytes().length);
    }

    @DisplayName("ascii85 - whitespace inside the stream is ignored")
    @Test
    public void ascii85Whitespace() throws IOException {
        assertEquals("Hello World!",
                textOf(new ASCII85Decode(bytesOf("87cUR\nD]i,\"\r\nEbo80~>")).readAllBytes()));
    }

    @DisplayName("ascii85 - decoding stops at the end of data marker")
    @Test
    public void ascii85EndMarker() throws IOException {
        assertEquals("Hell",
                textOf(new ASCII85Decode(bytesOf("87cUR~>ignored trailing junk")).readAllBytes()));
    }

    // ------------------------------------------------------------------
    // RunLengthDecode
    // ------------------------------------------------------------------

    /**
     * Builds a run-length stream: a length byte under 128 introduces that many plus one literal
     * bytes, one of 129 or more repeats the next byte 257 minus it times, and 128 ends the stream.
     */
    private static byte[] runLength(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    @DisplayName("run length - a literal run is copied out as it stands")
    @Test
    public void runLengthLiteral() throws IOException {
        // 2 introduces three literal bytes
        byte[] decoded = new RunLengthDecode(
                new ByteArrayInputStream(runLength(2, 'a', 'b', 'c', 128))).readAllBytes();
        assertEquals("abc", textOf(decoded));
    }

    @DisplayName("run length - a repeat run expands one byte")
    @Test
    public void runLengthRepeat() throws IOException {
        // 253 repeats the next byte 257 - 253 = 4 times
        byte[] decoded = new RunLengthDecode(
                new ByteArrayInputStream(runLength(253, 'x', 128))).readAllBytes();
        assertEquals("xxxx", textOf(decoded));
    }

    @DisplayName("run length - literal and repeat runs alternate")
    @Test
    public void runLengthMixed() throws IOException {
        byte[] decoded = new RunLengthDecode(
                new ByteArrayInputStream(runLength(1, 'a', 'b', 254, 'c', 0, 'd', 128)))
                .readAllBytes();
        assertEquals("abcccd", textOf(decoded));
    }

    @DisplayName("run length - the longest runs of each kind")
    @Test
    public void runLengthExtremes() throws IOException {
        // 127 is 128 literal bytes, the largest literal run there is
        int[] longLiteral = new int[130];
        longLiteral[0] = 127;
        for (int i = 1; i <= 128; i++) {
            longLiteral[i] = 'a';
        }
        longLiteral[129] = 128;
        assertEquals(128, new RunLengthDecode(
                new ByteArrayInputStream(runLength(longLiteral))).readAllBytes().length);

        // 129 is the longest repeat: 257 - 129 = 128 copies
        assertEquals(128, new RunLengthDecode(
                new ByteArrayInputStream(runLength(129, 'b', 128))).readAllBytes().length);
    }

    @DisplayName("run length - a stream that ends without its marker still decodes")
    @Test
    public void runLengthUnterminated() throws IOException {
        // Damaged files are truncated; what was decoded before the end must still come back.
        assertEquals("abc", textOf(new RunLengthDecode(
                new ByteArrayInputStream(runLength(2, 'a', 'b', 'c'))).readAllBytes()));
    }

    @DisplayName("run length - the end marker stops decoding")
    @Test
    public void runLengthEndMarker() throws IOException {
        assertEquals("ab", textOf(new RunLengthDecode(
                new ByteArrayInputStream(runLength(1, 'a', 'b', 128, 1, 'c', 'd')))
                .readAllBytes()));
    }

    // ------------------------------------------------------------------
    // FlateDecode
    // ------------------------------------------------------------------

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        byte[] out = new byte[raw.length * 2 + 64];
        int length = deflater.deflate(out);
        deflater.end();
        byte[] trimmed = new byte[length];
        System.arraycopy(out, 0, trimmed, 0, length);
        return trimmed;
    }

    @DisplayName("flate - a deflated stream decodes back to what went in")
    @Test
    public void flate() throws IOException {
        byte[] raw = "Hello world, hello world, hello world."
                .getBytes(StandardCharsets.ISO_8859_1);
        FlateDecode decode = new FlateDecode(new Library(), new DictionaryEntries(),
                new ByteArrayInputStream(deflate(raw)));
        assertArrayEquals(raw, decode.readAllBytes());
    }

    @DisplayName("flate - an empty stream decodes to nothing")
    @Test
    public void flateEmpty() throws IOException {
        FlateDecode decode = new FlateDecode(new Library(), new DictionaryEntries(),
                new ByteArrayInputStream(deflate(new byte[0])));
        assertEquals(0, decode.readAllBytes().length);
    }

    @DisplayName("flate - a stream longer than one buffer decodes whole")
    @Test
    public void flateLargeStream() throws IOException {
        // The filter reads in chunks; a stream that spans several of them is where a lost or
        // repeated chunk boundary would show.
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            text.append("line ").append(i).append('\n');
        }
        byte[] raw = text.toString().getBytes(StandardCharsets.ISO_8859_1);
        FlateDecode decode = new FlateDecode(new Library(), new DictionaryEntries(),
                new ByteArrayInputStream(deflate(raw)));
        assertArrayEquals(raw, decode.readAllBytes());
    }

    @DisplayName("flate - garbage that is not deflate data yields no content rather than throwing")
    @Test
    public void flateCorrupt() throws IOException {
        // A damaged stream must not take the page down with it.
        FlateDecode decode = new FlateDecode(new Library(), new DictionaryEntries(),
                bytesOf("this was never compressed"));
        assertEquals(0, decode.readAllBytes().length);
    }

    // ------------------------------------------------------------------
    // PredictorDecode
    // ------------------------------------------------------------------

    /**
     * Decode parameters for a predictor, as they appear in a stream dictionary.
     */
    private static DictionaryEntries predictorParams(int predictor, int columns, int colors,
                                                     int bitsPerComponent) {
        DictionaryEntries decodeParms = new DictionaryEntries();
        decodeParms.put(new Name("Predictor"), predictor);
        decodeParms.put(new Name("Columns"), columns);
        decodeParms.put(new Name("Colors"), colors);
        decodeParms.put(new Name("BitsPerComponent"), bitsPerComponent);
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("DecodeParms"), decodeParms);
        return entries;
    }

    private static byte[] unpredict(byte[] encoded, int predictor, int columns, int colors,
                                    int bitsPerComponent) throws IOException {
        return new PredictorDecode(new ByteArrayInputStream(encoded), new Library(),
                predictorParams(predictor, columns, colors, bitsPerComponent)).readAllBytes();
    }

    @DisplayName("predictor - PNG None leaves the row alone")
    @Test
    public void predictorPngNone() throws IOException {
        // Each PNG row is prefixed with the filter it used; 0 is none.
        byte[] decoded = unpredict(runLength(0, 10, 20, 30), 15, 3, 1, 8);
        assertArrayEquals(new byte[]{10, 20, 30}, decoded);
    }

    @DisplayName("predictor - PNG Sub adds the pixel to its left")
    @Test
    public void predictorPngSub() throws IOException {
        // 1 is Sub: each byte is stored as its difference from the one a pixel earlier.
        byte[] decoded = unpredict(runLength(1, 10, 10, 10), 15, 3, 1, 8);
        assertArrayEquals(new byte[]{10, 20, 30}, decoded);
    }

    @DisplayName("predictor - PNG Up adds the row above")
    @Test
    public void predictorPngUp() throws IOException {
        // 2 is Up.  The first row has nothing above it, so it is taken as zeros.
        byte[] decoded = unpredict(runLength(2, 10, 20, 30, 2, 1, 1, 1), 15, 3, 1, 8);
        assertArrayEquals(new byte[]{10, 20, 30, 11, 21, 31}, decoded);
    }

    @DisplayName("predictor - PNG Average uses the mean of left and above")
    @Test
    public void predictorPngAverage() throws IOException {
        // 3 is Average: the stored byte plus floor((left + above) / 2).
        byte[] decoded = unpredict(runLength(0, 10, 20, 30, 3, 0, 0, 0), 15, 3, 1, 8);
        assertEquals(6, decoded.length);
        assertEquals(5, decoded[3]);        // (0 + 10) / 2
    }

    @DisplayName("predictor - PNG Paeth picks the nearest of left, above and above-left")
    @Test
    public void predictorPngPaeth() throws IOException {
        // 4 is Paeth; with a zero row above it degenerates to Sub.
        byte[] decoded = unpredict(runLength(0, 0, 0, 0, 4, 10, 10, 10), 15, 3, 1, 8);
        assertEquals(6, decoded.length);
        assertArrayEquals(new byte[]{10, 20, 30},
                new byte[]{decoded[3], decoded[4], decoded[5]});
    }

    @DisplayName("predictor - rows of several colour components step a whole pixel back")
    @Test
    public void predictorMultipleComponents() throws IOException {
        // With three components a Sub row adds the byte three earlier, not the one before it, so
        // the channels stay separate; a wrong stride smears colour across the row.
        byte[] decoded = unpredict(runLength(1, 10, 20, 30, 1, 2, 3), 15, 2, 3, 8);
        assertArrayEquals(new byte[]{10, 20, 30, 11, 22, 33}, decoded);
    }

    @DisplayName("predictor - the PNG predictors are recognised")
    @Test
    public void isPredictor() {
        for (int predictor : new int[]{PredictorDecode.PREDICTOR_PNG_NONE,
                PredictorDecode.PREDICTOR_PNG_SUB, PredictorDecode.PREDICTOR_PNG_UP,
                PredictorDecode.PREDICTOR_PNG_AVG, PredictorDecode.PREDICTOR_PNG_PAETH,
                PredictorDecode.PREDICTOR_PNG_OPTIMUM}) {
            assertTrue(PredictorDecode.isPredictor(new Library(),
                    predictorParams(predictor, 3, 1, 8)), "predictor " + predictor);
        }
    }

    @DisplayName("predictor - the TIFF predictor is not claimed, because it is not implemented")
    @Test
    public void tiffPredictorIsNotClaimed() {
        // PREDICTOR_TIFF_2 is declared but the decoder has no branch for it.  Answering true here
        // would route a TIFF-predicted stream through the PNG code, which reads the first byte of
        // every row as a filter tag and shifts the whole image.  Saying no leaves it undecoded,
        // which is wrong in a way that is at least visible.
        assertTrue(!PredictorDecode.isPredictor(new Library(),
                predictorParams(PredictorDecode.PREDICTOR_TIFF_2, 3, 1, 8)));
    }

    @DisplayName("predictor - a stream with no predictor is not treated as predicted")
    @Test
    public void isNotPredictor() {
        // Reading a plain stream through a predictor would consume the first byte of every row
        // as a filter tag and shift everything after it.
        assertTrue(!PredictorDecode.isPredictor(new Library(), new DictionaryEntries()));
        assertTrue(!PredictorDecode.isPredictor(new Library(),
                predictorParams(PredictorDecode.PREDICTOR_NONE, 3, 1, 8)));
    }
}
