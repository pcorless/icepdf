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
package org.icepdf.core.pobjects.graphics.images.references;

import org.icepdf.core.pobjects.graphics.images.MaskedImage;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;

/**
 * Paints a {@link MaskedImage}: the base and its stencil are each drawn into a scratch buffer covering only
 * the device pixels this paint touches, the stencil's coverage is multiplied into the base's alpha, and the
 * result is drawn to the target pixel for pixel.
 * <p>
 * Neither part is ever scaled to the other's resolution.  Each is drawn the way a plain image would be,
 * from a mip level of its own when minified ({@link ImageDownsampler}), so the stencil's text edges come
 * out anti-aliased at any zoom.  The scratch buffers are bounded by the clip, and taken in bands so even a
 * clip the size of a large zoomed page costs a few megabytes at a time.
 *
 * @since 7.5
 */
final class MaskedImagePainter {

    /**
     * Device pixels in one band of scratch: 4 MB of base and 1 MB of coverage.
     */
    private static final int BAND_PIXELS = 1 << 20;

    private MaskedImagePainter() {
    }

    /**
     * True when {@code g} can take the band by band paint: a raster target, where the transform is in
     * device pixels.  Anything else (a printer, a vector target) draws {@link MaskedImage#composite()}.
     */
    static boolean canPaint(Graphics2D g) {
        return ImageDownsampler.isRasterTarget(g);
    }

    /**
     * Paints {@code image} into the rectangle {@code (x, y, w, h)} of {@code g}'s user space, as
     * {@code g.drawImage(image.composite(), x, y, w, h, null)} would.
     */
    static void paint(Graphics2D g, MaskedImage image, int x, int y, int w, int h) {
        AffineTransform transform = g.getTransform();
        Rectangle2D device = transform.createTransformedShape(new Rectangle2D.Double(x, y, w, h)).getBounds2D();
        Shape clip = g.getClip();
        if (clip != null) {
            Rectangle2D.intersect(device, transform.createTransformedShape(clip).getBounds2D(), device);
        }
        int dx = (int) Math.floor(device.getMinX());
        int dy = (int) Math.floor(device.getMinY());
        int dw = (int) Math.ceil(device.getMaxX()) - dx;
        int dh = (int) Math.ceil(device.getMaxY()) - dy;
        if (dw <= 0 || dh <= 0) {
            return;
        }
        Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        if (interpolation == null) {
            interpolation = RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
        }
        int bandHeight = Math.max(1, Math.min(dh, BAND_PIXELS / dw));
        AffineTransform saved = g.getTransform();
        try {
            for (int by = dy; by < dy + dh; by += bandHeight) {
                int bh = Math.min(bandHeight, dy + dh - by);
                BufferedImage band = paintBand(transform, interpolation, image, x, y, w, h, dx, by, dw, bh);
                // the band is in device pixels; the clip, composite and paint state stay as they are
                g.setTransform(new AffineTransform());
                g.drawImage(band, dx, by, null);
                g.setTransform(saved);
            }
        } finally {
            g.setTransform(saved);
        }
    }

    private static BufferedImage paintBand(AffineTransform transform, Object interpolation, MaskedImage image,
                                           int x, int y, int w, int h, int bx, int by, int bw, int bh) {
        AffineTransform bandTransform = AffineTransform.getTranslateInstance(-bx, -by);
        bandTransform.concatenate(transform);

        BufferedImage band = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB_PRE);
        draw(band, bandTransform, interpolation, image.getBase(), x, y, w, h);
        // zero coverage outside the placement is fine: the base left those pixels transparent
        BufferedImage coverage = new BufferedImage(bw, bh, BufferedImage.TYPE_BYTE_GRAY);
        draw(coverage, bandTransform, interpolation, image.getStencil(), x, y, w, h);

        // the stencil paints where it is black: alpha is 255 - grey
        int[] argb = ((DataBufferInt) band.getRaster().getDataBuffer()).getData();
        byte[] grey = ((DataBufferByte) coverage.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < argb.length; i++) {
            int a = 255 - (grey[i] & 255);
            int p = argb[i];
            if (a == 0) {
                argb[i] = 0;
            } else if (a != 255 && p != 0) {
                argb[i] = scale(p >>> 24, a) << 24 | scale((p >> 16) & 255, a) << 16
                        | scale((p >> 8) & 255, a) << 8 | scale(p & 255, a);
            }
        }
        return band;
    }

    /**
     * Draws one part into a band, as {@link ImageReference#drawImage} would draw a plain image: from a mip
     * level, with bilinear, when minified, and with the target's interpolation otherwise.
     */
    private static void draw(BufferedImage target, AffineTransform transform, Object interpolation,
                             BufferedImage part, int x, int y, int w, int h) {
        Graphics2D g = target.createGraphics();
        try {
            g.setTransform(transform);
            BufferedImage drawn = part;
            if (ImageDownsampler.isEnabled() && ImageDownsampler.deviceScale(g, part, x, y, w, h) < 1) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                drawn = ImageDownsampler.select(g, part, x, y, w, h);
            } else {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            }
            drawVisible(g, target.getWidth(), target.getHeight(), drawn, x, y, w, h);
        } finally {
            g.dispose();
        }
    }

    /**
     * Draws only the source pixels that land in the target, plus a pixel of margin for interpolation.
     * Java2D has no transforming loop for some source types (the one bit stencil among them) and converts
     * the whole source to one it has on every call: for a band of a large stencil, a full size copy per
     * band.  A sub-image (no pixel copy) keeps that conversion to what the band shows.
     */
    private static void drawVisible(Graphics2D g, int targetWidth, int targetHeight, BufferedImage image,
                                    int x, int y, int w, int h) {
        int imgW = image.getWidth();
        int imgH = image.getHeight();
        // source pixel -> user -> target pixel
        AffineTransform imageToTarget = new AffineTransform(g.getTransform());
        imageToTarget.translate(x, y);
        imageToTarget.scale(w / (double) imgW, h / (double) imgH);
        Rectangle2D src;
        try {
            src = imageToTarget.createInverse()
                    .createTransformedShape(new Rectangle2D.Double(0, 0, targetWidth, targetHeight)).getBounds2D();
        } catch (java.awt.geom.NoninvertibleTransformException e) {
            return;
        }
        int sx = Math.max(0, (int) Math.floor(src.getMinX()) - 1);
        int sy = Math.max(0, (int) Math.floor(src.getMinY()) - 1);
        int sx2 = Math.min(imgW, (int) Math.ceil(src.getMaxX()) + 1);
        int sy2 = Math.min(imgH, (int) Math.ceil(src.getMaxY()) + 1);
        if (sx2 <= sx || sy2 <= sy) {
            return;
        }
        AffineTransform draw = new AffineTransform(imageToTarget);
        draw.translate(sx, sy);
        g.setTransform(draw);
        g.drawImage(image.getSubimage(sx, sy, sx2 - sx, sy2 - sy), 0, 0, null);
    }

    private static int scale(int channel, int alpha) {
        int t = channel * alpha + 128;
        return (t + (t >> 8)) >> 8;
    }
}
