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

/**
 * Supplies the colour-space-specific math for transparency-group compositing.
 * <p>
 * The composition <i>flow</i> -- Porter-Duff alpha, the {@code (1-ab)Cs+ab*B}
 * backdrop weighting, constant-alpha mixing and the &sect;11.4.8 backdrop
 * removal -- is channel-count-agnostic and lives once in the shared compositor.
 * Only two things differ between an RGB and a (e.g.) DeviceCMYK group: how a
 * device pixel maps to/from the working colour channels, and the per-channel
 * separable blend functions (&sect;11.3.5).  Those are the entire contract of
 * this strategy, so a new blend space (CMYK, and later spot/DeviceN) is added by
 * implementing this interface without touching the shared flow -- an RGB change
 * cannot break CMYK and vice-versa.
 * <p>
 * Channel values are doubles in {@code [0,255]} (the 8-bit ink/colour domain;
 * RGB factored out of the original code in this domain to stay bit-identical, and
 * CMYK uses the same convention).  A group's content stays in its blend space for
 * the whole composite; {@link #toSRGB} is applied only once, at the final draw to
 * the device.
 *
 * @see <a href="cmyk-compositing-architecture">GH-501 design notes</a>
 */
public interface BlendingSpace {

    /** Number of colour channels (3 for RGB, 4 for CMYK; a value, not hard-coded,
     *  so spot/DeviceN spaces with N&gt;4 can be added later). */
    int channelCount();

    /** Unpacks an sRGB device pixel's colour into this space's channels (0..255).
     *  The pixel's alpha is handled by the flow, not here. */
    void fromSRGB(int argb, double[] channels);

    /** Packs working colour channels (0..255) plus an 8-bit alpha into an sRGB
     *  ARGB pixel.  Channels are clamped to [0,255]. */
    int toSRGB(double[] channels, int alpha);

    /** PDF 32000-1 &sect;11.3.5 separable blend function {@code B(cb, cs)} for a
     *  single channel, operands and result in {@code [0,255]}. */
    double separable(BlendComposite.BlendingMode mode, double cb, double cs);
}
