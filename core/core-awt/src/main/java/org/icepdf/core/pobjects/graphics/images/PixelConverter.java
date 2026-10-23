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
package org.icepdf.core.pobjects.graphics.images;

import org.icepdf.core.pobjects.graphics.DeviceCMYK;
import org.icepdf.core.pobjects.graphics.DeviceGray;
import org.icepdf.core.pobjects.graphics.PColorSpace;
import org.icepdf.core.pobjects.graphics.RasterOps.SampleDecode;

/**
 * Turns one pixel's samples into an ARGB value, for {@link RawDecoder}.  One converter is picked per image, so
 * the per-pixel work never re-tests the colour space or the bit depth.
 *
 * @since 7.5.0
 */
abstract class PixelConverter {

    /**
     * Reads the next pixel's samples.
     *
     * @param in the image's samples, positioned at the pixel.
     * @return the pixel's ARGB.
     */
    abstract int next(SampleReader in);

    /**
     * @param colorSpace       the image's colour space.
     * @param bitsPerComponent bits per sample.
     * @param decode           the image's Decode array.
     * @param imageMask        true for a stencil mask.
     * @param fillRgb          the fill colour a stencil mask paints.
     * @param pixelCount       pixels in the image.
     * @param cmykCapture      receives each pixel's CMYK samples (GH-501), or null.
     */
    static PixelConverter forImage(PColorSpace colorSpace, int bitsPerComponent, SampleDecode decode,
                                   boolean imageMask, int fillRgb, int pixelCount, byte[] cmykCapture) {
        if (imageMask) {
            return new MaskPixelConverter(bitsPerComponent, decode, fillRgb);
        }
        if (colorSpace instanceof DeviceGray) {
            return new GrayPixelConverter(bitsPerComponent, decode);
        }
        return new ColourSpacePixelConverter(colorSpace, bitsPerComponent, decode, pixelCount, cmykCapture);
    }

    /**
     * Whether a GH-501 CMYK capture applies: an 8-bit DeviceCMYK image while CMYK samples are being preserved.
     */
    static boolean capturesCmyk(PColorSpace colorSpace, int bitsPerComponent) {
        return ImageUtility.isPreserveCmyk() && bitsPerComponent == 8 && colorSpace instanceof DeviceCMYK;
    }
}
