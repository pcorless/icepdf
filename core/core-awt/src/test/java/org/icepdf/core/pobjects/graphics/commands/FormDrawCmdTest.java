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

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the degenerate soft-mask guard ({@link FormDrawCmd#maskContributesNothing})
 * behind the GH-501 follow-up "Java Magazine cover" fix.  A luminosity mask that
 * renders to zero everywhere (its own mask group's bbox differed from the content
 * and the oversized-buffer clamp put the mask content off-buffer) would erase the
 * whole group -- e.g. the cover's title-dimming box vanished.  Such a mask is a
 * misrender, so the guard flags it and the caller keeps the unmasked content.
 * A mask carrying any luminosity is a real effect and must not be flagged.
 */
public class FormDrawCmdTest {

    /** A fully transparent (zero-luminosity) mask buffer contributes nothing and
     *  must be flagged, so the group's content is drawn unmasked instead of erased. */
    @Test
    public void fullyTransparentMaskIsFlaggedAsContributingNothing() {
        BufferedImage emptyMask = new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB);
        // freshly created ARGB buffer is all 0x00000000 -> zero red channel everywhere
        assertTrue(FormDrawCmd.maskContributesNothing(emptyMask),
                "an all-transparent mask should be flagged (would erase the group)");
    }

    /** An opaque-black mask (zero luminosity but fully opaque) still contributes
     *  nothing, because applyExplicitSMask reads the red channel as the weight. */
    @Test
    public void opaqueBlackMaskIsFlaggedAsContributingNothing() {
        BufferedImage blackMask = new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = blackMask.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 64, 48);
        g.dispose();
        assertTrue(FormDrawCmd.maskContributesNothing(blackMask),
                "a zero-luminosity (black) mask should be flagged");
    }

    /** A mask with any non-zero luminosity is a genuine effect and must NOT be
     *  flagged -- even a single lit pixel keeps the mask (early-exit path). */
    @Test
    public void maskWithLuminosityIsNotFlagged() {
        BufferedImage mask = new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB);
        // one white pixel -> non-zero red -> real mask
        mask.setRGB(40, 30, 0xFFFFFFFF);
        assertFalse(FormDrawCmd.maskContributesNothing(mask),
                "a mask with any luminosity must be applied, not skipped");
    }

    /** A partial (gradient) mask -- the common valid case (a fade) -- must not be
     *  flagged. */
    @Test
    public void gradientMaskIsNotFlagged() {
        BufferedImage mask = new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = mask.createGraphics();
        g.setPaint(new java.awt.GradientPaint(0, 0, Color.BLACK, 64, 0, Color.WHITE));
        g.fillRect(0, 0, 64, 48);
        g.dispose();
        assertFalse(FormDrawCmd.maskContributesNothing(mask),
                "a black->white gradient mask carries luminosity and must be kept");
    }

    /** Null defensively returns false (nothing to skip). */
    @Test
    public void nullMaskIsNotFlagged() {
        assertFalse(FormDrawCmd.maskContributesNothing(null));
    }
}
