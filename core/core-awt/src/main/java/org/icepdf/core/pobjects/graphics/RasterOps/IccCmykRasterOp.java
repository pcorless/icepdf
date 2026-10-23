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
package org.icepdf.core.pobjects.graphics.RasterOps;

import org.icepdf.core.pobjects.graphics.DeviceCMYK;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.*;

/**
 * Raster operation for converting a CMYK colour to RGB using an ICC colour profile.
 * <br>
 * CC Color Profile for colour conversion is very accurate but it's also very slow.  Calls
 * to ColorConvertOp can be very slow.  This class attempts to use a colour cache to speed
 * up decoding on larger images.
 *
 * @since 6.2.3
 */
public class IccCmykRasterOp implements RasterOp {
    private final RenderingHints hints;
    private final ColorSpace colorSpace;

    public IccCmykRasterOp(RenderingHints hints) {
        this.hints = hints;
        this.colorSpace = DeviceCMYK.getIccCmykColorSpace();
    }

    public WritableRaster filter(Raster src, WritableRaster dest) {

        if (dest == null) dest = src.createCompatibleWritableRaster();

        // may have to add some instance of checks
        byte[] srcPixels = ((DataBufferByte) src.getDataBuffer()).getData();
        int[] destPixels = ((DataBufferInt) dest.getDataBuffer()).getData();

        int bands = src.getNumBands();
        float[] colorValue = new float[bands];

        float[] rgbColorValue;

        // ICC colorSpace.toRGB is a per-pixel native round-trip and is by far the
        // most expensive step here.  Flat regions (common in scanned CMYK pages)
        // repeat the same sample, so skip the conversion when the four CMYK bytes
        // match the previous pixel and reuse the last computed ARGB.  Photos rarely
        // repeat their neighbour but reuse far fewer colours than they have pixels
        // (Mass 3D 8.pdf: 161k colours in 12.8M pixels), so every converted colour is
        // also remembered.  A whole-raster ColorConvertOp was tried instead: barely
        // faster, and it renders with a different intent (up to 35 levels off).
        ColourMemo memo = new ColourMemo(destPixels.length, 32);
        int lastCmyk = 0;
        int lastArgb = 0;
        for (int pixel = 0, intPixels = 0; pixel < srcPixels.length; pixel += bands, intPixels++) {

            int cmyk = (srcPixels[pixel] & 0xff) << 24 |
                    (srcPixels[pixel + 1] & 0xff) << 16 |
                    (srcPixels[pixel + 2] & 0xff) << 8 |
                    (srcPixels[pixel + 3] & 0xff);

            if (cmyk != lastCmyk || lastArgb == 0) {
                int slot = memo.slot(cmyk);
                lastArgb = memo.argb(slot);
                if (lastArgb == 0) {
                    colorValue[0] = (cmyk >>> 24) / 255.0f;
                    colorValue[1] = (cmyk >>> 16 & 0xff) / 255.0f;
                    colorValue[2] = (cmyk >>> 8 & 0xff) / 255.0f;
                    colorValue[3] = (cmyk & 0xff) / 255.0f;

                    rgbColorValue = colorSpace.toRGB(colorValue);

                    lastArgb = (0xff << 24) |
                            (((int) (rgbColorValue[0] * 255) & 0xff) << 16) |
                            (((int) (rgbColorValue[1] * 255) & 0xff) << 8) |
                            ((int) (rgbColorValue[2] * 255) & 0xff);
                    memo.put(slot, cmyk, lastArgb);
                }
                lastCmyk = cmyk;
            }
            destPixels[intPixels] = lastArgb;
        }
        return dest;
    }

    public Rectangle2D getBounds2D(Raster src) {
        return null;
    }

    public WritableRaster createCompatibleDestRaster(Raster src) {
        return src.createCompatibleWritableRaster();
    }

    public Point2D getPoint2D(Point2D srcPt, Point2D dstPt) {
        if (dstPt == null)
            dstPt = (Point2D) srcPt.clone();
        else
            dstPt.setLocation(srcPt);
        return dstPt;
    }

    public RenderingHints getRenderingHints() {
        return hints;
    }
}
