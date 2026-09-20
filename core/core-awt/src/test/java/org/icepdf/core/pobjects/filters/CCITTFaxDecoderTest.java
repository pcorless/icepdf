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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests ICEpdf's own CCITT fax decoder against faxes encoded by libtiff.
 * <p>
 * {@link org.icepdf.core.pobjects.graphics.images.FaxDecoder} decodes a fax with TwelveMonkeys and
 * only reaches this decoder when that one throws, so a corpus of faxes that render correctly says
 * nothing about it: the fallback is never asked.  That is an unfortunate thing to leave untested,
 * because it runs exactly when a file is already unusual.
 * <p>
 * The fixtures are encoded by libtiff through Pillow and carry their expected picture beside them as
 * a PBM, so what is compared is one implementation against another.  A fixture this decoder had
 * produced would agree with it no matter what it did.  See make_ccitt_fixtures.py.
 * <p>
 * <b>On polarity:</b> a PBM sets a bit for black.  A CCITT filter's output does the opposite - PDF
 * 32000 7.4.6 has BlackIs1 false by default, meaning 0 bits are black - so the expected raster is
 * the PBM's inverted.  {@link #fallbackAgreesWithTwelveMonkeys()} pins that down against the other
 * decoder rather than leaving it as this test's opinion.
 */
public class CCITTFaxDecoderTest {

    private static final Path FIXTURES = Path.of("src/test/resources/ccitt");

    /** A picture, as the fixture's PBM holds it. */
    private static final class Picture {
        private final int width;
        private final int height;
        private final byte[] raster;

        Picture(int width, int height, byte[] raster) {
            this.width = width;
            this.height = height;
            this.raster = raster;
        }

        int width() {
            return width;
        }

        int height() {
            return height;
        }

        byte[] raster() {
            return raster;
        }

        int rowBytes() {
            return (width + 7) >> 3;
        }

        /**
         * The raster as the decoder should write it: a PBM sets a bit for black and a CCITT filter
         * clears one, so every bit is flipped.
         */
        byte[] expected() {
            byte[] flipped = new byte[raster.length];
            for (int i = 0; i < raster.length; i++) {
                flipped[i] = (byte) ~raster[i];
            }
            return flipped;
        }
    }

    /**
     * Reads a binary PBM, whose raster is already in the decoder's output form: one bit per pixel,
     * rows padded to a byte, a set bit meaning black.
     */
    private static Picture readPbm(String name) throws IOException {
        byte[] bytes = Files.readAllBytes(FIXTURES.resolve(name + ".pbm"));
        int[] at = {0};
        String magic = pbmToken(bytes, at);
        assertEquals("P4", magic, name + " should be a binary PBM");
        int width = Integer.parseInt(pbmToken(bytes, at));
        int height = Integer.parseInt(pbmToken(bytes, at));
        // exactly one whitespace byte separates the header from the raster
        byte[] raster = new byte[bytes.length - at[0]];
        System.arraycopy(bytes, at[0], raster, 0, raster.length);
        return new Picture(width, height, raster);
    }

    private static String pbmToken(byte[] bytes, int[] at) {
        StringBuilder token = new StringBuilder();
        while (Character.isWhitespace(bytes[at[0]])) {
            at[0]++;
        }
        while (at[0] < bytes.length && !Character.isWhitespace(bytes[at[0]])) {
            token.append((char) bytes[at[0]++]);
        }
        at[0]++;
        return token.toString();
    }

    private static byte[] encoded(String name, String encoding) throws IOException {
        return Files.readAllBytes(FIXTURES.resolve(name + "." + encoding + ".ccitt"));
    }

    /**
     * Renders a bitmap as text, so a failure says what the picture looked like rather than which
     * byte of several hundred first differed.
     */
    private static String render(byte[] raster, int width, int height) {
        StringBuilder out = new StringBuilder("\n");
        int rowBytes = (width + 7) >> 3;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * rowBytes + (x >> 3);
                boolean black = index < raster.length
                        && (raster[index] & (0x80 >> (x & 7))) == 0;
                out.append(black ? '#' : '.');
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static boolean isBlack(byte[] raster, int rowBytes, int x, int y) {
        int index = y * rowBytes + (x >> 3);
        return index < raster.length && (raster[index] & (0x80 >> (x & 7))) == 0;
    }

    /**
     * Compares the pixels the image actually has, ignoring the spare bits that pad the last byte of
     * each row.
     */
    private static void assertSamePicture(Picture expected, byte[] actual, String what) {
        byte[] want = expected.expected();
        int rowBytes = expected.rowBytes();
        for (int y = 0; y < expected.height(); y++) {
            for (int x = 0; x < expected.width(); x++) {
                if (isBlack(want, rowBytes, x, y) != isBlack(actual, rowBytes, x, y)) {
                    throw new AssertionError(what + ": first differs at " + x + "," + y
                            + "\nexpected:" + render(want, expected.width(), expected.height())
                            + "\nactual:" + render(actual, expected.width(), expected.height()));
                }
            }
        }
    }

    /**
     * Decodes with ICEpdf's decoder, the way FaxDecoder reaches it.
     */
    private static byte[] decode(String name, String encoding, int k, boolean align) throws IOException {
        Picture picture = readPbm(name);
        byte[] out = new byte[picture.height() * picture.rowBytes()];
        CCITTFaxDecoder decoder = new CCITTFaxDecoder(1, picture.width(), picture.height());
        decoder.setAlign(align);
        byte[] data = encoded(name, encoding);
        if (k < 0) {
            decoder.decodeT6(out, data, 0, picture.height());
        } else if (k == 0) {
            decoder.decodeT41D(out, data, 0, picture.height());
        } else {
            decoder.decodeT42D(out, data, 0, picture.height());
        }
        return out;
    }

    private void roundTrip(String name, String encoding, int k, boolean align) throws IOException {
        Picture expected = readPbm(name);
        byte[] actual = decode(name, encoding, k, align);
        assertSamePicture(expected, actual, name + " decoded as " + encoding);
    }

    /** Every fixture picture, by name. */
    private static final String[] PICTURES =
            {"checker", "stripes", "vertical", "blank", "solid", "dot", "narrow", "fax1728"};

    // ------------------------------------------------------------------
    // Group 4 (T.6), K < 0, much the commonest fax in a PDF
    // ------------------------------------------------------------------

    @DisplayName("G4 decodes to the picture that was encoded")
    @ParameterizedTest(name = "G4: {0}")
    @ValueSource(strings = {"checker", "stripes", "vertical", "blank", "solid", "dot", "narrow", "fax1728"})
    public void group4(String picture) throws Exception {
        roundTrip(picture, "g4", -1, false);
    }

    // ------------------------------------------------------------------
    // Group 3 (T.4), one dimensional, K = 0
    // ------------------------------------------------------------------

    @DisplayName("G3 one dimensional decodes to the picture that was encoded")
    @ParameterizedTest(name = "G3 1D: {0}")
    @ValueSource(strings = {"checker", "stripes", "vertical", "blank", "solid", "dot", "narrow", "fax1728"})
    public void group3OneDimensional(String picture) throws Exception {
        roundTrip(picture, "g3_1d", 0, false);
    }

    // ------------------------------------------------------------------
    // Group 3 (T.4), mixed, K > 0: lines may be coded against the one above
    // ------------------------------------------------------------------

    @DisplayName("G3 two dimensional decodes to the picture that was encoded")
    @ParameterizedTest(name = "G3 2D: {0}")
    @ValueSource(strings = {"checker", "stripes", "vertical", "blank", "solid", "dot", "narrow", "fax1728"})
    public void group3TwoDimensional(String picture) throws Exception {
        roundTrip(picture, "g3_2d", 1, false);
    }

    // ------------------------------------------------------------------
    // fill bits
    // ------------------------------------------------------------------

    @DisplayName("a fax padded with fill bits before each EOL decodes to its picture")
    @ParameterizedTest(name = "fill, K=0: {0}")
    @ValueSource(strings = {"checker", "stripes", "vertical", "blank", "solid", "dot", "narrow", "fax1728"})
    public void fillBitsOneDimensional(String picture) throws Exception {
        // T.4 lets an encoder put zero bits before an EOL so the code word ends on a byte boundary.
        // They are part of the bitstream and are consumed by reading past them, which is why these
        // decode with setAlign(false) - /EncodedByteAlign is a different thing, see below.
        roundTrip(picture, "g3_1d_fill", 0, false);
    }

    @DisplayName("a two dimensional fax padded with fill bits decodes to its picture")
    @ParameterizedTest(name = "fill, K>0: {0}")
    @ValueSource(strings = {"checker", "stripes", "vertical", "blank", "solid", "dot", "narrow", "fax1728"})
    public void fillBitsTwoDimensional(String picture) throws Exception {
        roundTrip(picture, "g3_2d_fill", 1, false);
    }

    @DisplayName("a one dimensional fax with fill bits is not decoded as a solid black page")
    @Test
    public void fillBitsAreNotDecodedAsImage() throws Exception {
        // The bug this pins: decodeT41D consumed a fixed twelve bits looking for an EOL, so any fill
        // before it was left in place and decoded as image data.  A run of zeros decodes to black,
        // which turned an all white fax into an entirely black page - the worst way to be wrong,
        // since it hides the scan completely rather than blemishing it.
        byte[] decoded = decode("blank", "g3_1d_fill", 0, false);
        for (byte b : decoded) {
            assertEquals((byte) 0xff, b,
                    "every bit of an all white fax should be white, was " + Integer.toBinaryString(b & 0xff));
        }
    }

    // ------------------------------------------------------------------
    // the fallback against the decoder it stands in for
    // ------------------------------------------------------------------

    /**
     * Decodes with TwelveMonkeys, which is what FaxDecoder reaches for first.  Loaded reflectively
     * for the same reason FaxDecoder loads it that way: the class is not part of the published API.
     */
    private static byte[] twelveMonkeys(byte[] data, int width, int size, int compression,
                                        long options, boolean align) throws Exception {
        Class<?> decoderClass =
                Class.forName("com.twelvemonkeys.imageio.plugins.tiff.CCITTFaxDecoderStream");
        Constructor<?> constructor = decoderClass.getConstructor(
                InputStream.class, int.class, int.class, long.class, boolean.class);
        constructor.setAccessible(true);
        InputStream stream = (InputStream) constructor.newInstance(
                new ByteArrayInputStream(data), width, compression, options, align);
        byte[] out = new byte[size];
        new DataInputStream(stream).readFully(out);
        return out;
    }

    @DisplayName("the fallback decoder produces exactly what TwelveMonkeys produces")
    @ParameterizedTest(name = "fallback matches TwelveMonkeys: {0}")
    @ValueSource(strings = {"checker", "stripes", "vertical", "blank", "solid", "dot", "narrow", "fax1728"})
    public void fallbackMatchesTwelveMonkeys(String picture) throws Exception {
        // FaxDecoder swaps one decoder for the other without telling anything downstream, so the two
        // have to agree byte for byte - padding bits included.  This is also what fixes the polarity
        // the rest of the class asserts: whichever way round these two put black, they put it the
        // same way, and a change to either shows up here rather than as a page of inverted scan.
        Picture expected = readPbm(picture);
        int size = expected.height() * expected.rowBytes();
        byte[] data = encoded(picture, "g4");

        byte[] theirs = twelveMonkeys(data, expected.width(), size, 4, 0L, false);
        byte[] ours = decode(picture, "g4", -1, false);
        assertArrayEquals(theirs, ours,
                picture + ": the fallback decoder disagrees with the one it stands in for");
    }

    // ------------------------------------------------------------------
    // controls
    // ------------------------------------------------------------------

    @DisplayName("the comparison notices when the wrong picture is decoded")
    @Test
    public void theComparisonBites() throws Exception {
        // Without this the tests above would pass just as happily against a decoder that returned
        // the same thing every time, or against fixtures that all held the same picture.
        Picture blank = readPbm("blank");
        byte[] dot = decode("dot", "g4", -1, false);
        assertThrows(AssertionError.class,
                () -> assertSamePicture(blank, dot, "a blank page is not a page with a dot"),
                "comparing different pictures should fail");
    }

    @DisplayName("the fixtures hold different pictures from one another")
    @Test
    public void theFixturesDiffer() throws Exception {
        // A generator bug that wrote one picture under every name would leave every test above
        // passing, having proved nothing at all.
        for (int i = 0; i < PICTURES.length; i++) {
            for (int j = i + 1; j < PICTURES.length; j++) {
                Picture one = readPbm(PICTURES[i]);
                Picture two = readPbm(PICTURES[j]);
                assertTrue(one.width() != two.width()
                                || one.height() != two.height()
                                || !Arrays.equals(one.raster(), two.raster()),
                        PICTURES[i] + " and " + PICTURES[j] + " are the same picture");
            }
        }
    }
}
