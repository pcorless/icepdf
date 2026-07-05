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

import org.icepdf.core.pobjects.graphics.DeviceRGB;
import org.icepdf.core.pobjects.graphics.PColorSpace;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.*;

/**
 * Convert a rgb encoded raster to the specified colour space.
 *
 * @since 5.1
 */
public class PColorSpaceRasterOp implements RasterOp {

    private final RenderingHints hints;
    private final PColorSpace colorSpace;

    public PColorSpaceRasterOp(PColorSpace colorSpace, RenderingHints hints) {
        this.hints = hints;
        this.colorSpace = colorSpace;
    }

    public WritableRaster filter(Raster src, WritableRaster dest) {

        if (dest == null) dest = src.createCompatibleWritableRaster();

        // may have to add some instance of checks
        byte[] srcPixels = ((DataBufferByte) src.getDataBuffer()).getData();
        int[] destPixels = ((DataBufferInt) dest.getDataBuffer()).getData();

        // already RGB not much to do so we just build the colour
        if (colorSpace instanceof DeviceRGB) {
            int bands = src.getNumBands();
            int[] rgbValues = new int[3];
            for (int pixel = 0, intPixels = 0; pixel < srcPixels.length; pixel += bands, intPixels++) {

                rgbValues[0] = (srcPixels[pixel] & 0xff);
                rgbValues[1] = (srcPixels[pixel + 1] & 0xff);
                rgbValues[2] = (srcPixels[pixel + 2] & 0xff);

                // reverse after the normalization to avoid looking gray data as
                // array is trimmed above.
                destPixels[intPixels] = ((rgbValues[0] & 0xff) << 16) |
                        ((rgbValues[1] & 0xff) << 8) |
                        (rgbValues[2] & 0xff);
            }
        } else {
            int bands = src.getNumBands();
            float[] values = new float[3];
            // getColor() can be costly (colour-space conversion plus a Color
            // allocation); reuse the previous result when the raw samples for a
            // pixel are unchanged, which covers the flat runs typical of these
            // images even when the colour space caches internally.
            int[] lastSamples = new int[bands];
            java.util.Arrays.fill(lastSamples, -1);
            int lastRgb = 0;
            boolean haveLast = false;
            for (int pixel = 0, intPixels = 0; pixel < srcPixels.length; pixel += bands, intPixels++) {

                boolean same = haveLast;
                for (int i = 0; i < bands; i++) {
                    int sample = srcPixels[pixel + i] & 0xff;
                    if (sample != lastSamples[i]) {
                        same = false;
                        lastSamples[i] = sample;
                    }
                    values[i] = sample / 255.0f;
                }
                if (!same) {
                    lastRgb = colorSpace.getColor(values).getRGB();
                    haveLast = true;
                }
                destPixels[intPixels] = lastRgb;
            }
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
        if (dstPt == null) {
            dstPt = (Point2D) srcPt.clone();
        } else {
            dstPt.setLocation(srcPt);
        }
        return dstPt;
    }

    public RenderingHints getRenderingHints() {
        return hints;
    }
}
