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
package org.icepdf.core.pobjects.graphics.commands;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.graphics.OptionalContentState;
import org.icepdf.core.pobjects.graphics.PaintTimer;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.SoftMask;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression pin for the GH-501 follow-up "faded conference-photo" bug: a
 * page-level {@code sh} shading fill carrying a <i>luminosity</i> soft mask
 * whose group has its own {@code cm} (a y-flip) white-washed the image beneath
 * it instead of fading it.
 * <p>
 * Cause: {@link ShadingSoftMaskDrawCmd#renderLuminosityMask} clips the mask
 * render to the fill region.  For an {@code sh} fill the render transform has the
 * mask group's own {@code cm} undone, so applying the region clip under that
 * transform placed it entirely off the mask buffer -- the gradient was clipped
 * away and only the {@code BC} backdrop survived.  With a white {@code BC} the
 * mask read as fully opaque, so the (white) shading covered the whole image.
 * <p>
 * The fix maps the clip through {@code translate(-device) . ctm} (device space)
 * so it stays aligned to the buffer regardless of the mask group's {@code cm}.
 * This test drives {@link ShadingSoftMaskDrawCmd#paintOperand} over a red
 * background and asserts the masked middle stays red (mask transparent there) --
 * before the fix the whole region came back opaque white.
 */
public class ShadingSoftMaskDrawCmdTest {

    /**
     * A vertical white->black->white luminosity mask (transparent middle) with a
     * white BC and its own y-flip cm must fade an {@code sh} white fill so the
     * content beneath shows through the middle, not be washed to solid white.
     */
    @Test
    public void luminosityMaskWithGroupCmFadesInsteadOfWashingWhite() {
        Library library = new Library();

        // Fill region in the sh/f user space (the mask group's coordinate space).
        final Rectangle2D fillRect = new Rectangle2D.Double(0, -100, 200, 100);

        // Mask group content: FIRST a y-flip cm (the trigger for the render
        // transform's cm-undo), then a vertical white->black->white gradient fill
        // over the region.  Luminosity: white edges -> opaque, black middle ->
        // transparent.
        final Shapes maskShapes = new Shapes();
        maskShapes.add(new TransformDrawCmd(new AffineTransform(1, 0, 0, -1, 0, 0)));
        maskShapes.add(new PaintDrawCmd(new LinearGradientPaint(
                new Point2D.Double(100, -100), new Point2D.Double(100, 0),
                new float[]{0f, 0.5f, 1f},
                new Color[]{Color.WHITE, Color.BLACK, Color.WHITE})));
        maskShapes.add(new ShapeDrawCmd(fillRect));
        maskShapes.add(new FillDrawCmd());

        Form maskForm = new Form(library, new DictionaryEntries(), (byte[]) null) {
            @Override
            public Shapes getShapes() {
                return maskShapes;
            }

            @Override
            public Rectangle2D getBBox() {
                return fillRect;
            }
        };

        SoftMask softMask = new SoftMask(library, new DictionaryEntries()) {
            @Override
            public Name getS() {
                return new Name(SoftMask.SOFT_MASK_TYPE_LUMINOSITY);
            }

            @Override
            public Form getG() {
                return maskForm;
            }

            @Override
            public List<Number> getBC() {
                return Collections.singletonList((Number) 1.0f); // white backdrop
            }
        };

        // `sh` fill: opaque white shading modulated by the luminosity mask.
        ShadingSoftMaskDrawCmd cmd = new ShadingSoftMaskDrawCmd(Color.WHITE, softMask, 1f);

        // Target: red page.  ctm maps the region's y -100..0 to device y 200..300.
        int w = 200, h = 300;
        BufferedImage target = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, w, h);
        AffineTransform ctm = new AffineTransform(1, 0, 0, 1, 0, 300);
        g.setTransform(ctm);
        g.setClip(fillRect);

        try {
            cmd.paintOperand(g, null, null, fillRect, new AffineTransform(),
                    new OptionalContentState(), true, new PaintTimer());
        } catch (Exception e) {
            throw new AssertionError("paintOperand threw", e);
        } finally {
            g.dispose();
        }

        // Middle of the region (device y=250 == mask black == transparent): the
        // red page must show through.  Before the fix this was opaque white.
        int mid = target.getRGB(100, 250);
        int mr = (mid >> 16) & 0xff, mg = (mid >> 8) & 0xff, mb = mid & 0xff;
        assertTrue(mr > 200 && mg < 90 && mb < 90,
                "masked middle should stay red (mask transparent), was #" + Integer.toHexString(mid));

        // Top edge (device y=201 == mask white == opaque): the white shading must
        // paint here, proving the masked overlay actually rendered (not skipped).
        int edge = target.getRGB(100, 201);
        int er = (edge >> 16) & 0xff, eg = (edge >> 8) & 0xff, eb = edge & 0xff;
        assertTrue(er > 200 && eg > 200 && eb > 200,
                "masked edge should be opaque white, was #" + Integer.toHexString(edge));
    }
}
