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
package org.icepdf.core.util.updater.writeables.image;

import io.github.pcorless.jpx.Jpeg2000Encoder;
import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGQTable;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-697: a burned image that arrived as a JPEG or a JPEG 2000 goes back the same way, not as Flate at several
 * times the size; black-and-white content, which both do badly, still goes to Flate.
 */
public class LossyReencodingTest {

    private final Library library = new Library();

    @AfterEach
    void clearProperties() {
        System.clearProperty(DctEncoder.QUALITY_PROPERTY);
        System.clearProperty(JpxEncoder.QUALITY_PROPERTY);
    }

    private Resources resources() {
        return new Resources(library, new DictionaryEntries());
    }

    private static GraphicsState graphicsState() {
        return new GraphicsState(new Shapes());
    }

    /** A photograph-like picture: a gradient with a coloured block, which neither codec keeps exactly. */
    private static BufferedImage picture(int type, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D g = image.createGraphics();
        g.setPaint(new java.awt.GradientPaint(0, 0, Color.ORANGE, width, height, Color.CYAN));
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLUE);
        g.fillRect(width / 4, height / 4, width / 2, height / 2);
        g.dispose();
        return image;
    }

    private static byte[] jpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static byte[] jpx(BufferedImage image, boolean lossless) throws IOException {
        boolean gray = image.getType() == BufferedImage.TYPE_BYTE_GRAY;
        int n = gray ? 1 : 3;
        byte[] samples = new byte[image.getWidth() * image.getHeight() * n];
        for (int y = 0, i = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                if (gray) {
                    samples[i++] = (byte) image.getRaster().getSample(x, y, 0);
                } else {
                    samples[i++] = (byte) (rgb >> 16);
                    samples[i++] = (byte) (rgb >> 8);
                    samples[i++] = (byte) rgb;
                }
            }
        }
        Jpeg2000Encoder.Options options = new Jpeg2000Encoder.Options();
        if (lossless) {
            options.lossless();
        } else {
            options.quality(0.8f);
        }
        return Jpeg2000Encoder.encode(samples, image.getWidth(), image.getHeight(), n, options);
    }

    private ImageStream stream(byte[] encoded, String filter, BufferedImage like) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(Dictionary.TYPE_KEY, new Name("XObject"));
        entries.put(Dictionary.SUBTYPE_KEY, new Name("Image"));
        entries.put(new Name("Width"), like.getWidth());
        entries.put(new Name("Height"), like.getHeight());
        entries.put(new Name("BitsPerComponent"), 8);
        entries.put(new Name("ColorSpace"),
                new Name(like.getType() == BufferedImage.TYPE_BYTE_GRAY ? "DeviceGray" : "DeviceRGB"));
        entries.put(new Name("Filter"), new Name(filter));
        entries.put(Dictionary.LENGTH_KEY, encoded.length);
        return new ImageStream(library, entries, ByteBuffer.wrap(encoded));
    }

    /** Decodes the stream and burns a black block into it, as a redaction does, ready to be written. */
    private ImageStream burned(ImageStream stream) {
        BufferedImage decoded = stream.getImage(graphicsState(), resources());
        assertNotNull(decoded, "the fixture should decode");
        // grey when the image is, as ImageBurner keeps a DeviceGray image
        boolean gray = "DeviceGray".equals(String.valueOf(stream.getEntries().get(new Name("ColorSpace"))));
        BufferedImage target = new BufferedImage(decoded.getWidth(), decoded.getHeight(),
                gray ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.drawImage(decoded, 0, 0, null);
        g.setColor(Color.BLACK);
        g.fillRect(4, 4, 16, 8);
        g.dispose();
        stream.setDecodedImage(target);
        return stream;
    }

    private BufferedImage decodeAgain(ImageStream encoded) {
        BufferedImage back = encoded.getImage(graphicsState(), resources());
        assertNotNull(back, "the re-encoded image has to decode again");
        return back;
    }

    private static void assertNear(int expected, int actual, int tolerance, String what) {
        for (int shift : new int[]{16, 8, 0}) {
            int e = (expected >> shift) & 0xFF;
            int a = (actual >> shift) & 0xFF;
            assertTrue(Math.abs(e - a) <= tolerance, what + ": expected near " + Integer.toHexString(expected)
                    + ", was " + Integer.toHexString(actual));
        }
    }

    @DisplayName("a burned colour JPEG goes back as a colour JPEG, nothing left of the old encoding's parameters")
    @Test
    public void jpegStaysJpeg() throws Exception {
        BufferedImage source = picture(BufferedImage.TYPE_INT_RGB, 64, 48);
        ImageStream stream = burned(stream(jpeg(source, 0.75f), "DCTDecode", source));
        stream.getEntries().put(new Name("DecodeParms"), new DictionaryEntries());
        ImageEncoder encoder = ImageEncoderFactory.createEncodedImage(stream);
        assertInstanceOf(DctEncoder.class, encoder);
        assertFalse(ImageEncoderFactory.changesFilter(stream), "a JPEG kept as a JPEG doesn't change filter");
        ImageStream encoded = encoder.encode();
        assertEquals("DCTDecode", encoded.getEntries().get(new Name("Filter")).toString());
        assertEquals("DeviceRGB", encoded.getEntries().get(new Name("ColorSpace")).toString());
        assertNull(encoded.getEntries().get(new Name("DecodeParms")), "the old decode parameters went");
        BufferedImage back = decodeAgain(encoded);
        assertEquals(64, back.getWidth());
        assertNear(0x000000, back.getRGB(10, 8), 12, "the burned block");
        assertNear(Color.BLUE.getRGB(), back.getRGB(30, 20), 24, "the untouched blue");
    }

    @DisplayName("a JPEG is written back with the original's quantisation tables, so at its quality and size")
    @Test
    public void jpegKeepsItsQuality() throws Exception {
        BufferedImage source = picture(BufferedImage.TYPE_INT_RGB, 64, 48);
        byte[] coarse = jpeg(source, 0.3f);
        ImageStream encoded = ImageEncoderFactory.createEncodedImage(
                burned(stream(coarse, "DCTDecode", source))).encode();
        JPEGQTable[] before = DctEncoder.quantisationTables(coarse, 3);
        JPEGQTable[] after = DctEncoder.quantisationTables(encoded.getRawBytes(), 3);
        assertNotNull(after);
        for (int i = 0; i < 2; i++) {
            assertArrayEquals(before[i].getTable(), after[i].getTable(), "table " + i);
        }
        // a fixed quality would have written it at about twice the size
        byte[] fine = jpeg(source, 0.9f);
        assertTrue(encoded.getRawBytes().length < fine.length,
                encoded.getRawBytes().length + " bytes, against " + fine.length + " at quality 0.9");
    }

    @DisplayName("the JPEG quality property overrides the original's tables")
    @Test
    public void jpegQualityProperty() throws Exception {
        BufferedImage source = picture(BufferedImage.TYPE_INT_RGB, 64, 48);
        byte[] original = jpeg(source, 0.75f);
        System.setProperty(DctEncoder.QUALITY_PROPERTY, "0.2");
        ImageStream encoded = ImageEncoderFactory.createEncodedImage(
                burned(stream(original, "DCTDecode", source))).encode();
        JPEGQTable[] after = DctEncoder.quantisationTables(encoded.getRawBytes(), 3);
        assertFalse(java.util.Arrays.equals(DctEncoder.quantisationTables(original, 3)[0].getTable(),
                after[0].getTable()), "a coarser table than the original's");
    }

    @DisplayName("a burned grey JPEG goes back as a grey JPEG")
    @Test
    public void greyJpegStaysGrey() throws Exception {
        BufferedImage source = picture(BufferedImage.TYPE_BYTE_GRAY, 64, 48);
        ImageStream encoded = ImageEncoderFactory.createEncodedImage(
                burned(stream(jpeg(source, 0.75f), "DCTDecode", source))).encode();
        assertEquals("DCTDecode", encoded.getEntries().get(new Name("Filter")).toString());
        assertEquals("DeviceGray", encoded.getEntries().get(new Name("ColorSpace")).toString());
        assertEquals(64, decodeAgain(encoded).getWidth());
    }

    @DisplayName("a burned JPEG 2000 goes back as a JPEG 2000 codestream that decodes")
    @Test
    public void jpxStaysJpx() throws Exception {
        BufferedImage source = picture(BufferedImage.TYPE_INT_RGB, 64, 48);
        ImageStream stream = burned(stream(jpx(source, false), "JPXDecode", source));
        stream.getEntries().put(new Name("SMaskInData"), 1);
        ImageEncoder encoder = ImageEncoderFactory.createEncodedImage(stream);
        assertInstanceOf(JpxEncoder.class, encoder);
        assertFalse(ImageEncoderFactory.changesFilter(stream));
        ImageStream encoded = encoder.encode();
        assertEquals("JPXDecode", encoded.getEntries().get(new Name("Filter")).toString());
        assertNull(encoded.getEntries().get(new Name("SMaskInData")), "no alpha was written");
        BufferedImage back = decodeAgain(encoded);
        assertEquals(64, back.getWidth());
        assertEquals(48, back.getHeight());
        assertNear(0x000000, back.getRGB(10, 8), 12, "the burned block");
        assertNear(Color.BLUE.getRGB(), back.getRGB(30, 20), 16, "the untouched blue");
    }

    @DisplayName("JPEG 2000 at quality 1 is lossless")
    @Test
    public void jpxLosslessProperty() throws Exception {
        System.setProperty(JpxEncoder.QUALITY_PROPERTY, "1");
        BufferedImage source = picture(BufferedImage.TYPE_INT_RGB, 64, 48);
        ImageStream stream = burned(stream(jpx(source, false), "JPXDecode", source));
        BufferedImage burned = stream.getDecodedImage();
        BufferedImage back = decodeAgain(ImageEncoderFactory.createEncodedImage(stream).encode());
        for (int y = 0; y < 48; y += 3) {
            for (int x = 0; x < 64; x += 3) {
                assertEquals(burned.getRGB(x, y), back.getRGB(x, y), "pixel " + x + "," + y);
            }
        }
    }

    @DisplayName("black-and-white content goes to Flate, whatever it arrived as")
    @Test
    public void bilevelGoesToFlate() throws Exception {
        BufferedImage text = new BufferedImage(64, 48, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = text.getRaster();
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 64; x++) {
                raster.setSample(x, y, 0, (x / 3 + y / 5) % 2 == 0 ? 0 : 255);
            }
        }
        // lossless, so it decodes to exactly two values
        ImageStream stream = burned(stream(jpx(text, true), "JPXDecode", text));
        assertInstanceOf(PredictorEncoder.class, ImageEncoderFactory.createEncodedImage(stream));
    }
}
