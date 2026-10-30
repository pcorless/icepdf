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
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A squiggly is a wave along the bottom of the marked text: drawn inside its bounds (so the
 * appearance bbox doesn't clip it), open (no line back to the start), and the same after a save.
 */
public class SquigglyMarkupTest {

    @TempDir
    Path temp;

    /** The marked text, in view space (= page at zoom 1, y down): 20..180 x 40..80. */
    private static final Rectangle2D.Double TEXT = new Rectangle2D.Double(20, 40, 160, 40);

    private Path blankPage() throws Exception {
        String content = "";
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Contents 4 0 R >>",
                "<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream");
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
        Path file = temp.resolve("blank.pdf");
        Files.write(file, out.toString().getBytes(StandardCharsets.ISO_8859_1));
        return file;
    }

    private static TextMarkupAnnotation squiggly(Page page) {
        AffineTransform toPageSpace = page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0, 1f);
        List<Shape> bounds = new ArrayList<>(List.of(toPageSpace.createTransformedShape(TEXT)));
        GeneralPath path = new GeneralPath();
        for (Shape shape : bounds) path.append(shape, false);
        Rectangle bbox = path.getBounds();
        TextMarkupAnnotation annotation = (TextMarkupAnnotation) AnnotationFactory.buildAnnotation(
                page.getLibrary(), TextMarkupAnnotation.SUBTYPE_SQUIGGLY, bbox);
        annotation.setCreationDate(PDate.formatDateTime(new Date()));
        annotation.setColor(Color.RED);
        annotation.setOpacity(255);
        annotation.setMarkupBounds(new ArrayList<>(bounds));
        annotation.setMarkupPath(path);
        annotation.setBBox(bbox);
        annotation.resetAppearanceStream(toPageSpace);
        return annotation;
    }

    private static BufferedImage paint(Page page) throws InterruptedException {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 200);
        page.paint(g, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0f, 1f, true, false);
        g.dispose();
        return image;
    }

    private static boolean red(int rgb) {
        Color c = new Color(rgb);
        return c.getRed() > 150 && c.getGreen() < 150 && c.getBlue() < 150;
    }

    /** Where the ink is: a wave near the bottom of the text, across its width, nothing elsewhere. */
    private static void assertWave(BufferedImage image, String when) {
        int top = 200, bottom = -1, left = 200, right = -1;
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 200; x++) {
                if (!red(image.getRGB(x, y))) continue;
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                left = Math.min(left, x);
                right = Math.max(right, x);
            }
        }
        assertTrue(bottom >= 0, when + ": something is drawn");
        int textBottom = (int) TEXT.getMaxY();
        assertTrue(top >= textBottom - 8 && bottom <= textBottom + 1,
                when + ": the ink sits at the bottom of the text, rows " + top + ".." + bottom);
        assertTrue(bottom - top >= 3, when + ": it waves, not a flat line (" + top + ".." + bottom + ")");
        assertTrue(left <= TEXT.getMinX() + 2 && right >= TEXT.getMaxX() - 2,
                when + ": it spans the text, columns " + left + ".." + right);
        // an open path: a closing line back to the start would run the length of the bottom row.
        int bottomRun = 0, run = 0;
        for (int x = 0; x < 200; x++) {
            run = red(image.getRGB(x, bottom)) || red(image.getRGB(x, top)) ? run + 1 : 0;
            bottomRun = Math.max(bottomRun, run);
        }
        assertTrue(bottomRun < 40, when + ": no straight line along an edge (longest run " + bottomRun + ")");
    }

    @DisplayName("a new squiggly draws a wave along the bottom of the text")
    @Test
    void drawsWave() throws Exception {
        Document document = new Document();
        document.setFile(blankPage().toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            page.addAnnotation(squiggly(page));
            assertWave(paint(page), "new");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("saved and reopened, the squiggly draws the same wave")
    @Test
    void survivesSave() throws Exception {
        Document document = new Document();
        document.setFile(blankPage().toString());
        Path saved = temp.resolve("saved.pdf");
        Page page = document.getPageTree().getPage(0);
        page.init();
        page.addAnnotation(squiggly(page));
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(saved))) {
            document.saveToOutputStream(out, WriteMode.INCREMENT_UPDATE);
        }
        document.dispose();

        Document reopened = new Document();
        reopened.setFile(saved.toString());
        try {
            Page page2 = reopened.getPageTree().getPage(0);
            page2.init();
            assertEquals(TextMarkupAnnotation.SUBTYPE_SQUIGGLY, page2.getAnnotations().get(0).getSubType());
            assertWave(paint(page2), "reopened");
        } finally {
            reopened.dispose();
        }
    }
}
