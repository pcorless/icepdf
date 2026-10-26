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
 * GH-176 tier 1: content painted through a glyph clip gets antialiased letter edges by filling the glyph
 * outline instead of clipping to it.  Each case renders twice, with the rewrite on and with it off (the hard
 * clip), and compares.
 */
public class GlyphClipFillTest {

    private static final int SIZE = 64;

    @TempDir
    Path temp;

    @AfterEach
    void clearProperty() {
        System.clearProperty(GlyphClipFill.ENABLED_PROPERTY);
    }

    /** A 64 x 64 RGB image, top half {@code top}, bottom half {@code bottom}. */
    private static String image(Color top, Color bottom) {
        StringBuilder samples = new StringBuilder();
        for (int y = 0; y < SIZE; y++) {
            Color c = y < SIZE / 2 ? top : bottom;
            for (int x = 0; x < SIZE; x++) {
                samples.append((char) c.getRed()).append((char) c.getGreen()).append((char) c.getBlue());
            }
        }
        return "<< /Type /XObject /Subtype /Image /Width " + SIZE + " /Height " + SIZE
                + " /BitsPerComponent 8 /ColorSpace /DeviceRGB /Length " + samples.length() + " >>\nstream\n"
                + samples + "\nendstream";
    }

    private Path page(String name, String content, String image) throws Exception {
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R"
                + " /Resources << /Font << /F1 5 0 R >> /XObject << /I1 6 0 R >> >> >>");
        objects.add("<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>");
        objects.add(image);
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

    private BufferedImage render(String name, String content, String image, boolean antialias) throws Exception {
        System.setProperty(GlyphClipFill.ENABLED_PROPERTY, String.valueOf(antialias));
        Document document = new Document();
        document.setFile(page(name + antialias, content, image).toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            BufferedImage out = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 300, 200);
            page.paint(g, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0f, 1f, true, false);
            g.dispose();
            return out;
        } finally {
            document.dispose();
        }
    }

    // the Charte table-of-contents shape: glyph clip under a cm, a rectangle clip, an image through both.
    private static final String CLIPPED_IMAGE = "q 1 0 0 1 20 100 cm BT 7 Tr /F1 48 Tf (HOME)Tj ET "
            + "q 0 -10 200 50 re W* n 200 0 0 50 0 -10 cm /I1 Do Q Q";

    private static boolean near(int rgb, Color c) {
        Color p = new Color(rgb);
        return Math.abs(p.getRed() - c.getRed()) < 24 && Math.abs(p.getGreen() - c.getGreen()) < 24
                && Math.abs(p.getBlue() - c.getBlue()) < 24;
    }

    /** Pixels that are neither white nor the given pure colours: the antialiased letter edges. */
    private static int edgePixels(BufferedImage image, Color... colours) {
        int edges = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                boolean pure = near(rgb, Color.WHITE);
                for (Color c : colours) pure |= near(rgb, c);
                if (!pure) edges++;
            }
        }
        return edges;
    }

    @DisplayName("a one-colour image through a glyph clip is filled with antialiased edges")
    @Test
    void solidImageIsFilled() throws Exception {
        Color red = new Color(192, 16, 16);
        String image = image(red, red);
        BufferedImage hard = render("solid", CLIPPED_IMAGE, image, false);
        BufferedImage soft = render("solid", CLIPPED_IMAGE, image, true);
        int hardRed = 0;
        int sameRed = 0;
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 300; x++) {
                if (near(hard.getRGB(x, y), red)) {
                    hardRed++;
                    if (near(soft.getRGB(x, y), red) || !near(soft.getRGB(x, y), Color.WHITE)) sameRed++;
                }
            }
        }
        assertTrue(hardRed > 500, "the hard clip paints the letters: " + hardRed);
        assertEquals(0, edgePixels(hard, red), "a hard clip leaves no partial pixels");
        assertTrue(edgePixels(soft, red) > 50, "the fill antialiases the letter edges");
        assertTrue(sameRed > hardRed * 0.98, "the same letters, in the same place: " + sameRed + "/" + hardRed);
    }

    @DisplayName("any other image is filled as a texture, mapped where drawImage puts it")
    @Test
    void imageIsTextured() throws Exception {
        Color red = new Color(200, 20, 20);
        Color blue = new Color(20, 20, 200);
        // top half red, bottom half blue: a flipped or misplaced texture swaps them.
        String image = image(red, blue);
        BufferedImage hard = render("two", CLIPPED_IMAGE, image, false);
        BufferedImage soft = render("two", CLIPPED_IMAGE, image, true);
        int pure = 0;
        int swapped = 0;
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 300; x++) {
                int rgb = hard.getRGB(x, y);
                Color expected = near(rgb, red) ? red : near(rgb, blue) ? blue : null;
                if (expected != null) {
                    pure++;
                    // the texture is bilinear, so the band where the halves meet blends; a swap is a bug.
                    if (near(soft.getRGB(x, y), expected == red ? blue : red)) swapped++;
                }
            }
        }
        assertTrue(pure > 500, "the hard clip paints both halves: " + pure);
        assertEquals(0, swapped, "the texture keeps each half where it was, out of " + pure);
        assertTrue(edgePixels(soft, red, blue) > edgePixels(hard, red, blue), "and antialiases the edges");
    }

    @DisplayName("two paints under one glyph clip keep the hard clip")
    @Test
    void twoPaintsFallBack() throws Exception {
        Color red = new Color(192, 16, 16);
        String content = "q 1 0 0 1 20 100 cm BT 7 Tr /F1 48 Tf (HOME)Tj ET "
                + "0.75 0.06 0.06 rg 0 -10 100 50 re f 100 -10 100 50 re f Q";
        BufferedImage hard = render("twofills", content, image(red, red), false);
        BufferedImage soft = render("twofills", content, image(red, red), true);
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 300; x++) {
                assertEquals(hard.getRGB(x, y), soft.getRGB(x, y), "pixel " + x + "," + y);
            }
        }
    }

    @DisplayName("a fill through a glyph clip is antialiased too")
    @Test
    void fillIsAntialiased() throws Exception {
        Color red = new Color(191, 15, 15);
        String content = "q 1 0 0 1 20 100 cm BT 7 Tr /F1 48 Tf (HOME)Tj ET "
                + "q 0 -10 200 50 re W n 0.75 0.06 0.06 rg 0 -10 200 50 re f Q Q";
        BufferedImage hard = render("fill", content, image(red, red), false);
        BufferedImage soft = render("fill", content, image(red, red), true);
        assertEquals(0, edgePixels(hard, red));
        assertTrue(edgePixels(soft, red) > 50);
    }
}
