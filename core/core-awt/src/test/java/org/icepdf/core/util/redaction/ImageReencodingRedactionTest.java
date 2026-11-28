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
package org.icepdf.core.util.redaction;

import io.github.pcorless.jpx.Jpeg2000Encoder;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.WriteMode;
import org.icepdf.core.util.updater.writeables.image.FaxEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-697: burning a redaction into a JPEG or JPEG 2000 page image writes it back the way it came, at about its
 * size, and an image with a higher-resolution {@code /Mask} is burned at its own resolution with its mask kept.
 * Built here rather than by the fixture script: the JPEG and JPEG 2000 bytes come from ImageIO and jpx-imageio.
 */
public class ImageReencodingRedactionTest {

    /** The page is 200 x 100; the image fills it. */
    private static final int PAGE_W = 200;
    private static final int PAGE_H = 100;

    /** A one-page PDF drawing image object 5 over the whole page, with {@code extra} objects from 6 on. */
    private static byte[] page(byte[] imageDict, byte[] imageData, Map<Integer, byte[][]> extra) throws IOException {
        Map<Integer, byte[]> objects = new LinkedHashMap<>();
        objects.put(1, ascii("<< /Type /Catalog /Pages 2 0 R >>"));
        objects.put(2, ascii("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"));
        objects.put(3, ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + PAGE_W + " " + PAGE_H
                + "] /Contents 4 0 R /Resources << /XObject << /Im0 5 0 R >> >> >>"));
        objects.put(4, stream(ascii(""), ascii("q " + PAGE_W + " 0 0 " + PAGE_H + " 0 0 cm /Im0 Do Q\n")));
        objects.put(5, stream(imageDict, imageData));
        for (Map.Entry<Integer, byte[][]> e : extra.entrySet()) {
            objects.put(e.getKey(), stream(e.getValue()[0], e.getValue()[1]));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(ascii("%PDF-1.7\n"));
        List<Integer> offsets = new ArrayList<>();
        for (Map.Entry<Integer, byte[]> e : objects.entrySet()) {
            offsets.add(out.size());
            out.write(ascii(e.getKey() + " 0 obj\n"));
            out.write(e.getValue());
            out.write(ascii("\nendobj\n"));
        }
        int xref = out.size();
        out.write(ascii("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n"));
        for (int offset : offsets) {
            out.write(ascii(String.format("%010d 00000 n \n", offset)));
        }
        out.write(ascii("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref
                + "\n%%EOF\n"));
        return out.toByteArray();
    }

    private static byte[] stream(byte[] dictEntries, byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(ascii("<< "));
        out.write(dictEntries);
        out.write(ascii(" /Length " + data.length + " >>\nstream\n"));
        out.write(data);
        out.write(ascii("\nendstream"));
        return out.toByteArray();
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static BufferedImage picture(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setPaint(new GradientPaint(0, 0, Color.ORANGE, width, height, Color.CYAN));
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    private static byte[] samples(BufferedImage image) {
        byte[] samples = new byte[image.getWidth() * image.getHeight() * 3];
        for (int y = 0, i = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                samples[i++] = (byte) (rgb >> 16);
                samples[i++] = (byte) (rgb >> 8);
                samples[i++] = (byte) rgb;
            }
        }
        return samples;
    }

    /** Redacts the left quarter of the page (user space x 10..50, y 10..90) and saves. */
    private static byte[] redactAndSave(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setByteArray(pdf, 0, pdf.length, "in.pdf");
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            page.addAnnotation(RedactionFixtures.redactionOver(document, new Rectangle(10, 10, 40, 80)));
            RedactionReport report = Redactor.redact(document, RedactionRequest.ofAnnotations());
            assertEquals(1, report.getImagesBurned(), report + " " + report.getWarnings());
            assertEquals(0, report.getImagesReEncoded(), "the filter is kept");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            return out.toByteArray();
        } finally {
            document.dispose();
        }
    }

    private static BufferedImage render(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setByteArray(pdf, 0, pdf.length, "x.pdf");
        try {
            return (BufferedImage) document.getPageImage(0, GraphicsRenderingHints.PRINT, Page.BOUNDARY_CROPBOX,
                    0f, 1f);
        } finally {
            document.dispose();
        }
    }

    private static ImageStream image(Document document, int number) {
        Library library = document.getCatalog().getLibrary();
        return (ImageStream) library.getObject(new Reference(number, 0));
    }

    /** Pixels of the page right of the redaction (x 60 on) differ by at most {@code tolerance} a channel. */
    private static void assertSameRightOfTheBox(BufferedImage before, BufferedImage after, int tolerance) {
        for (int y = 5; y < before.getHeight() - 5; y += 3) {
            for (int x = 60; x < before.getWidth() - 5; x += 3) {
                int p = before.getRGB(x, y);
                int q = after.getRGB(x, y);
                for (int shift = 0; shift < 24; shift += 8) {
                    int d = Math.abs(((p >> shift) & 255) - ((q >> shift) & 255));
                    assertTrue(d <= tolerance, "pixel " + x + "," + y + ": " + Integer.toHexString(p) + " became "
                            + Integer.toHexString(q));
                }
            }
        }
    }

    @DisplayName("a JPEG page image is burned and written back as a JPEG at about its size")
    @Test
    public void jpegPage() throws Exception {
        BufferedImage source = picture(400, 200);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(source, "jpg", jpeg);
        byte[] pdf = page(ascii("/Type /XObject /Subtype /Image /Width 400 /Height 200 /ColorSpace /DeviceRGB "
                + "/BitsPerComponent 8 /Filter /DCTDecode"), jpeg.toByteArray(), Map.of());
        byte[] saved = redactAndSave(pdf);
        assertTrue(saved.length < pdf.length * 3 / 2, saved.length + " bytes from " + pdf.length);
        Document document = new Document();
        document.setByteArray(saved, 0, saved.length, "out.pdf");
        try {
            ImageStream image = image(document, 5);
            assertEquals("DCTDecode", String.valueOf(image.getEntries().get(new Name("Filter"))));
        } finally {
            document.dispose();
        }
        BufferedImage after = render(saved);
        assertSameRightOfTheBox(render(pdf), after, 16);
        assertEquals(0, after.getRGB(30, 50) & 0xFFFFFF, "the burned area is black");
    }

    @DisplayName("a JPEG 2000 with a finer stencil /Mask is burned at its own size, its mask kept")
    @Test
    public void jpxWithAFinerMask() throws Exception {
        // base 50 x 25, mask 200 x 100: stripes, three pixels painted in every four columns
        BufferedImage base = picture(50, 25);
        byte[] codestream = Jpeg2000Encoder.encode(samples(base), 50, 25, 3,
                new Jpeg2000Encoder.Options().lossless());
        int maskW = 200;
        int maskH = 100;
        // CCITT, as a scan's mask is (or JBIG2): it decodes to a black-and-white image with no alpha, which is
        // what the stencil encoder misread as "paint everywhere"
        BufferedImage stripes = new BufferedImage(maskW, maskH, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = stripes.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, maskW, maskH);
        g.setColor(Color.BLACK);
        for (int x = 0; x < maskW; x += 4) {
            g.fillRect(x, 0, 3, maskH);
        }
        g.dispose();
        DictionaryEntries faxEntries = new DictionaryEntries();
        faxEntries.put(new Name("Width"), maskW);
        faxEntries.put(new Name("Height"), maskH);
        ImageStream fax = new ImageStream(new Library(), faxEntries, ByteBuffer.wrap(new byte[0]));
        fax.setDecodedImage(stripes);
        new FaxEncoder(fax).encode();
        Map<Integer, byte[][]> extra = Map.of(6, new byte[][]{ascii("/Type /XObject /Subtype /Image /Width " + maskW
                + " /Height " + maskH + " /ImageMask true /BitsPerComponent 1 /Filter /CCITTFaxDecode "
                + "/DecodeParms << /K -1 /BlackIs1 true /Columns " + maskW + " /Rows " + maskH + " >>"),
                fax.getRawBytes()});
        byte[] pdf = page(ascii("/Type /XObject /Subtype /Image /Width 50 /Height 25 /ColorSpace /DeviceRGB "
                + "/BitsPerComponent 8 /Filter /JPXDecode /Mask 6 0 R"), codestream, extra);
        byte[] saved = redactAndSave(pdf);
        Document document = new Document();
        document.setByteArray(saved, 0, saved.length, "out.pdf");
        try {
            ImageStream image = image(document, 5);
            assertEquals("JPXDecode", String.valueOf(image.getEntries().get(new Name("Filter"))));
            // the composite at the mask's size used to be written under the base's dictionary
            assertEquals(50, ((Number) image.getEntries().get(new Name("Width"))).intValue());
            assertEquals(25, ((Number) image.getEntries().get(new Name("Height"))).intValue());
            ImageStream maskStream = image(document, 6);
            assertEquals(maskW, ((Number) maskStream.getEntries().get(new Name("Width"))).intValue());
        } finally {
            document.dispose();
        }
        BufferedImage before = render(pdf);
        BufferedImage after = render(saved);
        // the stripes survive outside the box: a mask written as "paint everywhere" painted over them
        assertSameRightOfTheBox(before, after, 2);
        assertEquals(0, after.getRGB(30, 50) & 0xFFFFFF, "the burned area is black");
        assertEquals(0, after.getRGB(31, 50) & 0xFFFFFF, "including where the mask left the page alone");
    }
}
