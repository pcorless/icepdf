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

import org.icepdf.core.pobjects.graphics.PColorSpace;
import org.icepdf.core.pobjects.graphics.RasterOps.ColourMemo;

import java.util.Arrays;

/**
 * Any colour space without a cheaper path: each pixel's decoded components, in the colour space's own units,
 * get their colour from {@link PColorSpace#getColor}.
 * getColor (an ICC toRGB, a tint transform, plus a Color) is the cost, so a pixel whose samples repeat the
 * previous pixel's reuses its colour, and every converted colour is remembered by its packed samples (GH-617).
 *
 * @since 7.5.0
 */
final class ColourSpacePixelConverter extends PixelConverter {
    private final PColorSpace colorSpace;
    private final int bitsPerComponent;
    private final SampleDecode decode;
    private final int[] samples;
    private final int[] lastSamples;
    private final float[] components;
    // null when the samples don't pack into an int: 16-bit, or many components
    private final ColourMemo memo;
    private final byte[] cmykCapture;
    private int lastArgb;
    private int pixel;

    ColourSpacePixelConverter(PColorSpace colorSpace, int bitsPerComponent, SampleDecode decode, int pixelCount,
                              byte[] cmykCapture) {
        this.colorSpace = colorSpace;
        this.bitsPerComponent = bitsPerComponent;
        this.decode = decode;
        this.cmykCapture = cmykCapture;
        int count = colorSpace.getNumComponents();
        samples = new int[count];
        // -1 is an impossible sample, so the first pixel always converts
        lastSamples = new int[count];
        Arrays.fill(lastSamples, -1);
        components = new float[count];
        memo = ColourMemo.fits(bitsPerComponent, count)
                ? new ColourMemo(pixelCount, bitsPerComponent * count) : null;
    }

    @Override
    int next(SampleReader in) {
        boolean same = true;
        int key = 0;
        for (int i = 0; i < samples.length; i++) {
            int sample = in.getBits(bitsPerComponent);
            samples[i] = sample;
            if (sample != lastSamples[i]) {
                same = false;
                lastSamples[i] = sample;
            }
            key = key << bitsPerComponent | sample;
        }
        if (cmykCapture != null) {
            int at = pixel++ * 4;
            for (int i = 0; i < 4; i++) {
                cmykCapture[at + i] = (byte) SampleDecode.toByte(decode.value(i, samples[i]));
            }
        }
        if (same) {
            return lastArgb;
        }
        int slot = 0;
        if (memo != null) {
            slot = memo.slot(key);
            int argb = memo.argb(slot);
            if (argb != 0) {
                lastArgb = argb;
                return argb;
            }
        }
        for (int i = 0; i < samples.length; i++) {
            components[i] = decode.value(i, samples[i]);
        }
        lastArgb = colorSpace.getColor(components).getRGB();
        if (memo != null) {
            memo.put(slot, key, lastArgb);
        }
        return lastArgb;
    }
}
