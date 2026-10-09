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
package org.icepdf.fx.view;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.*;
import org.icepdf.core.pobjects.graphics.text.OffsetRange;
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Annotations created and edited the way PdfView does it (AnnotationCreator + AnnotationEdits, no
 * toolkit) survive an incremental save and reopen: types, geometry, text, a popup placed outside
 * the crop box, and a deletion.
 */
class AnnotationRoundTripTest {

    private static final Path FIXTURE = Paths.get("../viewer-awt/src/test/resources/redact/test_print.pdf");
    private static final String AUTHOR = "round-trip tester";
    private static final AnnotationEdits.Locker NO_LOCK = (page, mutation) -> mutation.run();

    @DisplayName("create, move, edit, delete; save; reopen: everything as left")
    @Test
    void roundTrip() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture not found");
        Document document = new Document();
        document.setFile(FIXTURE.toString());
        Page page = document.getPageTree().getPage(0);
        page.init();
        Library library = page.getLibrary();
        AffineTransform pageToView = page.getPageTransform(Page.BOUNDARY_CROPBOX, 0, 1);
        AffineTransform toPage = page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0, 1);
        AnnotationCreator.Style style = new AnnotationCreator.Style(AUTHOR, Color.RED, 255, 2f);

        // a square, then moved by (30, 20) view px
        MarkupAnnotation square = AnnotationCreator.shape(library, false, new Rectangle2D.Double(50, 50, 120, 60),
                toPage, style);
        add(page, square, AnnotationCreator.popup(library, square, false, toPage));
        Rectangle2D viewNow = pageToView.createTransformedShape(square.getUserSpaceRectangle()).getBounds2D();
        Rectangle2D movedView = new Rectangle2D.Double(viewNow.getX() + 30, viewNow.getY() + 20,
                viewNow.getWidth(), viewNow.getHeight());
        AnnotationEdits.reshape(NO_LOCK, page, 0, square, movedView, 30, 20, toPage);
        Rectangle2D.Float squareRect = copy(square.getUserSpaceRectangle());

        // a sticky note with text, its popup moved off the page (left of the crop box)
        TextAnnotation note = AnnotationCreator.note(library, 300, 100, pageToView, toPage, style);
        PopupAnnotation notePopup = AnnotationCreator.popup(library, note, true, toPage);
        add(page, note, notePopup);
        AnnotationEdits.contents(NO_LOCK, page, 0, note, "Round trip note");
        AnnotationEdits.popupRect(NO_LOCK, page, 0, notePopup, new Rectangle2D.Double(-150, 600, 140, 100));

        // ink
        GeneralPath stroke = new GeneralPath();
        stroke.moveTo(100, 300);
        stroke.lineTo(140, 330);
        stroke.lineTo(180, 290);
        InkAnnotation ink = AnnotationCreator.ink(library, stroke, toPage, style);
        add(page, ink, AnnotationCreator.popup(library, ink, false, toPage));

        // a line, then deleted
        LineAnnotation line = AnnotationCreator.line(library, new java.awt.geom.Point2D.Double(60, 400),
                new java.awt.geom.Point2D.Double(260, 420), toPage, style);
        add(page, line, AnnotationCreator.popup(library, line, false, toPage));
        AnnotationEdits.delete(NO_LOCK, page, 0, line);

        // a highlight over the first words of the page's text
        TextSequence sequence = page.getViewText().getTextSequence();
        OffsetRange range = OffsetRange.of(0, Math.min(20, sequence.length()));
        List<Rectangle2D> viewRects = new ArrayList<>();
        for (Rectangle2D r : sequence.rectsFor(range)) viewRects.add(pageToView.createTransformedShape(r).getBounds2D());
        TextMarkupAnnotation highlight = AnnotationCreator.textMarkup(library, TextMarkupAnnotation.SUBTYPE_HIGHLIGHT,
                viewRects, sequence.extractText(range), toPage,
                new AnnotationCreator.Style(AUTHOR, Color.YELLOW, TextMarkupAnnotation.HIGHLIGHT_ALPHA, 1f));
        add(page, highlight, AnnotationCreator.popup(library, highlight, false, toPage));

        // free text with its text set in place
        FreeTextAnnotation freeText = AnnotationCreator.freeText(library, 320, 500, 1, toPage, style);
        add(page, freeText, null);
        AnnotationEdits.freeTextContents(NO_LOCK, page, 0, freeText, "Free text body", toPage);

        Path saved = Files.createTempFile("pdfview-roundtrip", ".pdf");
        try {
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(saved))) {
                document.saveToOutputStream(out);
            }
            document.dispose();

            Document reopened = new Document();
            reopened.setFile(saved.toString());
            Page again = reopened.getPageTree().getPage(0);
            again.init();
            List<Annotation> mine = new ArrayList<>();
            for (Annotation a : again.getAnnotations()) {
                if (a instanceof MarkupAnnotation m && AUTHOR.equals(m.getTitleText())) mine.add(a);
            }

            SquareAnnotation square2 = only(mine, SquareAnnotation.class);
            assertRect(squareRect, square2.getUserSpaceRectangle(), "moved square");

            TextAnnotation note2 = only(mine, TextAnnotation.class);
            assertEquals("Round trip note", note2.getContents());
            PopupAnnotation popup2 = note2.getPopupAnnotation();
            assertNotNull(popup2, "note keeps its popup");
            assertEquals(-150, popup2.getUserSpaceRectangle().getX(), 1, "popup saved outside the crop box");
            assertTrue(popup2.isOpen());

            assertNotNull(only(mine, InkAnnotation.class));
            assertTrue(mine.stream().noneMatch(a -> a instanceof LineAnnotation), "deleted line stays deleted");

            TextMarkupAnnotation highlight2 = only(mine, TextMarkupAnnotation.class);
            assertEquals(TextMarkupAnnotation.SUBTYPE_HIGHLIGHT, highlight2.getSubType());
            assertEquals(highlight.getContents(), highlight2.getContents());

            FreeTextAnnotation freeText2 = only(mine, FreeTextAnnotation.class);
            assertEquals("Free text body", freeText2.getContents());
            reopened.dispose();
        } finally {
            Files.deleteIfExists(saved);
        }
    }

    @DisplayName("a highlight made at 200% sits on its text: rect, QuadPoints and painted appearance")
    @Test
    void highlightSitsOnItsText() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture " + FIXTURE);
        Document document = new Document();
        document.setFile(FIXTURE.toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            TextSequence sequence = page.getViewText().getTextSequence();
            OffsetRange range = OffsetRange.of(0, Math.min(20, sequence.length()));
            Rectangle2D text = null;
            for (Rectangle2D r : sequence.rectsFor(range)) {
                if (text == null) text = new Rectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight());
                else text.add(r);
            }
            assertNotNull(text, "the fixture has text");
            // made the way PdfViewSkin.markupSelection makes one, at 200%
            AffineTransform pageToView = page.getPageTransform(Page.BOUNDARY_CROPBOX, 0, 2);
            AffineTransform toPage = page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0, 2);
            List<Rectangle2D> viewRects = new ArrayList<>();
            for (Rectangle2D r : sequence.rectsFor(range)) viewRects.add(pageToView.createTransformedShape(r).getBounds2D());
            TextMarkupAnnotation highlight = AnnotationCreator.textMarkup(page.getLibrary(),
                    TextMarkupAnnotation.SUBTYPE_HIGHLIGHT, viewRects, "", toPage,
                    new AnnotationCreator.Style(AUTHOR, Color.YELLOW, TextMarkupAnnotation.HIGHLIGHT_ALPHA, 1f));

            assertRect(text, highlight.getUserSpaceRectangle(), "highlight /Rect");
            List<?> quad = (List<?>) highlight.getObject(MarkupAnnotation.KEY_QUAD_POINTS);
            Rectangle2D quadBounds = null;
            for (int i = 0; i + 1 < quad.size(); i += 2) {
                double x = ((Number) quad.get(i)).doubleValue(), y = ((Number) quad.get(i + 1)).doubleValue();
                if (quadBounds == null) quadBounds = new Rectangle2D.Double(x, y, 0, 0);
                else quadBounds.add(x, y);
            }
            assertRect(text, quadBounds, "highlight /QuadPoints (page space)");

            // painted alone at 100%: yellow over the text's middle, nothing well outside it.
            AffineTransform view1 = page.getPageTransform(Page.BOUNDARY_CROPBOX, 0, 1);
            Rectangle2D viewText = view1.createTransformedShape(text).getBounds2D();
            java.awt.Dimension size = page.getSize(Page.BOUNDARY_CROPBOX, 0, 1).toDimension();
            java.awt.image.BufferedImage layer = new java.awt.image.BufferedImage(size.width, size.height,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = layer.createGraphics();
            g.transform(view1);
            highlight.render(g, org.icepdf.core.util.GraphicsRenderingHints.SCREEN, page.getTotalRotation(0), 1, false);
            g.dispose();
            // the first selected rect's middle (the union's can fall between lines).
            Rectangle2D first = view1.createTransformedShape(sequence.rectsFor(range).get(0)).getBounds2D();
            Color middle = new Color(layer.getRGB((int) first.getCenterX(), (int) first.getCenterY()), true);
            assertTrue(middle.getAlpha() > 200 && middle.getRed() > 200 && middle.getBlue() < 80,
                    "yellow over the text: " + middle);
            int below = (int) Math.min(size.height - 1, viewText.getMaxY() + 20);
            assertEquals(0, new Color(layer.getRGB((int) viewText.getCenterX(), below), true).getAlpha(),
                    "nothing below the text");
        } finally {
            document.dispose();
        }
    }

    private static void add(Page page, Annotation annotation, PopupAnnotation popup) {
        AnnotationEdits.add(NO_LOCK, page, 0, annotation, popup);
    }

    private static <T extends Annotation> T only(List<Annotation> annotations, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Annotation a : annotations) if (type.isInstance(a)) found.add(type.cast(a));
        assertEquals(1, found.size(), "exactly one " + type.getSimpleName() + " of ours");
        return found.get(0);
    }

    private static Rectangle2D.Float copy(Rectangle2D r) {
        Rectangle2D.Float c = new Rectangle2D.Float();
        c.setRect(r);
        return c;
    }

    private static void assertRect(Rectangle2D expected, Rectangle2D actual, String what) {
        assertEquals(expected.getX(), actual.getX(), 1, what + " x");
        assertEquals(expected.getY(), actual.getY(), 1, what + " y");
        assertEquals(expected.getWidth(), actual.getWidth(), 1, what + " width");
        assertEquals(expected.getHeight(), actual.getHeight(), 1, what + " height");
    }
}
