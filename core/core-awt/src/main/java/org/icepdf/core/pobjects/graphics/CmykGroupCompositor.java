/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * Raster-level subtractive compositor for a DeviceCMYK transparency group
 * (GH-501 Phase 2, step 1).
 * <p>
 * The sRGB path in {@code FormDrawCmd.composeContribution} cannot blend a CMYK
 * group subtractively: once the group's content is decoded to sRGB the black (K)
 * channel is gone, and recovering CMYK from sRGB collapses straight back to an
 * additive RGB blend (proven against three reference renderers -- the
 * sRGB-recovered "subtractive" path fabricated a K channel and shifted
 * {@code pattern_and_CYMK_jpeg.pdf} orange&rarr;green).  This compositor instead
 * works from the <b>true</b> preserved CMYK samples
 * ({@link org.icepdf.core.pobjects.graphics.images.ImageUtility#getCmykSamples}),
 * so genuine black ink flows into the §11.3.5 blend.
 * <p>
 * The flow, per pixel, mirrors {@code composeContribution} but in the ink domain:
 * <ol>
 *   <li>source ink {@code Cs} = the true CMYK sample (real K);</li>
 *   <li>backdrop ink {@code Cb} = the reconstructed page backdrop, recovered from
 *       sRGB with no black ({@link CmykBlendingSpace#fromSRGB}) -- the backdrop
 *       came from an additive render, so K=0 is the honest value for it, and the
 *       fabricated-K hazard never applied to the backdrop, only the source;</li>
 *   <li>blend {@code B(Cb,Cs)} via {@link CmykBlendingSpace#separable};</li>
 *   <li>convert the blended ink to sRGB <b>once</b> at the end via
 *       {@link IccCmykRasterOp} -- the same ICC transform the decoder uses for an
 *       unblended CMYK image, so a Normal region and a blended region of the same
 *       image stay colour-consistent.</li>
 * </ol>
 * The result is the group's contribution {@code B(Cb,Cs)} at alpha {@code ca*ag},
 * to be drawn back SRC_OVER over the page (= {@code Cb}), yielding the §11.4.6
 * result {@code (1-ca*ag)*Cb + ca*ag*B(Cb,Cs)} with no backdrop double-count --
 * the same draw-back contract as the sRGB path.
 *
 * @see CmykBlendingSpace
 * @see IccCmykRasterOp
 */
public final class CmykGroupCompositor {

    private static final CmykBlendingSpace CMYK = CmykBlendingSpace.INSTANCE;

    private CmykGroupCompositor() {
    }

    /**
     * Composites a CMYK group contribution at the raster level.  All inputs are
     * pre-aligned to the {@code w x h} group buffer.
     *
     * @param srcInk       interleaved C,M,Y,K ink (length {@code 4*w*h}, 0..255) --
     *                     the TRUE preserved samples of the group's content.
     * @param srcAlpha     group coverage {@code ag} per pixel (length {@code w*h},
     *                     0..255); a pixel with {@code ag==0} contributes nothing.
     * @param backdropArgb reconstructed page backdrop {@code Cb} (length
     *                     {@code w*h}, packed ARGB).
     * @param mode         the group's separable blend mode.
     * @param ca           the group's constant alpha (0..1).
     * @param w            buffer width.
     * @param h            buffer height.
     * @return the contribution buffer (TYPE_INT_ARGB): colour {@code B(Cb,Cs)} at
     * alpha {@code ca*ag}, to be drawn back with SRC_OVER.
     */
    public static BufferedImage compose(byte[] srcInk, int[] srcAlpha, int[] backdropArgb,
                                        BlendComposite.BlendingMode mode, float ca,
                                        int w, int h) {
        int n = w * h;
        // Blended ink, interleaved C,M,Y,K -- fed to the ICC convert in one pass.
        byte[] outInk = new byte[n * 4];
        int[] outAlpha = new int[n];
        double[] cs = new double[4], cb = new double[4];
        for (int i = 0; i < n; i++) {
            int ag = srcAlpha[i] & 0xFF;
            if (ag == 0) {
                continue; // no group contribution -> stays transparent, page shows through
            }
            int p = i * 4;
            cs[0] = srcInk[p] & 0xFF;
            cs[1] = srcInk[p + 1] & 0xFF;
            cs[2] = srcInk[p + 2] & 0xFF;
            cs[3] = srcInk[p + 3] & 0xFF;
            CMYK.fromSRGB(backdropArgb[i], cb); // Cb ink, K=0 (honest for an sRGB backdrop)
            outInk[p] = clampInk(CMYK.separable(mode, cb[0], cs[0]));
            outInk[p + 1] = clampInk(CMYK.separable(mode, cb[1], cs[1]));
            outInk[p + 2] = clampInk(CMYK.separable(mode, cb[2], cs[2]));
            outInk[p + 3] = clampInk(CMYK.separable(mode, cb[3], cs[3]));
            int a = Math.round(ca * ag);
            outAlpha[i] = a < 0 ? 0 : (a > 255 ? 255 : a);
        }
        // Convert the blended ink to sRGB ONCE, through the decoder's ICC transform.
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Raster srcRaster = Raster.createInterleavedRaster(
                new DataBufferByte(outInk, outInk.length), w, h, w * 4, 4,
                new int[]{0, 1, 2, 3}, null);
        new IccCmykRasterOp(null).filter(srcRaster, rgb.getRaster());
        // Overlay the group's own alpha (ICC convert emits opaque 0xff): the
        // contribution carries ca*ag, so SRC_OVER over the page gives §11.4.6.
        int[] argb = ((java.awt.image.DataBufferInt) rgb.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < n; i++) {
            argb[i] = (outAlpha[i] << 24) | (argb[i] & 0x00FFFFFF);
        }
        return rgb;
    }

    private static byte clampInk(double v) {
        int i = (int) Math.round(v);
        return (byte) (i < 0 ? 0 : (i > 255 ? 255 : i));
    }
}
