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
import org.junit.jupiter.api.BeforeEach;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GH-176: text drawn with Tr 7 (add to clip) and then filled through a further re W clip.  The glyph
 * outlines carry the full CTM while the clip lives in user space, so under a cm the two never overlapped
 * and the intersection painted nothing.
 */
public class GlyphOutlineClipTest {

    // the fill covers this rectangle (page space); the glyph clip keeps only the letters inside it.
    private static final int FILL_AREA = 200 * 50;

    @TempDir
    Path temp;

    // this is about where the clip lands, so count pixels against the hard clip, not antialiased edges.
    @BeforeEach
    void hardClip() {
        System.setProperty(GlyphClipFill.ENABLED_PROPERTY, "false");
    }

    @AfterEach
    void clearProperty() {
        System.clearProperty(GlyphClipFill.ENABLED_PROPERTY);
    }

    private Path page(String name, String content) throws Exception {
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R"
                + " /Resources << /Font << /F1 5 0 R >> >> >>");
        objects.add("<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>");
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

    /** Red pixels on the page, painted on white at zoom 1. */
    private int redPixels(String name, String content) throws Exception {
        Document document = new Document();
        document.setFile(page(name, content).toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 300, 200);
            page.paint(g, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0f, 1f, true, false);
            g.dispose();
            int red = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    Color c = new Color(image.getRGB(x, y));
                    if (c.getRed() > 150 && c.getGreen() < 80 && c.getBlue() < 80) red++;
                }
            }
            return red;
        } finally {
            document.dispose();
        }
    }

    @DisplayName("a glyph clip then a re W clip paints the letters, whatever the CTM")
    @Test
    void glyphClipIntersectsUnderCm() throws Exception {
        // control: identity CTM, which always worked.
        int identity = redPixels("identity",
                "BT 7 Tr /F1 40 Tf 1 0 0 1 20 100 Tm (He)Tj ET "
                        + "q 20 90 200 50 re W n 0.75 0.06 0.06 rg 20 90 200 50 re f Q");
        assertTrue(identity > 200, "letters painted at identity: " + identity);
        assertTrue(identity < FILL_AREA / 2, "and clipped to the letters: " + identity);

        // the same page, placed with a translate (the Charte table of contents shape).
        int translated = redPixels("translated",
                "q 1 0 0 1 20 100 cm BT 7 Tr /F1 40 Tf (He)Tj ET "
                        + "q 0 -10 200 50 re W* n 0.75 0.06 0.06 rg 0 -10 200 50 re f Q Q");
        assertEquals(identity, translated, identity / 50 + 1.0, "translated CTM paints the same letters");

        // and with a scale, so the user space is no longer page space plus an offset.
        int scaled = redPixels("scaled",
                "q 2 0 0 2 10 50 cm BT 7 Tr /F1 20 Tf (He)Tj ET "
                        + "q 0 -5 100 25 re W n 0.75 0.06 0.06 rg 0 -5 100 25 re f Q Q");
        assertEquals(identity, scaled, identity / 50 + 1.0, "scaled CTM paints the same letters");
    }

    @DisplayName("the glyph clip is popped with its q, so later content is not clipped")
    @Test
    void glyphClipRestoredByQ() throws Exception {
        int red = redPixels("restored",
                "q 1 0 0 1 20 100 cm BT 7 Tr /F1 40 Tf (He)Tj ET Q "
                        + "0.75 0.06 0.06 rg 20 20 100 20 re f");
        assertEquals(100 * 20, red, "fill after Q is unclipped");
    }
}
