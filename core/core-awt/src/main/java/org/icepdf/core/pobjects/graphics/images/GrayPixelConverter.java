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
 * A DeviceGray image: every sample value's grey, after its Decode, is worked out once, up front, into a table.
 *
 * @since 7.5.0
 */
final class GrayPixelConverter extends PixelConverter {
    private final int bitsPerComponent;
    private final int[] argb;

    GrayPixelConverter(int bitsPerComponent, SampleDecode decode) {
        this.bitsPerComponent = bitsPerComponent;
        argb = new int[1 << bitsPerComponent];
        for (int sample = 0; sample < argb.length; sample++) {
            int gray = SampleDecode.toByte(decode.value(0, sample));
            argb[sample] = 0xff000000 | gray << 16 | gray << 8 | gray;
        }
    }

    @Override
    int next(SampleReader in) {
        return argb[in.getBits(bitsPerComponent)];
    }
}
