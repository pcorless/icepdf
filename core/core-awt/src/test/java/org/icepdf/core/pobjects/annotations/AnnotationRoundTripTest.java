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
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Creates each kind of annotation on a page, writes the document, and reads it back.
 * <p>
 * An annotation's properties live in a PDF dictionary, not in Java fields, so a setter that writes
 * the wrong key - or writes nothing at all - looks perfectly correct until the file is reopened.
 * These tests therefore assert only across a save: the annotation that comes back out of the bytes
 * is the one being checked, never the in-memory object that was just configured.
 * <p>
 * Both write modes are covered, because they take different paths through the writer: an
 * incremental update appends the new objects and a second cross-reference section, while a full
 * update rebuilds the file from the object graph.
 */
public class AnnotationRoundTripTest {

    private static final String FIXTURE = "/redaction/simple_tj.pdf";
    private static final Rectangle BOUNDS = new Rectangle(100, 500, 200, 100);

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static Document openFixture() throws Exception {
        Document document = new Document();
        document.setInputStream(
                AnnotationRoundTripTest.class.getResourceAsStream(FIXTURE), "simple_tj.pdf");
        return document;
    }

    private static Document open(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "round-trip");
        return document;
    }

    private static byte[] save(Document document, WriteMode writeMode) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.saveToOutputStream(out, writeMode);
        return out.toByteArray();
    }

    /**
     * Builds an annotation of the given subtype on page 0, hands it to {@code configure}, adds it,
     * saves, and returns the annotation as it reads back out of the saved bytes.
     *
     * @param subType   annotation subtype to create
     * @param writeMode how to write the document
     * @param configure applied to the annotation before it is added to the page
     * @param type      expected class of the annotation on the way back in
     * @return the reopened annotation
     */
    private static <T extends Annotation> T roundTrip(Name subType, WriteMode writeMode,
                                                      java.util.function.Consumer<T> configure,
                                                      Class<T> type) throws Exception {
        byte[] saved;
        Document document = openFixture();
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            Library library = document.getCatalog().getLibrary();

            T annotation = type.cast(AnnotationFactory.buildAnnotation(library, subType, BOUNDS));
            assertNotNull(annotation, "the factory should know how to build a " + subType);
            configure.accept(annotation);
            annotation.resetAppearanceStream(new AffineTransform());
            page.addAnnotation(annotation);

            saved = save(document, writeMode);
        } finally {
            document.dispose();
        }

        Document reopened = open(saved);
        try {
            Page page = reopened.getPageTree().getPage(0);
            page.init();
            for (Annotation annotation : page.getAnnotations()) {
                if (type.isInstance(annotation) && subType.equals(annotation.getSubType())) {
                    return type.cast(annotation);
                }
            }
        } finally {
            // the caller reads the annotation's dictionary, which the disposed document still holds
            reopened.dispose();
        }
        throw new AssertionError("no " + subType + " annotation survived the save");
    }

    // ------------------------------------------------------------------
    // the subtypes
    // ------------------------------------------------------------------

    @DisplayName("link - the highlight mode survives a save")
    @Test
    public void linkAnnotation() throws Exception {
        LinkAnnotation link = roundTrip(Annotation.SUBTYPE_LINK, WriteMode.INCREMENT_UPDATE,
                annotation -> annotation.setHighlightMode(LinkAnnotation.HIGHLIGHT_OUTLINE),
                LinkAnnotation.class);
        assertEquals(LinkAnnotation.HIGHLIGHT_OUTLINE, link.getHighlightMode());
    }

    @DisplayName("line - the endpoints and the interior colour survive a save")
    @Test
    public void lineAnnotation() throws Exception {
        LineAnnotation line = roundTrip(Annotation.SUBTYPE_LINE, WriteMode.INCREMENT_UPDATE,
                annotation -> {
                    annotation.setStartOfLine(new Point2D.Float(110, 510));
                    annotation.setEndOfLine(new Point2D.Float(290, 590));
                    annotation.setInteriorColor(Color.GREEN);
                    annotation.setColor(Color.RED);
                }, LineAnnotation.class);

        assertEquals(110.0, line.getStartOfLine().getX(), 0.5);
        assertEquals(510.0, line.getStartOfLine().getY(), 0.5);
        assertEquals(290.0, line.getEndOfLine().getX(), 0.5);
        assertEquals(590.0, line.getEndOfLine().getY(), 0.5);
        assertEquals(Color.GREEN, line.getInteriorColor());
        assertEquals(Color.RED, line.getColor());
    }

    @DisplayName("square - the fill colour survives a save, and no fill means no fill")
    @Test
    public void squareAnnotation() throws Exception {
        SquareAnnotation filled = roundTrip(Annotation.SUBTYPE_SQUARE, WriteMode.INCREMENT_UPDATE,
                annotation -> annotation.setFillColor(Color.YELLOW), SquareAnnotation.class);
        assertEquals(Color.YELLOW, filled.getFillColor());

        // Turning the fill off removes /IC entirely.  getFillColor still answers white - the class
        // keeps a paint to draw with either way - so isFillColor is what says whether to use it.
        SquareAnnotation unfilled = roundTrip(Annotation.SUBTYPE_SQUARE, WriteMode.INCREMENT_UPDATE,
                annotation -> annotation.setFillColor(false), SquareAnnotation.class);
        assertFalse(unfilled.isFillColor(), "an unfilled square should not report a fill");
        assertNull(unfilled.getEntries().get(MarkupAnnotation.IC_KEY),
                "an unfilled square should write no interior colour");
    }

    @DisplayName("circle - the fill colour survives a save")
    @Test
    public void circleAnnotation() throws Exception {
        CircleAnnotation circle = roundTrip(Annotation.SUBTYPE_CIRCLE, WriteMode.INCREMENT_UPDATE,
                annotation -> {
                    annotation.setFillColor(Color.CYAN);
                    annotation.setColor(Color.BLACK);
                }, CircleAnnotation.class);
        assertEquals(Color.CYAN, circle.getFillColor());
        assertEquals(Color.BLACK, circle.getColor());
    }

    @DisplayName("ink - the drawn path survives a save")
    @Test
    public void inkAnnotation() throws Exception {
        InkAnnotation ink = roundTrip(Annotation.SUBTYPE_INK, WriteMode.INCREMENT_UPDATE,
                annotation -> {
                    GeneralPath path = new GeneralPath();
                    path.moveTo(110, 510);
                    path.lineTo(150, 550);
                    path.lineTo(250, 520);
                    annotation.setInkPath(path);
                }, InkAnnotation.class);

        assertNotNull(ink.getInkPath(), "the ink list should have been written and read back");
        // the path's extent has to survive; its exact point count is the writer's business
        assertTrue(ink.getInkPath().getBounds().width > 0);
        assertTrue(ink.getInkPath().getBounds().height > 0);
    }

    @DisplayName("text markup - a highlight keeps its subtype, colour and quad points")
    @Test
    public void textMarkupAnnotation() throws Exception {
        TextMarkupAnnotation markup = roundTrip(TextMarkupAnnotation.SUBTYPE_HIGHLIGHT,
                WriteMode.INCREMENT_UPDATE,
                annotation -> {
                    List<java.awt.Shape> quads = new ArrayList<>();
                    quads.add(BOUNDS);
                    annotation.setMarkupBounds((ArrayList<java.awt.Shape>) quads);
                    annotation.setMarkupPath(new GeneralPath(BOUNDS));
                    annotation.setColor(Color.YELLOW);
                }, TextMarkupAnnotation.class);

        assertEquals(TextMarkupAnnotation.SUBTYPE_HIGHLIGHT, markup.getSubType());
        assertEquals(Color.YELLOW, markup.getColor());
        // the quad points are what a reader uses to paint the highlight: eight numbers per quad
        List<?> quadPoints = (List<?>) markup.getEntries().get(MarkupAnnotation.KEY_QUAD_POINTS);
        assertNotNull(quadPoints, "the highlight should have written its quad points");
        assertEquals(8, quadPoints.size());
    }

    @DisplayName("text markup - the other three subtypes are built and saved as themselves")
    @Test
    public void textMarkupSubtypes() throws Exception {
        for (Name subType : new Name[]{TextMarkupAnnotation.SUBTYPE_UNDERLINE,
                TextMarkupAnnotation.SUBTYPE_SQUIGGLY,
                TextMarkupAnnotation.SUBTYPE_STRIKE_OUT}) {
            TextMarkupAnnotation markup = roundTrip(subType, WriteMode.INCREMENT_UPDATE,
                    annotation -> {
                        List<java.awt.Shape> quads = new ArrayList<>();
                        quads.add(BOUNDS);
                        annotation.setMarkupBounds((ArrayList<java.awt.Shape>) quads);
                        annotation.setMarkupPath(new GeneralPath(BOUNDS));
                    }, TextMarkupAnnotation.class);
            assertEquals(subType, markup.getSubType());
        }
    }

    @DisplayName("free text - the text, font and colours survive a save")
    @Test
    public void freeTextAnnotation() throws Exception {
        FreeTextAnnotation freeText = roundTrip(Annotation.SUBTYPE_FREE_TEXT,
                WriteMode.INCREMENT_UPDATE,
                annotation -> {
                    annotation.setContents("typed into the page");
                    annotation.setFontSize(14);
                    annotation.setFontColor(Color.BLUE);
                    annotation.setFillColor(Color.WHITE);
                    annotation.setQuadding(1);
                }, FreeTextAnnotation.class);

        assertEquals("typed into the page", freeText.getContents());
        assertEquals(14f, freeText.getFontSize(), 0.5f);
        assertEquals(Color.BLUE, freeText.getFontColor());
        assertEquals(1, freeText.getQuadding());
    }

    @DisplayName("text - the sticky note's contents and title survive a save")
    @Test
    public void textAnnotation() throws Exception {
        TextAnnotation text = roundTrip(Annotation.SUBTYPE_TEXT, WriteMode.INCREMENT_UPDATE,
                annotation -> {
                    annotation.setContents("a note to self");
                    annotation.setTitleText("Reviewer");
                    annotation.setSubject("Chapter 2");
                }, TextAnnotation.class);

        assertEquals("a note to self", text.getContents());
        assertEquals("Reviewer", text.getTitleText());
        assertEquals("Chapter 2", text.getSubject());
    }

    @DisplayName("redact - the markup path survives a save")
    @Test
    public void redactionAnnotation() throws Exception {
        RedactionAnnotation redaction = roundTrip(Annotation.SUBTYPE_REDACT,
                WriteMode.INCREMENT_UPDATE,
                annotation -> {
                    List<java.awt.Shape> quads = new ArrayList<>();
                    quads.add(BOUNDS);
                    annotation.setMarkupBounds((ArrayList<java.awt.Shape>) quads);
                    annotation.setMarkupPath(new GeneralPath(BOUNDS));
                    annotation.setColor(Color.BLACK);
                }, RedactionAnnotation.class);

        assertNotNull(redaction.getEntries().get(MarkupAnnotation.KEY_QUAD_POINTS),
                "the redaction should have written the area it covers");
        assertEquals(Color.BLACK, redaction.getColor());
    }

    // ------------------------------------------------------------------
    // properties shared by every annotation
    // ------------------------------------------------------------------

    @DisplayName("the rectangle, contents and modified date survive a save")
    @Test
    public void commonProperties() throws Exception {
        SquareAnnotation square = roundTrip(Annotation.SUBTYPE_SQUARE, WriteMode.INCREMENT_UPDATE,
                annotation -> {
                    annotation.setContents("a comment");
                    annotation.setModifiedDate("D:20260101120000-05'00'");
                }, SquareAnnotation.class);

        assertEquals("a comment", square.getContents());
        assertNotNull(square.getModifiedDate());
        assertEquals(BOUNDS.x, square.getUserSpaceRectangle().getX(), 1.0);
        assertEquals(BOUNDS.width, square.getUserSpaceRectangle().getWidth(), 1.0);
    }

    @DisplayName("the annotation flags survive a save")
    @Test
    public void flags() throws Exception {
        SquareAnnotation square = roundTrip(Annotation.SUBTYPE_SQUARE, WriteMode.INCREMENT_UPDATE,
                annotation -> {
                    annotation.setFlag(Annotation.FLAG_PRINT, true);
                    annotation.setFlag(Annotation.FLAG_READ_ONLY, true);
                    annotation.setFlag(Annotation.FLAG_HIDDEN, false);
                }, SquareAnnotation.class);

        assertTrue(square.getFlagPrint());
        assertTrue(square.getFlagReadOnly());
        assertTrue(!square.getFlagHidden());
    }

    @DisplayName("a markup annotation's opacity survives a save")
    @Test
    public void opacity() throws Exception {
        SquareAnnotation square = roundTrip(Annotation.SUBTYPE_SQUARE, WriteMode.INCREMENT_UPDATE,
                annotation -> annotation.setOpacity(0.5f), SquareAnnotation.class);
        assertEquals(0.5f, square.getOpacity(), 0.01f);
    }

    @DisplayName("an appearance stream is written, and it is what the reopened annotation renders")
    @Test
    public void appearanceStream() throws Exception {
        SquareAnnotation square = roundTrip(Annotation.SUBTYPE_SQUARE, WriteMode.INCREMENT_UPDATE,
                annotation -> annotation.setFillColor(Color.ORANGE), SquareAnnotation.class);
        assertTrue(square.hasAppearanceStream(), "the annotation should carry its own /AP");
        assertNotNull(square.getAppearanceStream());
    }

    // ------------------------------------------------------------------
    // write modes
    // ------------------------------------------------------------------

    @DisplayName("a full rewrite preserves an annotation just as an incremental update does")
    @Test
    public void fullUpdate() throws Exception {
        SquareAnnotation square = roundTrip(Annotation.SUBTYPE_SQUARE, WriteMode.FULL_UPDATE,
                annotation -> {
                    annotation.setFillColor(Color.MAGENTA);
                    annotation.setContents("written whole");
                }, SquareAnnotation.class);
        assertEquals(Color.MAGENTA, square.getFillColor());
        assertEquals("written whole", square.getContents());
    }

    @DisplayName("an annotation survives two saves in a row")
    @Test
    public void twoSaves() throws Exception {
        // The second save has to contend with a file that already has an incremental section; the
        // state manager's view of what is new must not include what the first save already wrote.
        byte[] once;
        Document document = openFixture();
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            SquareAnnotation square = (SquareAnnotation) AnnotationFactory.buildAnnotation(
                    document.getCatalog().getLibrary(), Annotation.SUBTYPE_SQUARE, BOUNDS);
            square.setContents("first pass");
            square.resetAppearanceStream(new AffineTransform());
            page.addAnnotation(square);
            once = save(document, WriteMode.INCREMENT_UPDATE);
        } finally {
            document.dispose();
        }

        byte[] twice;
        Document second = open(once);
        try {
            Page page = second.getPageTree().getPage(0);
            page.init();
            Annotation existing = page.getAnnotations().get(0);
            existing.setContents("second pass");
            // Editing an annotation's dictionary does not by itself mark it dirty; the caller tells
            // the state manager, which is what decides the object gets written at all.
            second.getStateManager().addChange(
                    new PObject(existing, existing.getPObjectReference()));
            twice = save(second, WriteMode.INCREMENT_UPDATE);
        } finally {
            second.dispose();
        }

        Document third = open(twice);
        try {
            Page page = third.getPageTree().getPage(0);
            page.init();
            assertEquals("second pass", page.getAnnotations().get(0).getContents());
        } finally {
            third.dispose();
        }
    }

    // ------------------------------------------------------------------
    // deletion
    // ------------------------------------------------------------------

    @DisplayName("a deleted annotation is gone from the saved file")
    @Test
    public void deleteAnnotation() throws Exception {
        byte[] withAnnotation;
        Document document = openFixture();
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            SquareAnnotation square = (SquareAnnotation) AnnotationFactory.buildAnnotation(
                    document.getCatalog().getLibrary(), Annotation.SUBTYPE_SQUARE, BOUNDS);
            square.resetAppearanceStream(new AffineTransform());
            page.addAnnotation(square);
            withAnnotation = save(document, WriteMode.INCREMENT_UPDATE);
        } finally {
            document.dispose();
        }

        byte[] withoutAnnotation;
        Document second = open(withAnnotation);
        try {
            Page page = second.getPageTree().getPage(0);
            page.init();
            assertEquals(1, page.getAnnotations().size());
            page.deleteAnnotation(page.getAnnotations().get(0));
            withoutAnnotation = save(second, WriteMode.FULL_UPDATE);
        } finally {
            second.dispose();
        }

        Document third = open(withoutAnnotation);
        try {
            Page page = third.getPageTree().getPage(0);
            page.init();
            assertTrue(page.getAnnotations() == null || page.getAnnotations().isEmpty(),
                    "the deleted annotation should not have come back");
        } finally {
            third.dispose();
        }
    }

    // ------------------------------------------------------------------
    // the factory
    // ------------------------------------------------------------------

    @DisplayName("the factory returns the right class for every subtype it claims to support")
    @Test
    public void factoryDispatch() throws Exception {
        Document document = openFixture();
        try {
            Library library = document.getCatalog().getLibrary();
            assertInstanceOf(LinkAnnotation.class,
                    AnnotationFactory.buildAnnotation(library, Annotation.SUBTYPE_LINK, BOUNDS));
            assertInstanceOf(LineAnnotation.class,
                    AnnotationFactory.buildAnnotation(library, Annotation.SUBTYPE_LINE, BOUNDS));
            assertInstanceOf(SquareAnnotation.class,
                    AnnotationFactory.buildAnnotation(library, Annotation.SUBTYPE_SQUARE, BOUNDS));
            assertInstanceOf(CircleAnnotation.class,
                    AnnotationFactory.buildAnnotation(library, Annotation.SUBTYPE_CIRCLE, BOUNDS));
            assertInstanceOf(InkAnnotation.class,
                    AnnotationFactory.buildAnnotation(library, Annotation.SUBTYPE_INK, BOUNDS));
            assertInstanceOf(FreeTextAnnotation.class,
                    AnnotationFactory.buildAnnotation(library, Annotation.SUBTYPE_FREE_TEXT, BOUNDS));
            assertInstanceOf(TextAnnotation.class,
                    AnnotationFactory.buildAnnotation(library, Annotation.SUBTYPE_TEXT, BOUNDS));
            assertInstanceOf(PopupAnnotation.class,
                    AnnotationFactory.buildAnnotation(library, Annotation.SUBTYPE_POPUP, BOUNDS));
            assertInstanceOf(RedactionAnnotation.class,
                    AnnotationFactory.buildAnnotation(library, Annotation.SUBTYPE_REDACT, BOUNDS));
            assertInstanceOf(TextMarkupAnnotation.class,
                    AnnotationFactory.buildAnnotation(library,
                            TextMarkupAnnotation.SUBTYPE_HIGHLIGHT, BOUNDS));
        } finally {
            document.dispose();
        }
    }

    @DisplayName("the factory declines a subtype it does not support")
    @Test
    public void factoryUnknownSubtype() throws Exception {
        Document document = openFixture();
        try {
            assertNull(AnnotationFactory.buildAnnotation(document.getCatalog().getLibrary(),
                    new Name("NoSuchSubtype"), BOUNDS));
        } finally {
            document.dispose();
        }
    }
}
