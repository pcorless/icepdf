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
package org.icepdf.core.util.parser.content;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.commands.DrawCmd;
import org.icepdf.core.pobjects.graphics.commands.ImageDrawCmd;
import org.icepdf.core.pobjects.graphics.commands.StencilMaskFillDrawCmd;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-632: tiny stencil masks are filled as rectangles instead of drawn as images.  Each case renders with the
 * conversion on and off and compares.
 */
public class StencilMaskFillTest {

    @TempDir
    Path temp;

    @AfterEach
    void clearProperty() {
        System.clearProperty(StencilMaskFill.ENABLED_PROPERTY);
    }

    /**
     * A 200 x 100 page drawing an 8 x 2 stencil mask scaled to 160 x 40 at (20, 30), in red.
     * Row 0 = 0x0F (left half 0-bits), row 1 = 0xF0 (right half 0-bits).
     */
    private Path page(String name, String extraImageEntries, int width, int height, byte[] samples)
            throws Exception {
        return page(name, "q 0.8 0.1 0.1 rg 160 0 0 40 20 30 cm /M1 Do Q", extraImageEntries, width, height,
                samples);
    }

    private Path page(String name, String content, String extraImageEntries, int width, int height,
                      byte[] samples) throws Exception {
        StringBuilder data = new StringBuilder();
        for (byte b : samples) data.append((char) (b & 0xFF));
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 100] /Contents 4 0 R"
                + " /Resources << /XObject << /M1 5 0 R >> >> >>");
        objects.add("<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream");
        objects.add("<< /Type /XObject /Subtype /Image /Width " + width + " /Height " + height
                + " /ImageMask true" + extraImageEntries + " /Length " + data.length() + " >>\nstream\n"
                + data + "\nendstream");
        StringBuilder out = new StringBuilder("%PDF-1.7\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.length());
            out.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = out.length();
        out.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) out.append(String.format("%010d 00000 n \n", offset));
        out.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        Path file = temp.resolve(name + ".pdf");
        Files.write(file, out.toString().getBytes(StandardCharsets.ISO_8859_1));
        return file;
    }

    private static final class Rendered {
        BufferedImage image;
        int fills;
        int images;
        int listedImages;
    }

    private Rendered render(Path file, boolean fill) throws Exception {
        System.setProperty(StencilMaskFill.ENABLED_PROPERTY, String.valueOf(fill));
        Document document = new Document();
        document.setFile(file.toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            Rendered r = new Rendered();
            for (DrawCmd cmd : page.getShapes().getShapes()) {
                if (cmd instanceof StencilMaskFillDrawCmd) r.fills++;
                if (cmd instanceof ImageDrawCmd) r.images++;
            }
            r.listedImages = page.getImages().size();
            r.image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = r.image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 200, 100);
            page.paint(g, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0f, 1f, true, false);
            g.dispose();
            return r;
        } finally {
            document.dispose();
        }
    }

    private static boolean red(BufferedImage image, int x, int y) {
        Color c = new Color(image.getRGB(x, y));
        return c.getRed() > 150 && c.getGreen() < 80 && c.getBlue() < 80;
    }

    private static boolean white(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) & 0xFFFFFF) == 0xFFFFFF;
    }

    // the mask covers x 20..180, y (page) 30..70 -> image y 30..70; row 0 is the top half (image y 30..50).
    private static void assertPattern(BufferedImage image, boolean inverted, String label) {
        // row 0 = 0x0F: left half 0-bits; row 1 = 0xF0: right half 0-bits.  0-bits paint unless inverted.
        boolean topLeft = !inverted;
        assertEquals(topLeft, red(image, 50, 40), label + ": top left");
        assertEquals(!topLeft, red(image, 150, 40), label + ": top right");
        assertEquals(!topLeft, red(image, 50, 60), label + ": bottom left");
        assertEquals(topLeft, red(image, 150, 60), label + ": bottom right");
        assertTrue(white(image, 10, 50) && white(image, 190, 50) && white(image, 100, 20) && white(image, 100, 80),
                label + ": nothing outside the mask");
    }

    @DisplayName("a tiny mask is filled, painting the same samples as the image, and is still listed")
    @Test
    void filledLikeTheImage() throws Exception {
        Path file = page("mask", "", 8, 2, new byte[]{0x0F, (byte) 0xF0});
        Rendered image = render(file, false);
        Rendered fill = render(file, true);
        assertEquals(1, image.images);
        assertEquals(0, image.fills);
        assertEquals(1, fill.fills, "converted to a fill");
        assertEquals(0, fill.images);
        assertPattern(image.image, false, "image");
        assertPattern(fill.image, false, "fill");
        assertEquals(image.listedImages, fill.listedImages, "Page.getImages still lists the mask");
    }

    @DisplayName("/Decode [1 0] inverts which samples paint")
    @Test
    void decodeInverted() throws Exception {
        Path file = page("inverted", " /Decode [1 0]", 8, 2, new byte[]{0x0F, (byte) 0xF0});
        assertPattern(render(file, false).image, true, "image");
        Rendered fill = render(file, true);
        assertEquals(1, fill.fills);
        assertPattern(fill.image, true, "fill");
    }

    @DisplayName("a mask over the size limit, or with /Interpolate, keeps the image path")
    @Test
    void largeOrInterpolatedKeepsImage() throws Exception {
        int width = 128;
        int height = StencilMaskFill.MAX_SAMPLES / width + 1;
        byte[] samples = new byte[width / 8 * height];
        Rendered large = render(page("large", "", width, height, samples), true);
        assertEquals(1, large.images);
        assertEquals(0, large.fills);

        Rendered interpolated = render(page("interp", " /Interpolate true", 8, 2,
                new byte[]{0x0F, (byte) 0xF0}), true);
        assertEquals(1, interpolated.images);
        assertEquals(0, interpolated.fills);
    }

    @DisplayName("a mask thinner than a device pixel paints one whole row of pixels, without antialiasing")
    @Test
    void subPixelMaskTouchesWholePixels() throws Exception {
        // an 8 x 1 mask that paints every sample, scaled to 160 x 0.3 pt: 0.3 px tall at zoom 1.
        Path file = page("thin", "q 0.8 0.1 0.1 rg 160 0 0 0.3 20 50.4 cm /M1 Do Q", "", 8, 1, new byte[]{0x00});
        Rendered fill = render(file, true);
        assertEquals(1, fill.fills);
        int rows = 0;
        for (int y = 0; y < 100; y++) {
            int reds = 0;
            for (int x = 0; x < 200; x++) {
                if (red(fill.image, x, y)) reds++;
                else assertTrue(white(fill.image, x, y), "only full red or white, no antialiased edge: " + x + "," + y);
            }
            if (reds > 0) {
                rows++;
                assertEquals(160, reds, "the row spans the mask");
            }
        }
        assertEquals(1, rows, "one whole row");
    }
}
