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
package org.icepdf.core.util.parser.content;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.PatternColor;
import org.icepdf.core.pobjects.graphics.images.ImageParams;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.Defs;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.List;

/**
 * Tiny stencil masks painted as vector fills (GH-632).
 * <br>
 * A stencil mask (ImageMask true) paints the fill colour wherever its 1-bit samples say so.  Drivers such as
 * PScript5 emit hatching and line work as tens of thousands of masks a few samples wide and one row high
 * (9216.pdf: 122,537).  Each one taking the image path costs a reference, a pooled decode, mask colouring and
 * a transformed drawImage that allocates a raster.  For a mask this small the painted samples are a handful
 * of rectangles; filling them in the unit square the image would be drawn into gives the same coverage,
 * antialiased like any other fill.
 * <br>
 * Only the narrow, common case converts: an image XObject mask of at most {@link #MAX_SAMPLES} samples and
 * {@link #MAX_RUNS} runs, 1 bit per sample, generic filters, no /Interpolate, painted with a plain fill
 * colour (no pattern, soft mask or blend mode) in a viewing parse.  Everything else keeps the image path.
 * Kill switch: {@value #ENABLED_PROPERTY}.
 */
final class StencilMaskFill {

    static final String ENABLED_PROPERTY = "org.icepdf.core.stencilMask.fill";
    static final int MAX_SAMPLES = 4096;
    static final int MAX_RUNS = 256;

    private static final Name INTERPOLATE_KEY = new Name("Interpolate");

    private StencilMaskFill() {
    }

    /**
     * @param inTextBlock the Do is inside BT/ET, where the image square is only translated, not flipped (GH-243)
     * @return the mask's painted samples as a shape in the current user space, covering what drawing the image
     * into its unit square would, or null when this mask should keep the image path.
     */
    static Mask of(ImageStream imageStream, GraphicsState graphicState, boolean inTextBlock) {
        if (!Defs.booleanProperty(ENABLED_PROPERTY, true)) {
            return null;
        }
        ImageParams params = imageStream.getImageParams();
        if (!params.isImageMask() || params.getBitsPerComponent() != 1 || params.hasSMask() || params.hasMask()) {
            return null;
        }
        int width = params.getWidth();
        int height = params.getHeight();
        if (width <= 0 || height <= 0 || (long) width * height > MAX_SAMPLES) {
            return null;
        }
        if (Boolean.TRUE.equals(imageStream.getLibrary().getObject(imageStream.getEntries(), INTERPOLATE_KEY))) {
            return null;
        }
        if (!genericFilters(imageStream.getFilterNames())) {
            return null;
        }
        if (graphicState.getFillColor() == null || graphicState.getFillColorSpace() instanceof PatternColor
                || graphicState.isOverprintOther()
                || (graphicState.getExtGState() != null && (graphicState.getExtGState().getSMask() != null
                || graphicState.getExtGState().getBlendingMode() != null))) {
            return null;
        }
        int rowBytes = (width + 7) / 8;
        byte[] data = imageStream.getDecodedStreamBytes(rowBytes * height);
        if (data == null || data.length < rowBytes * height) {
            return null;
        }
        // a sample paints when its decoded value is below 0.5, as MaskPixelConverter decides.
        boolean paintsZero = true;
        Object decode = imageStream.getLibrary().getObject(imageStream.getEntries(), ImageParams.DECODE_KEY);
        if (decode instanceof List && ((List<?>) decode).size() >= 2
                && ((List<?>) decode).get(0) instanceof Number && ((List<?>) decode).get(1) instanceof Number) {
            paintsZero = ((Number) ((List<?>) decode).get(0)).floatValue() < 0.5f;
        }
        boolean[] paints = new boolean[width * height];
        // runs as (row, start, end) triples
        int[] runs = new int[3 * 8];
        int runCount = 0;
        for (int y = 0; y < height; y++) {
            int start = -1;
            for (int x = 0; x <= width; x++) {
                boolean paint = false;
                if (x < width) {
                    boolean zero = (data[y * rowBytes + (x >> 3)] & (0x80 >> (x & 7))) == 0;
                    paint = zero == paintsZero;
                    paints[y * width + x] = paint;
                }
                if (paint && start < 0) {
                    start = x;
                } else if (!paint && start >= 0) {
                    if (runCount == MAX_RUNS) {
                        return null;
                    }
                    if (3 * runCount + 3 > runs.length) {
                        runs = Arrays.copyOf(runs, runs.length * 2);
                    }
                    runs[3 * runCount] = y;
                    runs[3 * runCount + 1] = start;
                    runs[3 * runCount + 2] = x;
                    runCount++;
                    start = -1;
                }
            }
        }
        // The image is drawn into its unit square, row 0 at the top, after scale(1, -1) translate(0, -1) (only the
        // translate in a text block).  In the current user space a row y0..y1 of that square is 1-y1..1-y0
        // (y0-1..y1-1 in a text block).  Most masks are one run: a plain rectangle.
        float rowHeight = 1f / height;
        Shape shape;
        if (runCount == 1) {
            shape = rectangle(runs, 0, width, rowHeight, inTextBlock);
        } else {
            Path2D.Float path = new Path2D.Float(Path2D.WIND_NON_ZERO, Math.max(runCount * 5, 1));
            for (int i = 0; i < runCount; i++) {
                path.append(rectangle(runs, i, width, rowHeight, inTextBlock), false);
            }
            shape = path;
        }
        AffineTransform imageToUser = inTextBlock
                ? new AffineTransform(1, 0, 0, 1, 0, -1)
                : new AffineTransform(1, 0, 0, -1, 0, 1);
        return new Mask(shape, paints, width, height, runs, runCount, imageToUser);
    }

    private static Rectangle2D.Float rectangle(int[] runs, int i, int width, float rowHeight, boolean inTextBlock) {
        float y0 = runs[3 * i] * rowHeight;
        float x0 = runs[3 * i + 1] / (float) width;
        float x1 = runs[3 * i + 2] / (float) width;
        float top = inTextBlock ? y0 - 1 : 1 - (y0 + rowHeight);
        return new Rectangle2D.Float(x0, top, x1 - x0, rowHeight);
    }

    private static boolean genericFilters(List<?> filters) {
        if (filters == null) {
            return true;
        }
        for (Object filter : filters) {
            switch (String.valueOf(filter)) {
                case "FlateDecode":
                case "Fl":
                case "LZWDecode":
                case "LZW":
                case "ASCIIHexDecode":
                case "AHx":
                case "ASCII85Decode":
                case "A85":
                case "RunLengthDecode":
                case "RL":
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    /** A mask's painted area and samples. */
    static final class Mask {
        final Shape shape;
        final boolean[] paints;
        final int width;
        final int height;
        final int[] runs;
        final int runCount;
        final AffineTransform imageToUser;

        Mask(Shape shape, boolean[] paints, int width, int height, int[] runs, int runCount,
             AffineTransform imageToUser) {
            this.shape = shape;
            this.paints = paints;
            this.width = width;
            this.height = height;
            this.runs = runs;
            this.runCount = runCount;
            this.imageToUser = imageToUser;
        }
    }
}
