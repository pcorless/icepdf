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
package org.icepdf.core.pobjects.graphics.commands;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.OptionalContentState;
import org.icepdf.core.pobjects.graphics.PaintTimer;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.graphics.images.references.ImageDownsampler;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * A tiny stencil mask image (ImageMask true) painted without the image pipeline (GH-632).  The parser read its
 * samples, so there is no image reference, pooled decode or decode task per mask.
 * <ul>
 *     <li>A mask smaller than a device pixel across or down, such as the 1200 dpi hatching rows drivers emit
 *     (0.06 px tall at 72 dpi), is filled as rectangles, one per run, each widened to a whole device pixel and
 *     not antialiased.  That is how an image mask rasterises at that size: drawn as an image it touches whole
 *     pixels, and antialiased exact coverage fades the hatching to a faint grey.</li>
 *     <li>Any other mask is drawn as the image it is, built once from its samples, the same way
 *     {@code ImageReference} draws a decoded mask (from a mip level, bilinear, when minified).  Rasterising a
 *     larger mask as fills would not match it: antialiasing leaves seams between stacked masks, and without
 *     it sub-pixel tile edges leave gaps.</li>
 * </ul>
 * It is a {@link FillDrawCmd} over the mask's shape, so vector output (PostScript) still fills the mask, and
 * {@link org.icepdf.core.pobjects.graphics.Shapes#getImages()} still lists the image.
 *
 * @since 8.0
 */
public class StencilMaskFillDrawCmd extends FillDrawCmd {

    private static final AffineTransform IDENTITY = new AffineTransform();

    private final ImageStream imageStream;
    private final boolean[] paints;
    private final int width;
    private final int height;
    private final Color color;
    // painted runs as (row, start, end) triples
    private final int[] runs;
    private final int runCount;
    // the image's unit square -> the user space this command paints in
    private final AffineTransform imageToUser;
    // built on first use; a benign race builds the same image twice.
    private volatile BufferedImage image;

    /**
     * @param imageStream the mask image
     * @param paints      row-major, true where the mask paints
     * @param width       mask width in samples
     * @param height      mask height in samples
     * @param color       the fill colour the mask paints with
     * @param runs        the painted runs, as (row, start, end) sample triples
     * @param runCount    the number of runs
     * @param imageToUser maps the unit square the image is drawn into (row 0 at the top) to the user space this
     *                    command paints in
     */
    public StencilMaskFillDrawCmd(ImageStream imageStream, boolean[] paints, int width, int height, Color color,
                                  int[] runs, int runCount, AffineTransform imageToUser) {
        this.imageStream = imageStream;
        this.paints = paints;
        this.width = width;
        this.height = height;
        this.color = color;
        this.runs = runs;
        this.runCount = runCount;
        this.imageToUser = imageToUser;
    }

    public ImageStream getImageStream() {
        return imageStream;
    }

    @Override
    public Shape paintOperand(Graphics2D g, Page parentPage, Shape currentShape, Shape clip, AffineTransform base,
                              OptionalContentState optionalContentState, boolean paintAlpha,
                              PaintTimer paintTimer) {
        if (!optionalContentState.isVisible()) {
            return currentShape;
        }
        AffineTransform user = g.getTransform();
        AffineTransform device = new AffineTransform(user);
        device.concatenate(imageToUser);
        // the whole mask's device extent along the image's two axes
        double across = Point2D.distance(0, 0, device.getScaleX(), device.getShearY());
        double down = Point2D.distance(0, 0, device.getShearX(), device.getScaleY());
        boolean axisAligned = (device.getShearX() == 0 && device.getShearY() == 0)
                || (device.getScaleX() == 0 && device.getScaleY() == 0);
        if ((across < 1 || down < 1) && axisAligned && ImageDownsampler.isRasterTarget(g)) {
            fillRuns(g, device, user);
        } else {
            drawImage(g, user);
        }
        return currentShape;
    }

    /** Each run as a device rectangle at least one pixel each way, without antialiasing. */
    private void fillRuns(Graphics2D g, AffineTransform device, AffineTransform user) {
        Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setTransform(IDENTITY);
        try {
            Rectangle2D.Double run = new Rectangle2D.Double();
            double[] corners = new double[4];
            for (int i = 0; i < runCount; i++) {
                corners[0] = runs[3 * i + 1] / (double) width;
                corners[1] = runs[3 * i] / (double) height;
                corners[2] = runs[3 * i + 2] / (double) width;
                corners[3] = (runs[3 * i] + 1) / (double) height;
                // axis-aligned, so two opposite corners give the device bounds
                device.transform(corners, 0, corners, 0, 2);
                double w = Math.max(1, Math.abs(corners[2] - corners[0]));
                double h = Math.max(1, Math.abs(corners[3] - corners[1]));
                run.setRect((corners[0] + corners[2] - w) / 2, (corners[1] + corners[3] - h) / 2, w, h);
                g.fill(run);
            }
        } finally {
            g.setTransform(user);
            if (antialiasing != null) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
            }
        }
    }

    /** The mask as an image into its unit square, as ImageReference draws a decoded mask. */
    private void drawImage(Graphics2D g, AffineTransform user) {
        BufferedImage drawn = getImage();
        g.transform(imageToUser);
        Object interpolation = null;
        try {
            // GH-608: a minified image is drawn from a box-filtered mip level with bilinear.
            if (ImageDownsampler.isEnabled() && ImageDownsampler.isRasterTarget(g)) {
                AffineTransform t = g.getTransform();
                double scale = Math.max(Math.hypot(t.getScaleX(), t.getShearY()) / width,
                        Math.hypot(t.getShearX(), t.getScaleY()) / height);
                if (scale < 1) {
                    interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
                    if (interpolation == null) {
                        interpolation = RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
                    }
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    drawn = ImageDownsampler.select(g, drawn, 0, 0, 1, 1);
                }
            }
            g.drawImage(drawn, 0, 0, 1, 1, null);
        } finally {
            if (interpolation != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            }
            g.setTransform(user);
        }
    }

    /**
     * @return the mask as the image decoding would have made it: the fill colour where it paints, transparent
     * elsewhere
     */
    public BufferedImage getImage() {
        BufferedImage built = image;
        if (built == null) {
            built = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            int argb = color.getRGB() | 0xFF000000;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (paints[y * width + x]) {
                        built.setRGB(x, y, argb);
                    }
                }
            }
            image = built;
        }
        return built;
    }
}
