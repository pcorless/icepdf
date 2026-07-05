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

import org.icepdf.core.pobjects.graphics.RasterOps.IccCmykRasterOp;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the raster-level subtractive contract of {@link CmykGroupCompositor}
 * (GH-501 Phase 2, step 1): genuine black ink survives the blend, the final
 * convert matches the decoder's ICC transform, and the contribution carries the
 * group alpha {@code ca*ag}.
 */
public class CmykGroupCompositorTest {

    /** ICC sRGB of an interleaved CMYK ink buffer, the reference the compositor
     *  must match for a no-op blend (Multiply over white). */
    private static int[] iccRgb(byte[] ink, int w, int h) {
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Raster src = Raster.createInterleavedRaster(
                new DataBufferByte(ink, ink.length), w, h, w * 4, 4,
                new int[]{0, 1, 2, 3}, null);
        new IccCmykRasterOp(null).filter(src, rgb.getRaster());
        return ((DataBufferInt) rgb.getRaster().getDataBuffer()).getData().clone();
    }

    /** Multiply over a white (no-ink) backdrop is the identity in CMYK, so the
     *  blended ink equals the source ink and the result must equal a direct ICC
     *  convert of that ink -- proving the K channel flows through untouched. */
    @Test
    public void multiplyOverWhiteMatchesIccConvertIncludingBlack() {
        int w = 4, h = 1;
        byte[] ink = new byte[]{
                // C   M    Y    K
                (byte) 0, (byte) 0, (byte) 0, (byte) 255,      // pure black ink
                (byte) 0, (byte) 0, (byte) 200, (byte) 30,     // inky orange with some K
                (byte) 255, (byte) 0, (byte) 0, (byte) 0,      // cyan, no K
                (byte) 40, (byte) 60, (byte) 10, (byte) 120,   // muddy mid with K
        };
        int[] alpha = {255, 255, 255, 255};
        int[] backdrop = {0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF};

        BufferedImage out = CmykGroupCompositor.compose(
                ink, alpha, backdrop, BlendComposite.BlendingMode.MULTIPLY, 1f, w, h);
        int[] got = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
        int[] expected = iccRgb(ink, w, h);

        for (int i = 0; i < w; i++) {
            assertEquals(expected[i] & 0xFFFFFF, got[i] & 0xFFFFFF,
                    () -> "Multiply-over-white must equal the unblended ICC convert (K preserved)");
            assertEquals(0xFF, got[i] >>> 24, "full ca*ag -> opaque contribution");
        }
        // The pure-black-ink pixel must actually be (near) black, not washed to a
        // colour -- this is the property the sRGB path could never deliver.
        int black = got[0];
        assertTrue(((black >> 16) & 0xFF) < 60 && ((black >> 8) & 0xFF) < 60 && (black & 0xFF) < 60,
                () -> String.format("K=255 ink must stay dark, got %06X", black & 0xFFFFFF));
    }

    /** The contribution alpha is round(ca*ag); ag==0 contributes nothing. */
    @Test
    public void contributionAlphaIsCaTimesAg() {
        int w = 3, h = 1;
        byte[] ink = new byte[]{
                10, 20, 30, 40,
                10, 20, 30, 40,
                10, 20, 30, 40,
        };
        int[] alpha = {255, 128, 0};
        int[] backdrop = {0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF};
        BufferedImage out = CmykGroupCompositor.compose(
                ink, alpha, backdrop, BlendComposite.BlendingMode.MULTIPLY, 0.5f, w, h);
        int[] got = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
        assertEquals(128, got[0] >>> 24, "ca=0.5 * ag=255 -> 128");
        assertEquals(64, got[1] >>> 24, "ca=0.5 * ag=128 -> 64");
        assertEquals(0, got[2] >>> 24, "ag=0 -> no contribution");
    }
}
