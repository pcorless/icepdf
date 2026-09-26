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

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DctDecoderTest {

    /**
     * Encodes a test image in {@code format} and decodes it through the ICEpdf decoder for {@code filter}: left half
     * black, right half white, so a subsampled read can be checked for content, not just size.  A gray image is
     * declared DeviceGray, an RGB one DeviceRGB; the stream has no page resources, so a JPX colour space other
     * than gray would not resolve.
     */
    static BufferedImage decode(int width, int height, String format, String filter, boolean gray)
            throws IOException {
        BufferedImage source = new BufferedImage(width, height,
                gray ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width / 2, height);
        g.dispose();
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(source, format, jpeg), "no ImageIO writer for " + format);

        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Type"), new Name("XObject"));
        entries.put(new Name("Subtype"), new Name("Image"));
        entries.put(new Name("Width"), width);
        entries.put(new Name("Height"), height);
        entries.put(new Name("BitsPerComponent"), 8);
        entries.put(new Name("ColorSpace"), new Name(gray ? "DeviceGray" : "DeviceRGB"));
        entries.put(new Name("Filter"), new Name(filter));
        ImageStream imageStream = new ImageStream(new Library(), entries, jpeg.toByteArray());
        return filter.equals("JPXDecode")
                ? new JpxDecoder(imageStream, null).decode()
                : new DctDecoder(imageStream, null).decode();
    }

    private static BufferedImage decode(int width, int height) throws IOException {
        return decode(width, height, "jpeg", "DCTDecode", false);
    }

    @DisplayName("an image within the size limit is decoded at full size")
    @Test
    public void normalImageIsNotScaled() throws IOException {
        BufferedImage image = decode(400, 16);
        assertNotNull(image);
        assertEquals(400, image.getWidth());
        assertEquals(16, image.getHeight());
    }

    @DisplayName("a really big image is subsampled while decoding, to about the preferred size")
    @Test
    public void reallyBigImageIsSubsampled() throws IOException {
        // 12000 wide is past the 10000 px limit; read every 8th pixel, 1500 px wide.  The full raster was
        // decoded first and then scaled, which on a 12848 x 27733 page scan is 1GB per image.
        BufferedImage image = decode(12000, 16);
        assertNotNull(image);
        assertEquals(12000 / 8, image.getWidth());
        assertEquals(2, image.getHeight());
        // the content survives: black on the left, white on the right
        assertTrue((image.getRGB(10, 0) & 0xFF) < 64);
        assertTrue((image.getRGB(image.getWidth() - 10, 0) & 0xFF) > 192);
    }

    @DisplayName("the subsampling step keeps the longest edge at or above the preferred size")
    @Test
    public void subsamplingFor() {
        DctDecoder decoder = new DctDecoder(null, null);
        assertEquals(1, decoder.subsamplingFor(9000, 9000));
        assertEquals(18, decoder.subsamplingFor(12848, 27733));
        assertTrue(27733 / decoder.subsamplingFor(12848, 27733) >= AbstractImageDecoder.preferredSize);
        assertEquals(6, decoder.subsamplingFor(10001, 100));
    }

    // ------------------------------------------------------------------
    // JPEG header sniffing
    // ------------------------------------------------------------------

    /** A marker segment: FF, the marker, a two byte length that counts itself, then the body. */
    private static void segment(java.io.ByteArrayOutputStream out, int marker, byte[] body) {
        int length = body.length + 2;
        out.write(0xFF);
        out.write(marker);
        out.write(length >> 8);
        out.write(length);
        out.write(body, 0, body.length);
    }

    /** Adobe APP14 body with the given colour transform: 0 none, 1 YCbCr, 2 YCCK. */
    private static byte[] adobe(int transform) {
        return new byte[]{'A', 'd', 'o', 'b', 'e', 0, 100, 0, 0, 0, 0, (byte) transform};
    }

    /** Frame header body: precision, height, width, then an id, sampling and table per component. */
    private static byte[] frame(int components) {
        byte[] body = new byte[6 + 3 * components];
        body[0] = 8;
        body[2] = 16;
        body[4] = 16;
        body[5] = (byte) components;
        return body;
    }

    /** Scan header body: component count, a selector pair per component, then spectral/approximation bytes. */
    private static byte[] scan(int components) {
        byte[] body = new byte[1 + 2 * components + 3];
        body[0] = (byte) components;
        return body;
    }

    private static byte[] header(int appPadding, int sofMarker, int transform) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8);
        if (appPadding > 0) {
            segment(out, 0xED, new byte[appPadding]); // APP13, Photoshop resources
        }
        segment(out, 0xEE, adobe(transform));
        segment(out, sofMarker, frame(4));
        segment(out, 0xDA, scan(4));
        out.write(0x12); // entropy-coded data
        return out.toByteArray();
    }

    private static int encoding(byte[] header) {
        return new DctDecoder(null, null).getJPEGEncoding(header, header.length);
    }

    @DisplayName("an Adobe YCCK marker is found in a baseline JPEG")
    @Test
    public void ycckBaseline() {
        assertEquals(DctDecoder.JPEG_ENC_YCCK, encoding(header(0, 0xC0, 2)));
    }

    @DisplayName("an Adobe YCCK marker is found in a progressive JPEG")
    @Test
    public void ycckProgressive() {
        // only SOF0 counted as a frame header, so a progressive (SOF2) YCCK image was taken for plain CMYK
        assertEquals(DctDecoder.JPEG_ENC_YCCK, encoding(header(0, 0xC2, 2)));
    }

    @DisplayName("an Adobe marker behind a large Photoshop block is still found")
    @Test
    public void adobeMarkerBehindPhotoshopBlock() {
        // AuftPapier04.pdf: a 4KB APP13 put APP14 past the 2048 bytes the sniffing read; the YCCK logo was
        // read as CMYK, bright green with its black plate gone
        DctDecoder decoder = new DctDecoder(null, null);
        byte[] header = header(4232, 0xC2, 2);
        assertEquals(DctDecoder.JPEG_ENC_YCCK, decoder.getJPEGEncoding(header, header.length));
        assertTrue(decoder.encodingFromAdobeMarker);
    }

    @DisplayName("a header cut short doesn't throw")
    @Test
    public void truncatedHeader() {
        byte[] header = header(0, 0xC2, 2);
        for (int length = 0; length < header.length; length++) {
            encoding(java.util.Arrays.copyOf(header, length));
        }
    }

    @DisplayName("the start of frame markers are SOF0 to SOF15, less DHT, JPG and DAC")
    @Test
    public void startOfFrameMarkers() {
        for (int marker = 0xC0; marker <= 0xCF; marker++) {
            boolean expected = marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            assertEquals(expected, DctDecoder.isStartOfFrame((byte) marker), Integer.toHexString(marker));
        }
        assertEquals(false, DctDecoder.isStartOfFrame((byte) 0xDA));
    }

    @DisplayName("decoding reads an Adobe marker that sits behind a large Photoshop block")
    @Test
    public void decodeFindsAdobeMarkerBehindPhotoshopBlock() throws IOException {
        // a real JPEG with a 4KB APP13 and an Adobe APP14 (transform 1, YCbCr) spliced in after SOI
        BufferedImage source = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        ImageIO.write(source, "jpeg", plain);
        byte[] jpeg = plain.toByteArray();
        java.io.ByteArrayOutputStream spliced = new java.io.ByteArrayOutputStream();
        spliced.write(jpeg, 0, 2);
        segment(spliced, 0xED, new byte[4232]);
        segment(spliced, 0xEE, adobe(1));
        spliced.write(jpeg, 2, jpeg.length - 2);

        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Subtype"), new Name("Image"));
        entries.put(new Name("Width"), 16);
        entries.put(new Name("Height"), 16);
        entries.put(new Name("BitsPerComponent"), 8);
        entries.put(new Name("ColorSpace"), new Name("DeviceRGB"));
        entries.put(new Name("Filter"), new Name("DCTDecode"));
        DctDecoder decoder = new DctDecoder(new ImageStream(new Library(), entries, spliced.toByteArray()), null);
        assertNotNull(decoder.decode());
        assertTrue(decoder.encodingFromAdobeMarker, "APP14 past the first 2KB was not seen");
    }
}
