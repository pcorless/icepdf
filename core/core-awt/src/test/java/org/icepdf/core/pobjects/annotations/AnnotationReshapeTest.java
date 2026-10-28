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
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Annotation.reshape: a resize stretches the geometry ink and lines keep in user space with their
 * box; a move (same size) carries it along.
 */
public class AnnotationReshapeTest {

    private Document document;
    private Library library;

    @BeforeEach
    void open() throws Exception {
        document = new Document();
        document.setInputStream(AnnotationReshapeTest.class.getResourceAsStream("/redaction/simple_tj.pdf"),
                "simple_tj.pdf");
        library = document.getCatalog().getLibrary();
    }

    @AfterEach
    void close() {
        document.dispose();
    }

    private InkAnnotation ink() {
        InkAnnotation ink = (InkAnnotation) AnnotationFactory.buildAnnotation(library, Annotation.SUBTYPE_INK,
                new Rectangle(100, 100, 100, 50));
        ink.setBorderStyle(new BorderStyle());
        ink.setColor(Color.RED);
        GeneralPath path = new GeneralPath();
        path.moveTo(110, 110);
        path.lineTo(190, 140);
        ink.setInkPath(path);
        ink.resetAppearanceStream(new AffineTransform());
        return ink;
    }

    @DisplayName("ink: doubling the box doubles the strokes, anchored to the box")
    @Test
    void inkStretches() {
        InkAnnotation ink = ink();
        ink.reshape(new Rectangle2D.Double(100, 100, 200, 100), 0, 0, new AffineTransform());
        Rectangle2D strokes = ink.getInkPath().getBounds2D();
        assertEquals(120, strokes.getX(), 0.01);
        assertEquals(120, strokes.getY(), 0.01);
        assertEquals(160, strokes.getWidth(), 0.01);
        assertEquals(60, strokes.getHeight(), 0.01);
        assertEquals(new Rectangle2D.Float(100, 100, 200, 100), ink.getUserSpaceRectangle());
    }

    @DisplayName("ink: a move carries the strokes; the gesture delta isn't applied on top")
    @Test
    void inkMoves() {
        InkAnnotation ink = ink();
        ink.reshape(new Rectangle2D.Double(130, 80, 100, 50), 30, 20, new AffineTransform());
        Rectangle2D strokes = ink.getInkPath().getBounds2D();
        assertEquals(140, strokes.getX(), 0.01);
        assertEquals(90, strokes.getY(), 0.01);
        assertEquals(80, strokes.getWidth(), 0.01);
    }

    @DisplayName("line: its ends stretch with the box")
    @Test
    void lineStretches() {
        LineAnnotation line = (LineAnnotation) AnnotationFactory.buildAnnotation(library, Annotation.SUBTYPE_LINE,
                new Rectangle(100, 100, 100, 100));
        line.setBorderStyle(new BorderStyle());
        line.setColor(Color.BLUE);
        line.setStartOfLine(new Point2D.Double(110, 110));
        line.setEndOfLine(new Point2D.Double(190, 190));
        line.resetAppearanceStream(new AffineTransform());
        line.reshape(new Rectangle2D.Double(100, 100, 50, 200), 0, 0, new AffineTransform());
        assertEquals(new Point2D.Double(105, 120), line.getStartOfLine());
        assertEquals(new Point2D.Double(145, 280), line.getEndOfLine());
    }

    @DisplayName("other annotations: reshape is set /Rect, then reset with the delta")
    @Test
    void squareSetsRect() {
        SquareAnnotation square = (SquareAnnotation) AnnotationFactory.buildAnnotation(library,
                Annotation.SUBTYPE_SQUARE, new Rectangle(100, 100, 100, 50));
        square.setBorderStyle(new BorderStyle());
        square.setColor(Color.GREEN);
        square.resetAppearanceStream(new AffineTransform());
        square.reshape(new Rectangle2D.Double(50, 60, 70, 80), 0, 0, new AffineTransform());
        assertEquals(new Rectangle2D.Float(50, 60, 70, 80), square.getUserSpaceRectangle());
    }
}
