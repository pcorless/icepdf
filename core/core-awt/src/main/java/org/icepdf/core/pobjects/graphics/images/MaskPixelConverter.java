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

/**
 * A stencil mask (8.9.6.2): a sample paints the fill colour where the decoded value is 0 and leaves the page
 * alone elsewhere.  With the default Decode [0 1] that is sample 0, with [1 0] sample 1.
 *
 * @since 7.5.0
 */
final class MaskPixelConverter extends PixelConverter {
    private final int bitsPerComponent;
    private final float paintSample;
    private final int fillRgb;

    MaskPixelConverter(int bitsPerComponent, float[] decode, int fillRgb) {
        this.bitsPerComponent = bitsPerComponent;
        // Dmin is the sample that decodes to 0 for a one-bit mask
        this.paintSample = decode[0];
        this.fillRgb = fillRgb;
    }

    @Override
    int next(SampleReader in) {
        return in.getBits(bitsPerComponent) == paintSample ? fillRgb : 0;
    }
}
