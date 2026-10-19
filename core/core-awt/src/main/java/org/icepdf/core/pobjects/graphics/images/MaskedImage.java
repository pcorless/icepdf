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
package org.icepdf.core.pobjects.graphics.images;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.Raster;

/**
 * An image with an explicit stencil {@code /Mask} (PDF 8.9.6.3) whose two parts are kept apart until paint
 * time, each at its own resolution.
 * <p>
 * The base and its mask need not share a resolution, and a scan typically pairs a low resolution colour
 * layer with a 1 bit mask four times finer.  Combining them up front means one ARGB raster at the finer of
 * the two (5375x7350 for docGris.pdf: 158 MB a page, from a 7 MB colour layer and a 5 MB mask), which the
 * paint then shrinks right back down.  Kept apart, the paint combines them only over the device pixels it
 * actually draws.
 * <p>
 * This is a {@link BufferedImage} only so it can travel the {@code BufferedImage} typed decode and pool
 * path; as a {@code BufferedImage} it is the <em>unmasked</em> base.  Whatever is not the paint path must
 * take {@link #composite()} instead, and {@link ImageStream#getImage} and the draw command already do.
 *
 * @since 7.5
 */
public final class MaskedImage extends BufferedImage {

    private final BufferedImage base;
    private final BufferedImage stencil;

    /**
     * @param base    the decoded image
     * @param stencil the mask, one bit a pixel: black paints the base, white leaves the page alone
     */
    MaskedImage(BufferedImage base, BufferedImage stencil) {
        super(base.getColorModel(), base.getRaster(), base.isAlphaPremultiplied(), null);
        this.base = base;
        this.stencil = stencil;
    }

    /**
     * @return the base image, without the mask
     */
    public BufferedImage getBase() {
        return base;
    }

    /**
     * @return the mask, {@code TYPE_BYTE_BINARY}: black where the base is painted, white where it is not
     */
    public BufferedImage getStencil() {
        return stencil;
    }

    /**
     * The two parts combined into one ARGB image, as the decode used to produce them, for anything that
     * wants the pixels rather than a paint.  Built on every call and not kept.
     *
     * @return a new image at the finer of the two resolutions
     */
    public BufferedImage composite() {
        // applyExplicitMask writes into a base that has alpha; this one is kept, so it gets a copy
        BufferedImage source = base;
        if (base.getColorModel().hasAlpha()) {
            source = new BufferedImage(base.getColorModel(), base.copyData(null), base.isAlphaPremultiplied(), null);
        }
        // the stencil's black/white is exactly what applyExplicitMask reads as paint/leave
        return ImageUtility.applyExplicitMask(source, stencil);
    }

    /**
     * Reduces a decoded {@code /Mask} image to a one bit stencil, with the same reading of its pixels as
     * {@link ImageUtility#applyExplicitMask(BufferedImage, BufferedImage)}: transparent black, opaque white
     * and transparent white leave the page alone, anything else paints.
     *
     * @param mask decoded mask image
     * @return {@code TYPE_BYTE_BINARY} stencil the size of {@code mask}, black where the base is painted
     */
    static BufferedImage toStencil(BufferedImage mask) {
        int w = mask.getWidth();
        int h = mask.getHeight();
        BufferedImage stencil = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
        byte[] out = ((DataBufferByte) stencil.getRaster().getDataBuffer()).getData();
        int stride = (w + 7) / 8;
        int leave = mask.getColorModel().getNumComponents() == 4 ? 0xffffff : -1;

        // A one bit decode (CCITT, JBIG2) is already a stencil, perhaps inverted: copy its rows.
        if (mask.getColorModel() instanceof IndexColorModel && copyPackedBits(mask, out, stride, leave)) {
            return stencil;
        }
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            mask.getRGB(0, y, w, 1, row, 0, w);
            int o = y * stride;
            for (int x = 0; x < w; x++) {
                if (leaves(row[x], leave)) {
                    out[o + (x >> 3)] |= (byte) (0x80 >> (x & 7));
                }
            }
        }
        return stencil;
    }

    private static boolean leaves(int rgb, int leave) {
        return rgb == 0 || rgb == leave || rgb == 0xffffff;
    }

    private static boolean copyPackedBits(BufferedImage mask, byte[] out, int stride, int leave) {
        Raster raster = mask.getRaster();
        if (!(raster.getSampleModel() instanceof MultiPixelPackedSampleModel)
                || !(raster.getDataBuffer() instanceof DataBufferByte)
                || raster.getParent() != null) {
            return false;
        }
        MultiPixelPackedSampleModel sm = (MultiPixelPackedSampleModel) raster.getSampleModel();
        IndexColorModel icm = (IndexColorModel) mask.getColorModel();
        if (sm.getPixelBitStride() != 1 || sm.getDataBitOffset() != 0 || icm.getMapSize() < 2) {
            return false;
        }
        boolean leave0 = leaves(icm.getRGB(0), leave);
        boolean leave1 = leaves(icm.getRGB(1), leave);
        if (leave0 == leave1) {
            return false;
        }
        byte[] in = ((DataBufferByte) raster.getDataBuffer()).getData();
        int inStride = sm.getScanlineStride();
        int offset = raster.getDataBuffer().getOffset();
        int h = mask.getHeight();
        // the stencil's bit 1 (white) leaves: a source whose index 1 leaves copies straight across
        for (int y = 0; y < h; y++) {
            int i = offset + y * inStride;
            int o = y * stride;
            if (leave1) {
                System.arraycopy(in, i, out, o, stride);
            } else {
                for (int x = 0; x < stride; x++) {
                    out[o + x] = (byte) ~in[i + x];
                }
            }
        }
        return true;
    }
}
