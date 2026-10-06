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

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ContentParser}: what a content stream leaves behind in the graphics state and in
 * the {@link Shapes} list it builds.
 * <p>
 * Rendering is not exercised here - only the interpretation of the operators, which is where a
 * content stream's meaning is decided.  The graphics state after a parse is the cheapest honest
 * assertion available: it is the state every draw command was issued under, so a wrong CTM, a
 * wrong colour or an unbalanced {@code q}/{@code Q} shows up in it directly.
 */
public class ContentParserTest {

    /**
     * Parses the content and returns the parser, so both the shapes and the final graphics state
     * are available to assert against.
     */
    private static ContentParser parse(String content) throws Exception {
        Library library = new Library();
        Resources resources = new Resources(library, new DictionaryEntries());
        ContentParser parser = new ContentParser(library, resources);
        parser.parse(streamsOf(content), null);
        return parser;
    }

    private static Stream[] streamsOf(String content) {
        return Stream.fromByteArray(content.getBytes(StandardCharsets.ISO_8859_1),
                new Dictionary(new Library(), new DictionaryEntries()));
    }

    // ------------------------------------------------------------------
    // colour
    // ------------------------------------------------------------------

    @DisplayName("colour - rg and RG set the fill and stroke colours independently")
    @Test
    public void deviceRgb() throws Exception {
        GraphicsState state = parse("1 0 0 rg 0 0 1 RG").getGraphicsState();
        assertEquals(Color.RED, state.getFillColor());
        assertEquals(Color.BLUE, state.getStrokeColor());
    }

    @DisplayName("colour - g and G set a grey")
    @Test
    public void deviceGray() throws Exception {
        GraphicsState state = parse("0 g 1 G").getGraphicsState();
        assertEquals(Color.BLACK, state.getFillColor());
        assertEquals(Color.WHITE, state.getStrokeColor());
    }

    @DisplayName("colour - k converts CMYK to something paintable")
    @Test
    public void deviceCmyk() throws Exception {
        // 0 0 0 1 is full black ink; the exact conversion is the colour space's business, but it
        // has to be dark and it has to be set.
        Color fill = parse("0 0 0 1 k").getGraphicsState().getFillColor();
        assertNotNull(fill);
        assertTrue(fill.getRed() < 64 && fill.getGreen() < 64 && fill.getBlue() < 64,
                "full black ink should convert to a dark colour, was " + fill);
    }

    // ------------------------------------------------------------------
    // the graphics state stack
    // ------------------------------------------------------------------

    @DisplayName("q and Q - the stack restores the colour set before the save")
    @Test
    public void saveAndRestore() throws Exception {
        GraphicsState state = parse("1 0 0 rg q 0 1 0 rg Q").getGraphicsState();
        assertEquals(Color.RED, state.getFillColor(), "Q should have restored the red fill");
    }

    @DisplayName("q and Q - nesting restores one level at a time")
    @Test
    public void nestedSaveAndRestore() throws Exception {
        GraphicsState state = parse("1 0 0 rg q 0 1 0 rg q 0 0 1 rg Q Q").getGraphicsState();
        assertEquals(Color.RED, state.getFillColor());
    }

    @DisplayName("q and Q - an unbalanced Q does not throw")
    @Test
    public void unbalancedRestore() throws Exception {
        // Producers do emit a stray Q; the page still has to parse.
        assertNotNull(parse("Q Q 1 0 0 rg").getGraphicsState());
    }

    // ------------------------------------------------------------------
    // the current transform
    // ------------------------------------------------------------------

    @DisplayName("cm - concatenates onto the current transform")
    @Test
    public void concatenateMatrix() throws Exception {
        AffineTransform ctm = parse("2 0 0 2 10 20 cm").getGraphicsState().getCTM();
        assertEquals(2.0, ctm.getScaleX(), 0.0001);
        assertEquals(2.0, ctm.getScaleY(), 0.0001);
        assertEquals(10.0, ctm.getTranslateX(), 0.0001);
        assertEquals(20.0, ctm.getTranslateY(), 0.0001);
    }

    @DisplayName("cm - two matrices multiply rather than replace")
    @Test
    public void concatenatedMatricesMultiply() throws Exception {
        AffineTransform ctm = parse("2 0 0 2 0 0 cm 3 0 0 3 0 0 cm").getGraphicsState().getCTM();
        assertEquals(6.0, ctm.getScaleX(), 0.0001);
    }

    @DisplayName("cm - a transform inside q/Q is undone by the Q")
    @Test
    public void matrixRestoredByQ() throws Exception {
        AffineTransform ctm = parse("q 5 0 0 5 0 0 cm Q").getGraphicsState().getCTM();
        assertEquals(1.0, ctm.getScaleX(), 0.0001);
    }

    // ------------------------------------------------------------------
    // stroke parameters
    // ------------------------------------------------------------------

    @DisplayName("stroke - width, cap, join, miter limit and dash pattern")
    @Test
    public void strokeParameters() throws Exception {
        GraphicsState state = parse("3 w 1 J 1 j 5 M [4 2] 1 d").getGraphicsState();
        assertEquals(3f, state.getLineWidth(), 0.0001f);
        assertEquals(1, state.getLineCap());
        assertEquals(1, state.getLineJoin());
        assertEquals(5f, state.getMiterLimit(), 0.0001f);
        assertNotNull(state.getDashArray());
        assertEquals(4f, state.getDashArray()[0], 0.0001f);
        assertEquals(1f, state.getDashPhase(), 0.0001f);
    }

    @DisplayName("stroke - a dash array of all zeros is treated as solid")
    @Test
    public void zeroDashArray() throws Exception {
        // A zero-length dash would make a stroke invisible, and some producers write one meaning
        // "solid"; the parser normalises it rather than passing it to Java2D.
        GraphicsState state = parse("[0] 0 d").getGraphicsState();
        assertNull(state.getDashArray());
    }

    // ------------------------------------------------------------------
    // paths and clipping
    // ------------------------------------------------------------------

    @DisplayName("paths - a filled rectangle becomes a draw command")
    @Test
    public void filledRectangle() throws Exception {
        Shapes shapes = parse("1 0 0 rg 10 20 100 200 re f").getShapes();
        assertTrue(shapes.getShapesCount() > 0, "a filled rectangle should add draw commands");
    }

    @DisplayName("paths - m/l/h build a closed path that can be stroked")
    @Test
    public void strokedPath() throws Exception {
        Shapes shapes = parse("0 0 m 100 0 l 100 100 l h S").getShapes();
        assertTrue(shapes.getShapesCount() > 0);
    }

    @DisplayName("paths - the curve operators are accepted")
    @Test
    public void curveOperators() throws Exception {
        // c, v and y are three spellings of the same cubic; all three have to parse.
        assertTrue(parse("0 0 m 10 10 20 20 30 30 c S").getShapes().getShapesCount() > 0);
        assertTrue(parse("0 0 m 20 20 30 30 v S").getShapes().getShapesCount() > 0);
        assertTrue(parse("0 0 m 10 10 30 30 y S").getShapes().getShapesCount() > 0);
    }

    @DisplayName("clipping - W n sets the clip to the path just built")
    @Test
    public void clipPath() throws Exception {
        Shape clip = parse("10 20 100 200 re W n").getGraphicsState().getClip();
        assertNotNull(clip, "W n should have set a clip");
        Rectangle2D bounds = clip.getBounds2D();
        assertEquals(10.0, bounds.getX(), 0.5);
        assertEquals(20.0, bounds.getY(), 0.5);
        assertEquals(100.0, bounds.getWidth(), 0.5);
        assertEquals(200.0, bounds.getHeight(), 0.5);
    }

    @DisplayName("clipping - a clip inside q/Q is released by the Q")
    @Test
    public void clipRestoredByQ() throws Exception {
        assertNull(parse("q 10 10 10 10 re W n Q").getGraphicsState().getClip());
    }

    // ------------------------------------------------------------------
    // robustness
    // ------------------------------------------------------------------

    @DisplayName("robustness - an operator missing its operands does not abort the stream")
    @Test
    public void missingOperands() throws Exception {
        // "rg" with two numbers instead of three is malformed; the rest of the page must still be
        // interpreted, because the alternative is a blank page.
        GraphicsState state = parse("1 0 rg 3 w").getGraphicsState();
        assertEquals(3f, state.getLineWidth(), 0.0001f,
                "the operator after a malformed one should still have been applied");
    }

    @DisplayName("robustness - an empty content stream parses to nothing")
    @Test
    public void emptyStream() throws Exception {
        assertEquals(0, parse("").getShapes().getShapesCount());
    }

    @DisplayName("robustness - a stream of only whitespace and comments parses to nothing")
    @Test
    public void commentOnlyStream() throws Exception {
        assertEquals(0, parse("% nothing to see\n   \n").getShapes().getShapesCount());
    }

    @DisplayName("robustness - a Do naming an XObject that does not exist is ignored")
    @Test
    public void missingXObject() throws Exception {
        // A broken /XObject reference is common in damaged files and must not take the page down.
        assertNotNull(parse("q /NoSuchThing Do Q").getShapes());
    }

    @DisplayName("robustness - a gs naming a missing ExtGState is ignored")
    @Test
    public void missingExtGState() throws Exception {
        assertNotNull(parse("/NoSuchState gs 3 w").getGraphicsState());
    }

    @DisplayName("compatibility - BX/EX brackets round unknown operators")
    @Test
    public void compatibilitySection() throws Exception {
        GraphicsState state = parse("BX /Unknown someOp EX 3 w").getGraphicsState();
        assertEquals(3f, state.getLineWidth(), 0.0001f);
    }

    @DisplayName("marked content - BDC/EMC and BMC leave the state untouched")
    @Test
    public void markedContent() throws Exception {
        GraphicsState state = parse("/OC << /MCID 0 >> BDC 3 w EMC /Tag BMC EMC").getGraphicsState();
        assertEquals(3f, state.getLineWidth(), 0.0001f);
    }
}
