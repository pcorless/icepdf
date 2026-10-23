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
 * An image's Decode array (8.9.5.2): maps each component's samples, 0 to 2^bitsPerComponent - 1, linearly onto
 * [Dmin, Dmax], a value in the colour space's own units (0..1 for most, L* 0..100 for Lab, the index for
 * Indexed).  A Dmin above Dmax inverts the samples.
 *
 * @since 7.5.0
 */
final class SampleDecode {
    // Dmin * maxSample, so a value is one product and one division: exactly sample / maxSample for [0 1] and
    // (maxSample - sample) / maxSample for [1 0]
    private final float[] scaledMin;
    private final float[] span;
    private final float maxSample;

    /**
     * @param range            a {@code [Dmin Dmax]} pair per component, see {@link ImageParams#getDecodeRange()}.
     * @param bitsPerComponent bits per sample.
     */
    SampleDecode(float[] range, int bitsPerComponent) {
        int components = range.length / 2;
        maxSample = (1 << bitsPerComponent) - 1;
        scaledMin = new float[components];
        span = new float[components];
        for (int i = 0; i < components; i++) {
            scaledMin[i] = range[2 * i] * maxSample;
            span[i] = range[2 * i + 1] - range[2 * i];
        }
    }

    /**
     * @param component colour component.
     * @param sample    its sample.
     * @return the decoded value, Dmin + sample * (Dmax - Dmin) / maxSample.
     */
    float value(int component, int sample) {
        return (scaledMin[component] + span[component] * sample) / maxSample;
    }

    /**
     * @param value a decoded value meant to lie in 0..1.
     * @return it as a byte value, 0 to 255, rounded and clamped.
     */
    static int toByte(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255)));
    }
}
