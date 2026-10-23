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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.graphics.images.references.ImagePool;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-625: a big image drawn small is decoded reduced: a box filter of the full decode, which is
 * kept apart from the full image in the pool and gives way to it when the image is drawn large.
 */
class ReducedDecodeTest {

    private static final Reference IMAGE = new Reference(5, 0);
    private static final int PAGE = 200;

    private final List<Document> documents = new ArrayList<>();

    @AfterEach
    void tearDown() {
        System.clearProperty(ReducedDecode.THRESHOLD_PROPERTY);
        System.clearProperty(ReducedDecode.ENABLED_PROPERTY);
        documents.forEach(Document::dispose);
    }

    @DisplayName("the reduction leaves at least two source pixels per device pixel, as a power of two")
    @Test
    void factor() {
        assertEquals(1, ReducedDecode.factorFor(1000, 1000, 1000, 1000));
        assertEquals(1, ReducedDecode.factorFor(1000, 1000, 300, 300));
        assertEquals(2, ReducedDecode.factorFor(1000, 1000, 250, 250));
        assertEquals(4, ReducedDecode.factorFor(1000, 1000, 100, 100));
        assertEquals(2, ReducedDecode.factorFor(1000, 1000, 100, 250), "the less reduced direction rules");
        assertEquals(ReducedDecode.MAX_FACTOR, ReducedDecode.factorFor(100000, 100000, 1, 1));
        assertEquals(1, ReducedDecode.factorFor(1000, 1000, 0, 0));
    }

    @DisplayName("DeviceRGB 8: the reduced decode is the full decode box filtered")
    @Test
    void rgb() throws Exception {
        assertBoxFiltered("/DeviceRGB", 8, 3, null);
    }

    @DisplayName("DeviceGray 8, with an inverting decode array")
    @Test
    void grayInverted() throws Exception {
        assertBoxFiltered("/DeviceGray /Decode [1 0]", 8, 1, null);
    }

    @DisplayName("DeviceGray 1 bit, rows padded to a byte")
    @Test
    void grayOneBit() throws Exception {
        assertBoxFiltered("/DeviceGray", 1, 1, null);
    }

    @DisplayName("Indexed: the palette colours are averaged, not the indices")
    @Test
    void indexed() throws Exception {
        assertBoxFiltered("[/Indexed /DeviceRGB 3 <FF000000FF000000FFFFFFFF>]", 8, 1, 4);
    }

    @DisplayName("DeviceCMYK: through the same per-pixel conversion as the full decode")
    @Test
    void cmyk() throws Exception {
        assertBoxFiltered("/DeviceCMYK", 8, 4, null);
    }

    @DisplayName("16 bit RGB goes through the per-pixel conversion too")
    @Test
    void rgbSixteenBit() throws Exception {
        assertBoxFiltered("/DeviceRGB", 16, 3, null);
    }

    @DisplayName("only big, unmasked images are candidates")
    @Test
    void candidates() throws Exception {
        Document big = document(imagePdf("/DeviceRGB", 8, 3, 2100, 2000, null, ""));
        assertTrue(ReducedDecode.isCandidate(image(big)), "2100x2000 is over 16MB decoded");
        Document small = document(imagePdf("/DeviceRGB", 8, 3, 300, 300, null, ""));
        assertFalse(ReducedDecode.isCandidate(image(small)));
        Document masked = document(imagePdf("/DeviceRGB", 8, 3, 2100, 2000, null, " /SMask 5 0 R"));
        assertFalse(ReducedDecode.isCandidate(image(masked)), "a masked image decodes in full");
        System.setProperty(ReducedDecode.ENABLED_PROPERTY, "false");
        assertFalse(ReducedDecode.isCandidate(image(big)));
    }

    @DisplayName("drawn small, the page keeps only a reduced image; drawn large, the full image replaces it")
    @Test
    void drawnSmallThenLarge() throws Exception {
        System.setProperty(ReducedDecode.THRESHOLD_PROPERTY, "1");
        Document document = document(imagePdf("/DeviceRGB", 8, 3, 1024, 1024, null, ""));
        ImagePool pool = document.getCatalog().getLibrary().getImagePool();

        paint(document, 0.25f);
        BufferedImage reduced = pool.getReduced(IMAGE);
        assertNotNull(reduced, "drawn at 50px: a reduced decode");
        assertEquals(128, reduced.getWidth(), "1024 / 8: still two source pixels per device pixel");
        assertNull(pool.get(IMAGE), "and no full decode");

        paint(document, 2f);
        assertNotNull(pool.get(IMAGE), "drawn at 400px the image is decoded in full");
        assertNull(pool.getReduced(IMAGE), "which replaces the reduced one");

        paint(document, 0.25f);
        assertNull(pool.getReduced(IMAGE), "the full image serves the small drawing too");
    }

    @DisplayName("drawn small, reduced and full decodes paint the same page within a level or two")
    @Test
    void samePage() throws Exception {
        assertSamePage(imagePdf("/DeviceRGB", 8, 3, 1024, 1024, null, ""));
    }

    @DisplayName("a gray JPEG drawn small is no lighter reduced: its colour comes from drawing it, not getRGB")
    @Test
    void grayJpegSamePage() throws Exception {
        BufferedImage source = new BufferedImage(800, 800, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = source.createGraphics();
        for (int x = 0; x < 800; x++) {
            g.setColor(new java.awt.Color(x * 255 / 799, x * 255 / 799, x * 255 / 799));
            g.drawLine(x, 0, x, 799);
        }
        g.dispose();
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(source, "jpeg", jpeg);
        assertSamePage(pdf("/Width 800 /Height 800 /ColorSpace /DeviceGray /BitsPerComponent 8 /Filter /DCTDecode",
                jpeg.toByteArray()));
    }

    private void assertSamePage(byte[] pdf) throws Exception {
        System.setProperty(ReducedDecode.THRESHOLD_PROPERTY, "1");
        Document document = document(pdf);
        BufferedImage reduced = paint(document, 0.25f);
        assertNotNull(document.getCatalog().getLibrary().getImagePool().getReduced(IMAGE), "drawn reduced");
        System.setProperty(ReducedDecode.ENABLED_PROPERTY, "false");
        BufferedImage full = paint(document(pdf), 0.25f);
        double total = 0;
        int worst = 0;
        for (int y = 0; y < full.getHeight(); y++) {
            for (int x = 0; x < full.getWidth(); x++) {
                int d = maxChannelDifference(full.getRGB(x, y), reduced.getRGB(x, y));
                total += d;
                worst = Math.max(worst, d);
            }
        }
        double mean = total / (full.getWidth() * full.getHeight());
        assertTrue(mean < 2, "mean difference " + mean);
        assertTrue(worst < 24, "worst difference " + worst);
    }

    @DisplayName("JPEG: decoded whole and box filtered, so thin lines survive")
    @Test
    void jpeg() throws Exception {
        int width = 203;
        int height = 141;
        BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(java.awt.Color.BLACK);
        for (int x = 3; x < width; x += 9) {
            g.drawLine(x, 0, x, height);
        }
        g.dispose();
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(source, "jpeg", jpeg);
        Document document = document(pdf("/Width " + width + " /Height " + height
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode", jpeg.toByteArray()));
        assertCloseToBoxFiltered(document, width, height);
    }

    @DisplayName("JPEG 2000: the reader drops two resolution levels for a reduction of 4")
    @Test
    void jpeg2000() throws Exception {
        byte[] codestream = java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(getClass().getResource("/jpx/reduce.j2k").toURI()));
        Document document = document(pdf("/Width 96 /Height 96 /ColorSpace /DeviceRGB /BitsPerComponent 8"
                + " /Filter /JPXDecode", codestream));
        Page page = document.getPageTree().getPage(0);
        page.init();
        BufferedImage reduced = image(document).getReducedImage(null, page.getResources(), 4);
        assertNotNull(reduced);
        assertEquals(24, reduced.getWidth());
        assertEquals(24, reduced.getHeight());

        // the wavelet's own low-pass (bit-exact with OpenJPEG -r 2 in jpx-imageio), not a box filter
        javax.imageio.stream.ImageInputStream in = javax.imageio.ImageIO.createImageInputStream(
                new java.io.ByteArrayInputStream(codestream));
        javax.imageio.ImageReader reader = javax.imageio.ImageIO.getImageReaders(in).next();
        reader.setInput(in);
        javax.imageio.ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceSubsampling(4, 4, 0, 0);
        java.awt.image.Raster expected = reader.readRaster(0, param);
        reader.dispose();
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                int argb = reduced.getRGB(x, y);
                int rgb = expected.getSample(x, y, 0) << 16 | expected.getSample(x, y, 1) << 8 | expected.getSample(x, y, 2);
                assertTrue(maxChannelDifference(argb, rgb) <= 1, "at " + x + "," + y);
            }
        }
    }

    /** The reduced decode by 4 is the right size, and the full decode box filtered, within rounding. */
    private void assertCloseToBoxFiltered(Document document, int width, int height) throws Exception {
        Page page = document.getPageTree().getPage(0);
        page.init();
        ImageStream image = image(document);
        BufferedImage full = image.getImage(null, page.getResources());
        BufferedImage reduced = image.getReducedImage(null, page.getResources(), 4);
        assertNotNull(reduced);
        assertEquals(ReducedDecode.reduced(width, 4), reduced.getWidth());
        assertEquals(ReducedDecode.reduced(height, 4), reduced.getHeight());
        for (int oy = 0; oy < reduced.getHeight(); oy++) {
            for (int ox = 0; ox < reduced.getWidth(); ox++) {
                int d = maxChannelDifference(boxAverage(full, ox * 4, oy * 4, 4), reduced.getRGB(ox, oy));
                assertTrue(d <= 2, "at " + ox + "," + oy + ": " + d);
            }
        }
    }

    /** The reduced decode, by 4 and by 8, against the full decode box filtered the same way. */
    private void assertBoxFiltered(String colourSpace, int bpc, int components, Integer levels) throws Exception {
        // sizes that don't divide by the factor, so the edge blocks are partial
        int width = 101;
        int height = 67;
        Document document = document(imagePdf(colourSpace, bpc, components, width, height, levels, ""));
        Page page = document.getPageTree().getPage(0);
        page.init();
        ImageStream image = image(document);
        BufferedImage full = image.getImage(null, page.getResources());
        assertNotNull(full);
        for (int factor : new int[]{4, 8}) {
            BufferedImage reduced = image.getReducedImage(null, page.getResources(), factor);
            assertNotNull(reduced, colourSpace + " by " + factor);
            assertEquals(ReducedDecode.reduced(width, factor), reduced.getWidth());
            assertEquals(ReducedDecode.reduced(height, factor), reduced.getHeight());
            for (int oy = 0; oy < reduced.getHeight(); oy++) {
                for (int ox = 0; ox < reduced.getWidth(); ox++) {
                    int expected = boxAverage(full, ox * factor, oy * factor, factor);
                    int d = maxChannelDifference(expected, reduced.getRGB(ox, oy));
                    assertTrue(d <= 2, colourSpace + " by " + factor + " at " + ox + "," + oy + ": "
                            + Integer.toHexString(expected) + " vs " + Integer.toHexString(reduced.getRGB(ox, oy)));
                }
            }
        }
    }

    private static int boxAverage(BufferedImage image, int x0, int y0, int factor) {
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int y = y0; y < Math.min(y0 + factor, image.getHeight()); y++) {
            for (int x = x0; x < Math.min(x0 + factor, image.getWidth()); x++) {
                int argb = image.getRGB(x, y);
                r += (argb >> 16) & 0xFF;
                g += (argb >> 8) & 0xFF;
                b += argb & 0xFF;
                n++;
            }
        }
        return 0xFF000000 | (int) ((r + n / 2) / n) << 16 | (int) ((g + n / 2) / n) << 8 | (int) ((b + n / 2) / n);
    }

    private static int maxChannelDifference(int a, int b) {
        int d = 0;
        for (int shift = 0; shift < 24; shift += 8) {
            d = Math.max(d, Math.abs(((a >> shift) & 0xFF) - ((b >> shift) & 0xFF)));
        }
        return d;
    }

    private BufferedImage paint(Document document, float zoom) throws Exception {
        Page page = document.getPageTree().getPage(0);
        page.init();
        int size = Math.round(PAGE * zoom);
        BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            page.paint(g, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0f, zoom);
        } finally {
            g.dispose();
        }
        return canvas;
    }

    private Document document(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setByteArray(pdf, 0, pdf.length, null);
        documents.add(document);
        return document;
    }

    private static ImageStream image(Document document) {
        return (ImageStream) document.getCatalog().getLibrary().getObject(IMAGE);
    }

    /**
     * A one page PDF drawing a {@code width x height} Flate image, object 5, over the whole page.
     * The samples are a pattern with edges, stripes and gradients, so averaging shows.
     */
    private static byte[] imagePdf(String colourSpace, int bpc, int components, int width, int height,
                                   Integer levels, String extra) throws Exception {
        int rowBytes = (width * components * bpc + 7) / 8;
        byte[] samples = new byte[rowBytes * height];
        int max = (1 << bpc) - 1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int c = 0; c < components; c++) {
                    int v;
                    if (levels != null) {
                        v = ((x / 3) + (y / 5)) % levels;
                    } else if (bpc == 1) {
                        v = ((x + y) % 3 == 0 || (x / 7) % 2 == 0) ? 1 : 0;
                    } else {
                        double t = c == 0 ? x / (double) width : c == 1 ? y / (double) height
                                : ((x * 7 + y * 3) % 17) / 16.0;
                        v = (int) Math.round(t * max);
                    }
                    put(samples, y * rowBytes, x * components + c, bpc, v);
                }
            }
        }
        ByteArrayOutputStream deflated = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(deflated, new Deflater(1))) {
            out.write(samples);
        }
        return pdf("/Width " + width + " /Height " + height + " /ColorSpace " + colourSpace
                + " /BitsPerComponent " + bpc + extra + " /Filter /FlateDecode", deflated.toByteArray());
    }

    /** A one page PDF drawing image object 5, {@code dictionary} plus {@code data}, over the whole page. */
    private static byte[] pdf(String dictionary, byte[] data) {
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + PAGE + " " + PAGE + "]"
                + " /Resources << /XObject << /Im1 5 0 R >> >> /Contents 4 0 R >>");
        String content = "q " + PAGE + " 0 0 " + PAGE + " 0 0 cm /Im1 Do Q";
        objects.add("<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream");
        objects.add("<< /Type /XObject /Subtype /Image " + dictionary + " /Length " + data.length + " >>\nstream\n"
                + new String(data, StandardCharsets.ISO_8859_1) + "\nendstream");

        StringBuilder pdf = new StringBuilder("%PDF-1.7\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) {
            pdf.append(String.format("%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static void put(byte[] row, int offset, int index, int bpc, int value) {
        if (bpc == 8) {
            row[offset + index] = (byte) value;
        } else if (bpc == 16) {
            row[offset + index * 2] = (byte) (value >> 8);
            row[offset + index * 2 + 1] = (byte) value;
        } else {
            int bit = index * bpc;
            row[offset + (bit >> 3)] |= (byte) (value << (8 - bpc - (bit & 7)));
        }
    }
}
