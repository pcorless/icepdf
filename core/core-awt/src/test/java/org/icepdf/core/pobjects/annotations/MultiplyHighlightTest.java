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
 * Highlights are drawn with the Multiply blend mode, as a highlighter (and Acrobat) does: the paper
 * turns yellow and text under it stays black.  And a blend appearance drawn on a transparent layer -
 * a viewer's annotation layer, an export without the page - shows its colour, where the blend
 * against nothing used to paint black (A5).
 */
public class MultiplyHighlightTest {

    @TempDir
    Path temp;

    /** A 200 x 200 page with a black square (the "text") at 20..80. */
    private Path page(String name, List<String> extraObjects, String annots) throws Exception {
        return page(name, "0 g 20 120 60 60 re f", extraObjects, annots);
    }

    private Path page(String name, String content, List<String> extraObjects, String annots) throws Exception {
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Contents 4 0 R" + annots + " >>");
        objects.add("<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream");
        objects.addAll(extraObjects);
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

    /** A highlight made the way the tools make one, over view (= page at zoom 1) 10..150 x 10..90. */
    private static TextMarkupAnnotation highlight(Page page) {
        AffineTransform toPageSpace = page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0, 1f);
        // as the Swing tool does: the selection's view rectangles, converted to page space.
        List<Shape> bounds = new ArrayList<>(List.of(toPageSpace.createTransformedShape(
                new Rectangle2D.Double(10, 10, 140, 80))));
        GeneralPath path = new GeneralPath();
        for (Shape shape : bounds) path.append(shape, false);
        Rectangle bbox = path.getBounds();
        TextMarkupAnnotation annotation = (TextMarkupAnnotation) AnnotationFactory.buildAnnotation(
                page.getLibrary(), TextMarkupAnnotation.SUBTYPE_HIGHLIGHT, bbox);
        annotation.setCreationDate(PDate.formatDateTime(new Date()));
        annotation.setColor(Color.YELLOW);
        annotation.setOpacity(TextMarkupAnnotation.HIGHLIGHT_ALPHA);
        annotation.setMarkupBounds(new ArrayList<>(bounds));
        annotation.setMarkupPath(path);
        annotation.setBBox(bbox);
        annotation.resetAppearanceStream(toPageSpace);
        return annotation;
    }

    /** The page with its annotations, on white, at zoom 1. */
    private static BufferedImage paint(Page page) throws Exception {
        return paint(page, BufferedImage.TYPE_INT_ARGB);
    }

    private static BufferedImage paint(Page page, int imageType) throws Exception {
        BufferedImage image = new BufferedImage(200, 200, imageType);
        Graphics2D g = image.createGraphics();
        page.paint(g, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0f, 1f, true, false);
        g.dispose();
        return image;
    }

    /** One annotation alone on a transparent layer, at zoom 1. */
    private static BufferedImage layer(Page page, Annotation annotation) {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.transform(page.getPageTransform(Page.BOUNDARY_CROPBOX, 0f, 1f));
        annotation.render(g, GraphicsRenderingHints.SCREEN, page.getTotalRotation(0f), 1f, false);
        g.dispose();
        return image;
    }

    private static void assertYellow(int argb, String where) {
        Color c = new Color(argb, true);
        assertTrue(c.getRed() > 200 && c.getGreen() > 200 && c.getBlue() < 80 && c.getAlpha() > 200,
                where + " is yellow: " + c);
    }

    private static void assertBlack(int argb, String where) {
        Color c = new Color(argb, true);
        assertTrue(c.getRed() < 40 && c.getGreen() < 40 && c.getBlue() < 40 && c.getAlpha() > 200,
                where + " stays black: " + c);
    }

    @DisplayName("a new highlight multiplies: the paper turns yellow, the text under it stays black")
    @Test
    void highlightMultiplies() throws Exception {
        Document document = new Document();
        document.setFile(page("plain", List.of(), "").toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            TextMarkupAnnotation highlight = highlight(page);
            assertTrue(highlight.appearanceHasBlendMode(), "written with a blend mode");
            page.addAnnotation(highlight, true);
            BufferedImage image = paint(page);
            assertYellow(image.getRGB(120, 50), "paper under the highlight");
            assertBlack(image.getRGB(50, 50), "the black square under the highlight");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("on a transparent layer the highlight shows its colour, not black")
    @Test
    void transparentLayer() throws Exception {
        Document document = new Document();
        document.setFile(page("layer", List.of(), "").toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            BufferedImage image = layer(page, highlight(page));
            assertYellow(image.getRGB(120, 50), "the highlight on a transparent layer");
            assertEquals(0, new Color(image.getRGB(180, 180), true).getAlpha(), "outside stays transparent");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("saved and reopened, the highlight still multiplies (its /ExtGState has /BM /Multiply)")
    @Test
    void survivesSave() throws Exception {
        Document document = new Document();
        document.setFile(page("save", List.of(), "").toString());
        Path saved = temp.resolve("saved.pdf");
        Page page = document.getPageTree().getPage(0);
        page.init();
        page.addAnnotation(highlight(page), true);
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(saved))) {
            document.saveToOutputStream(out, WriteMode.INCREMENT_UPDATE);
        }
        document.dispose();

        Document reopened = new Document();
        reopened.setFile(saved.toString());
        try {
            Page page2 = reopened.getPageTree().getPage(0);
            page2.init();
            Annotation highlight = page2.getAnnotations().get(0);
            assertTrue(highlight.appearanceHasBlendMode(), "re-read with its blend mode");
            BufferedImage image = paint(page2);
            assertYellow(image.getRGB(120, 50), "paper under the highlight");
            assertBlack(image.getRGB(50, 50), "the black square under the highlight");
            assertYellow(layer(page2, highlight).getRGB(120, 50), "the reopened highlight on a transparent layer");
        } finally {
            reopened.dispose();
        }
    }

    @DisplayName("a highlight from another writer (a Multiply appearance) on a transparent layer isn't black")
    @Test
    void thirdPartyMultiplyAppearance() throws Exception {
        // an Acrobat-style highlight: an appearance form whose gs sets /BM /Multiply, filled yellow.
        String appearance = "<< /Type /XObject /Subtype /Form /BBox [0 0 140 80] "
                + "/Resources << /ExtGState << /G0 << /BM /Multiply /ca 1 /CA 1 >> >> >> /Length 31 >>\n"
                + "stream\n/G0 gs 1 1 0 rg 0 0 140 80 re f\nendstream";
        Path file = page("third-party", List.of(appearance,
                        "<< /Type /Annot /Subtype /Highlight /Rect [10 110 150 190] /F 4 /C [1 1 0] "
                                + "/QuadPoints [10 190 150 190 10 110 150 110] /AP << /N 5 0 R >> >>"),
                " /Annots [6 0 R]");
        Document document = new Document();
        document.setFile(file.toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            Annotation highlight = page.getAnnotations().get(0);
            assertTrue(highlight.appearanceHasBlendMode());
            assertYellow(layer(page, highlight).getRGB(120, 50), "a Multiply appearance on a transparent layer");
            assertBlack(paint(page).getRGB(50, 50), "the black square under it on the page");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("on an opaque RGB page image the highlight multiplies over text, not paints over it")
    @Test
    void rgbPageImageKeepsText() throws Exception {
        // Document.getPageImage paints into a TYPE_INT_RGB image.  Its alpha byte is undefined: shape
        // fills happen to store 0xFF, but glyphs are stored with 0 - which read as a transparent
        // backdrop and painted the highlight colour over the text.
        Document document = new Document();
        document.setFile(page("rgb", "BT /F1 60 Tf 20 120 Td (II) Tj ET",
                List.of("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"),
                " /Resources << /Font << /F1 5 0 R >> >>").toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            page.addAnnotation(highlight(page), true);
            BufferedImage rgb = paint(page, BufferedImage.TYPE_INT_RGB);
            BufferedImage argb = paint(page, BufferedImage.TYPE_INT_ARGB);
            assertYellow(rgb.getRGB(120, 50), "paper under the highlight");
            // the glyphs' anti-aliased edges are the pixels stored with alpha 0 here.
            int worst = 0;
            for (int y = 0; y < 200; y++) {
                for (int x = 0; x < 200; x++) {
                    Color c = new Color(rgb.getRGB(x, y)), d = new Color(argb.getRGB(x, y));
                    worst = Math.max(worst, Math.max(Math.abs(c.getRed() - d.getRed()),
                            Math.max(Math.abs(c.getGreen() - d.getGreen()), Math.abs(c.getBlue() - d.getBlue()))));
                }
            }
            assertTrue(worst <= 2, "the RGB page image matches the ARGB one, worst channel delta " + worst);
        } finally {
            document.dispose();
        }
    }
}
