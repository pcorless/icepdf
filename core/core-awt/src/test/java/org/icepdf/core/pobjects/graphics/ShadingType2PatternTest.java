/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.functions.Function;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link ShadingPattern#anchorToDefaultSpace} behind the GH-501 follow-up
 * shading-pattern fix.  A shading pattern's {@code Matrix} maps pattern space to
 * the page's <i>default</i> coordinate system, and PDF 32000-1 §8.7.3.1 requires
 * the pattern's appearance to be independent of the CTM in effect when it is used
 * as the fill colour.  icepdf paints the gradient under the live graphics
 * transform ({@code base × CTM}) and Java2D concatenates the pattern matrix on
 * top, so the fill-time CTM must be cancelled by pre-multiplying with its inverse
 * -- otherwise a page authored under a {@code 0.1 cm} scale (Java Magazine ads)
 * shrinks the gradient axis ~10x and washes the fill to a single clamped colour.
 */
public class ShadingType2PatternTest {

    private static ShadingType2Pattern newPattern(AffineTransform patternMatrix) {
        // A shading pattern (PatternType 2, used via scn) -- the only case that
        // is anchored to default space.  A bare `sh` shading has no PatternType.
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(ShadingPattern.PATTERN_TYPE_KEY, ShadingPattern.PATTERN_TYPE_SHADING);
        ShadingType2Pattern pattern = new ShadingType2Pattern(new Library(), entries);
        pattern.setMatrix(patternMatrix);
        return pattern;
    }

    private static GraphicsState stateWithCtm(AffineTransform ctm) {
        GraphicsState gs = new GraphicsState((Shapes) null);
        gs.setCTM(ctm);
        return gs;
    }

    /**
     * A non-identity CTM (the 0.1x page scale) must be cancelled: the anchored
     * matrix equals CTM^-1 concatenated with the pattern matrix, so painting it
     * under base×CTM yields base×patternMatrix (the default-space placement).
     */
    @Test
    public void cancelsFillTimeCtmScale() throws Exception {
        AffineTransform patternMatrix = new AffineTransform(0, 26.5, -541, 0, 782, 135);
        ShadingType2Pattern pattern = newPattern(patternMatrix);
        AffineTransform ctm = AffineTransform.getScaleInstance(0.1, 0.1);

        AffineTransform anchored = pattern.anchorToDefaultSpace(patternMatrix, stateWithCtm(ctm));

        AffineTransform expected = ctm.createInverse();
        expected.concatenate(patternMatrix);
        assertEquals(expected, anchored,
                "fill-time CTM must be cancelled so the pattern anchors to default page space");

        // Sanity: base × CTM × anchored == base × patternMatrix for any base.
        AffineTransform base = new AffineTransform(2, 0, 0, -2, 0, 1536);
        AffineTransform live = new AffineTransform(base);
        live.concatenate(ctm);
        live.concatenate(anchored);
        AffineTransform spec = new AffineTransform(base);
        spec.concatenate(patternMatrix);
        assertEquals(spec, live, "effective paint transform must land at base × patternMatrix");
    }

    /**
     * A bare {@code sh} shading (no {@code /PatternType}) paints in the current
     * user space, so its matrix must NOT be anchored even under a non-identity
     * CTM -- cancelling the CTM there collapses the gradient (ICE-15's badge,
     * whose {@code cm} scales a unit gradient up ~87x).  Only shading patterns
     * (PatternType 2, via scn) are anchored.
     */
    @Test
    public void shShadingIsNotAnchored() {
        AffineTransform patternMatrix = new AffineTransform();
        // No PatternType entry -> treated as a direct `sh` shading.
        ShadingType2Pattern shShading = new ShadingType2Pattern(new Library(), new DictionaryEntries());
        shShading.setMatrix(patternMatrix);
        AffineTransform ctm = AffineTransform.getScaleInstance(87, 87);

        assertEquals(patternMatrix, shShading.anchorToDefaultSpace(patternMatrix, stateWithCtm(ctm)),
                "an sh shading must keep the current CTM (matrix returned unchanged)");
    }

    /**
     * An identity CTM (the common case) must leave the pattern matrix unchanged
     * so ordinary shading patterns are unaffected by the fix.
     */
    @Test
    public void identityCtmLeavesMatrixUnchanged() {
        AffineTransform patternMatrix = new AffineTransform(3, 0, 0, 3, 10, 20);
        ShadingType2Pattern pattern = newPattern(patternMatrix);

        AffineTransform anchored = pattern.anchorToDefaultSpace(patternMatrix,
                stateWithCtm(new AffineTransform()));

        assertEquals(patternMatrix, anchored, "identity CTM must be a no-op");
    }

    /**
     * A null graphics state (or null CTM) falls back to the raw pattern matrix.
     */
    @Test
    public void nullStateFallsBackToRawMatrix() {
        AffineTransform patternMatrix = new AffineTransform(3, 0, 0, 3, 10, 20);
        ShadingType2Pattern pattern = newPattern(patternMatrix);

        assertEquals(patternMatrix, pattern.anchorToDefaultSpace(patternMatrix, null),
                "a null graphics state must not alter the matrix");
    }

    /**
     * A degenerate (non-invertible) CTM falls back to the raw matrix rather than
     * throwing, so a zero-scale state cannot break pattern painting.
     */
    @Test
    public void nonInvertibleCtmFallsBackToRawMatrix() {
        AffineTransform patternMatrix = new AffineTransform(3, 0, 0, 3, 10, 20);
        ShadingType2Pattern pattern = newPattern(patternMatrix);
        AffineTransform degenerate = new AffineTransform(0, 0, 0, 0, 0, 0);

        assertEquals(patternMatrix, pattern.anchorToDefaultSpace(patternMatrix, stateWithCtm(degenerate)),
                "a non-invertible CTM must fall back to the raw pattern matrix");
    }

    /**
     * A near-vertical axis must still produce a gradient.  Sampling the axis by
     * solving y = mx + b collapsed here: the ends differ by 9e-5 in x over 20
     * units of y, so the slope is ~2e5, the intercept b = y0 - m*x0 is ~6e7, and
     * the float sum (m*x) + b cancels away every digit of the result.  All eleven
     * samples then landed off the axis and the gradient degenerated to one flat
     * colour -- the opera mask's white upper lip in shadding-2-3-6.pdf.
     */
    @Test
    public void nearVerticalAxisStillProducesAGradient() {
        ShadingType2Pattern pattern = newPattern(new AffineTransform());
        pattern.colorSpace = PColorSpace.getColorSpace(new Library(), new Name("DeviceGray"));
        pattern.extend = Arrays.asList(false, false);
        pattern.function = new Function[]{rampFunction()};
        // the opera mask's upper-lip axis: 20 units tall, 9.2e-5 units wide.
        Point2D.Float start = new Point2D.Float(293.48584f, 366.00098f);
        Point2D.Float end = new Point2D.Float(293.48575f, 346.00128f);

        Color[] colors = pattern.calculateColorPoints(10, start, end, 0f, 1f);

        assertEquals(11, colors.length);
        assertEquals(0, colors[0].getRed(), "t=0 end of the ramp is black");
        assertEquals(255, colors[10].getRed(), "t=1 end of the ramp is white");
        for (int i = 1; i < colors.length; i++) {
            assertTrue(colors[i].getRed() > colors[i - 1].getRed(),
                    "sample " + i + " must advance along the axis, got " + colors[i - 1] + " -> " + colors[i]);
        }
    }

    /** 1-in 1-out identity ramp, so a sample's colour reports its parametric value. */
    private static Function rampFunction() {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Domain"), Arrays.asList(0, 1));
        return new Function(new Dictionary(new Library(), entries)) {
            @Override
            public float[] calculate(float[] m) {
                return new float[]{m[0]};
            }
        };
    }
}
