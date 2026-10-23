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

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.Indexed;
import org.icepdf.core.util.Defs;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;

/**
 * GH-625: when a big image is drawn much smaller than its full size, it is decoded at a reduced
 * size instead of in full.  A scan drawn at 5% doesn't need the 344MB raster its full size takes,
 * and on a small heap there may be no room for one.
 * <p>
 * The reduction is a power of two, chosen so the decoded image still has at least twice the
 * device pixels it is drawn into; the GH-608 downsampler filters it the rest of the way, as it
 * does a full decode.  How each decoder gets there:
 * <ul>
 * <li>Flate and other plain sample data: a box filter while the samples stream in
 * ({@link ReducedRasterDecoder}), so the full raster never exists;</li>
 * <li>JPEG 2000: the reader discards resolution levels, the wavelet's own low-pass;</li>
 * <li>JPEG: a full decode, box filtered; the reader's own subsampling drops pixels, and thin lines
 * with them.</li>
 * </ul>
 * Only images without masks are reduced, and only on a raster target: printing and export get the
 * full image.  {@value #ENABLED_PROPERTY} turns it off; {@value #THRESHOLD_PROPERTY} (MB, of the full
 * decode at four bytes a pixel) is the size an image must reach to be reduced.  A JPEG 2000 image
 * has its own, lower threshold, {@value #JPX_THRESHOLD_PROPERTY}: dropping resolution levels is
 * also much faster than a full decode, so it pays well below the size where memory matters.
 */
public final class ReducedDecode {

    public static final String ENABLED_PROPERTY = "org.icepdf.core.images.reducedDecode";
    public static final String THRESHOLD_PROPERTY = "org.icepdf.core.images.reducedDecodeThreshold";
    public static final String JPX_THRESHOLD_PROPERTY = "org.icepdf.core.images.reducedDecodeJpxThreshold";

    private static final Name SMASK_IN_DATA_KEY = new Name("SMaskInData");

    /** The largest reduction: past it an image is a few device pixels anyway. */
    static final int MAX_FACTOR = 64;

    private ReducedDecode() {
    }

    /**
     * True when {@code imageStream} may be decoded reduced: big enough, unmasked, and a format with
     * a reduced decode.  Read off the dictionary alone, so it is cheap to ask at parse time.
     */
    public static boolean isCandidate(ImageStream imageStream) {
        if (imageStream == null || imageStream.getPObjectReference() == null
                || !Defs.booleanProperty(ENABLED_PROPERTY, true)) {
            return false;
        }
        ImageParams params = imageStream.getImageParams();
        boolean jpx = ImageDecoderFactory.containsFilter(imageStream, ImageDecoderFactory.JPX_DECODE_FILTERS);
        if (jpx && !isJpxReducible(imageStream)) {
            return false;
        }
        long threshold = (long) (jpx ? Defs.intProperty(JPX_THRESHOLD_PROPERTY, 4)
                : Defs.intProperty(THRESHOLD_PROPERTY, 16)) << 20;
        if ((long) params.getWidth() * params.getHeight() * 4 < threshold) {
            return false;
        }
        if (params.isImageMask() || imageStream.getEntries().get(ImageParams.SMASK_KEY) != null
                || imageStream.getEntries().get(ImageParams.MASK_KEY) != null
                || imageStream.getEntries().get(SMASK_IN_DATA_KEY) != null) {
            return false;
        }
        if (ImageDecoderFactory.containsFilter(imageStream, ImageDecoderFactory.CCITTFAX_DECODE_FILTERS)
                || ImageDecoderFactory.containsFilter(imageStream, ImageDecoderFactory.JBIG2_DECODE_FILTERS)) {
            return false;
        }
        if (!isPlainSamples(imageStream)) {
            // JPEG and JPEG 2000 decoders already scale a really big image down to about 1500px as they
            // read it, whatever the zoom; JPEG and JPEG 2000 carry their own depth, /BitsPerComponent
            // may be missing.
            return params.getWidth() <= AbstractImageDecoder.maxImageWidth
                    && params.getHeight() <= AbstractImageDecoder.maxImageHeight;
        }
        int bpc = params.getBitsPerComponent();
        return bpc == 1 || bpc == 2 || bpc == 4 || bpc == 8 || bpc == 16;
    }

    /**
     * The reduction the codec itself reads at for a wanted {@code factor}.  A JPEG reader subsamples
     * by keeping one pixel in each block and dropping the rest, which loses the thin lines of a
     * scanned drawing, so a JPEG is read whole and box filtered after: no saving at decode, but
     * only the reduced image is kept.
     */
    static int decodeFactor(ImageStream imageStream, int factor) {
        if (ImageDecoderFactory.containsFilter(imageStream, ImageDecoderFactory.DCT_DECODE_FILTERS)) {
            return 1;
        }
        return factor;
    }

    /**
     * The decoder that reads {@code imageStream} reduced by {@code factor}, or null when there is
     * none and the full decode should be drawn.
     */
    static AbstractImageDecoder decoderFor(ImageStream imageStream, ImageParams params,
                                           GraphicsState graphicsState, int factor) {
        // a CMYK group capture wants the image's true CMYK samples, which only the full decode keeps
        if (ImageUtility.isPreserveCmyk()) {
            return null;
        }
        if (factor < 2) {
            return null;
        }
        int reduction = decodeFactor(imageStream, factor);
        AbstractImageDecoder decoder;
        if (ImageDecoderFactory.containsFilter(imageStream, ImageDecoderFactory.DCT_DECODE_FILTERS)) {
            DctDecoder dct = new DctDecoder(imageStream, graphicsState);
            return new AbstractImageDecoder(imageStream, graphicsState) {
                @Override
                public BufferedImage decode() {
                    return ReducedRasterDecoder.boxFilter(dct.decode(), factor);
                }
            };
        } else if (ImageDecoderFactory.containsFilter(imageStream, ImageDecoderFactory.JPX_DECODE_FILTERS)) {
            // dropping resolution levels averages samples, which is wrong for palette indices: an
            // /Indexed image, or one with no /ColorSpace, whose JP2 wrapper may hold a palette.
            if (imageStream.getEntries().get(ImageParams.COLORSPACE_KEY) == null
                    || params.getColourSpace() instanceof Indexed) {
                return null;
            }
            decoder = new JpxDecoder(imageStream, graphicsState);
        } else if (isPlainSamples(imageStream)) {
            return new ReducedRasterDecoder(imageStream, graphicsState, reduction);
        } else {
            return null;
        }
        decoder.setReduction(reduction);
        return decoder;
    }

    /**
     * False for a JPEG 2000 image whose samples may be palette indices, which can't be averaged: one
     * with no /ColorSpace (the JP2 wrapper's palette may apply) or a direct /Indexed one.  A named
     * colour space is resolved later, against the page's resources ({@link #decoderFor}).
     */
    private static boolean isJpxReducible(ImageStream imageStream) {
        Object colourSpace = imageStream.getLibrary().getObject(imageStream.getEntries(), ImageParams.COLORSPACE_KEY);
        if (colourSpace == null) {
            return false;
        }
        if (colourSpace instanceof java.util.List && !((java.util.List<?>) colourSpace).isEmpty()) {
            Object family = ((java.util.List<?>) colourSpace).get(0);
            return !"Indexed".equals(String.valueOf(family)) && !"I".equals(String.valueOf(family));
        }
        return true;
    }

    /**
     * The reduction to decode at: the largest power of two that leaves at least two source pixels
     * for each device pixel, both ways; 1 when the image is drawn near full size.
     *
     * @param width          image width
     * @param height         image height
     * @param deviceWidth    drawn width in device pixels
     * @param deviceHeight   drawn height in device pixels
     */
    public static int factorFor(int width, int height, double deviceWidth, double deviceHeight) {
        if (!(deviceWidth > 0) || !(deviceHeight > 0)) {
            return 1;
        }
        int factor = 1;
        while (factor < MAX_FACTOR
                && width / (factor * 2.0) >= 2 * deviceWidth
                && height / (factor * 2.0) >= 2 * deviceHeight) {
            factor *= 2;
        }
        return factor;
    }

    /**
     * The reduction for an image drawn into {@code (x, y, w, h)} of {@code g}'s user space.
     */
    public static int factorFor(Graphics2D g, int imageWidth, int imageHeight, int x, int y, int w, int h) {
        AffineTransform t = g.getTransform();
        double deviceWidth = Math.abs(w) * Math.hypot(t.getScaleX(), t.getShearY());
        double deviceHeight = Math.abs(h) * Math.hypot(t.getShearX(), t.getScaleY());
        return factorFor(imageWidth, imageHeight, deviceWidth, deviceHeight);
    }

    /** Size of one side reduced by {@code factor}, rounding up as the decoders do. */
    public static int reduced(int size, int factor) {
        return (size + factor - 1) / factor;
    }

    /** True when {@code stream}'s samples are plain data: no codec, so {@link ReducedRasterDecoder} reads them. */
    static boolean isPlainSamples(ImageStream imageStream) {
        return !ImageDecoderFactory.containsFilter(imageStream, ImageDecoderFactory.DCT_DECODE_FILTERS)
                && !ImageDecoderFactory.containsFilter(imageStream, ImageDecoderFactory.JPX_DECODE_FILTERS)
                && !ImageDecoderFactory.containsFilter(imageStream, ImageDecoderFactory.CCITTFAX_DECODE_FILTERS)
                && !ImageDecoderFactory.containsFilter(imageStream, ImageDecoderFactory.JBIG2_DECODE_FILTERS);
    }
}
