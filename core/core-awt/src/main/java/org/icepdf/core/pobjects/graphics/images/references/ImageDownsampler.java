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

import org.icepdf.core.util.Defs;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Zoom-aware minification for image painting.
 * <p>
 * Java2D's {@code drawImage} point-samples the source when it shrinks an image, whatever the
 * interpolation hint: bilinear and bicubic only look at the 2x2 or 4x4 pixels around each sample
 * point.  Below about 50% every other source row and column is skipped, so the one-pixel strokes of
 * a scanned page (CCITT fax, JBIG2, stencil masks) fall between samples and the text breaks up.
 * <p>
 * Here the image is instead drawn from a mip level: the source repeatedly halved with a 2x2 box
 * filter until the next halving would drop below the device resolution, then drawn with bilinear.
 * Every source pixel contributes to the result: a standard 2x2 box-filter mip map.
 * <p>
 * The pyramid is keyed weakly on the decoded image, so it lives exactly as long as the image the
 * {@link ImagePool} (or a reference) keeps, and every image reference type shares it.  Levels are
 * held softly and rebuilt on demand.  Opaque grey images, the scanned-document case, keep their
 * levels as one byte per pixel, so the whole pyramid is about a third of the source's pixel count.
 * <p>
 * Enabled by default; {@code -Dorg.icepdf.core.imageReference.downsample=false} or
 * {@link #setEnabled(boolean)} turns it off.
 *
 * @since 7.5
 */
public final class ImageDownsampler {

    private static volatile boolean enabled =
            Defs.booleanProperty("org.icepdf.core.imageReference.downsample", true);

    /**
     * Images smaller than this on both sides are not worth a pyramid.
     */
    private static final int MIN_SIZE = 4;

    private static final Map<BufferedImage, Pyramid> pyramids = new WeakHashMap<>();

    private ImageDownsampler() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Turns zoom-aware downsampling on or off for subsequent paints.
     */
    public static void setEnabled(boolean enabled) {
        ImageDownsampler.enabled = enabled;
    }

    /**
     * Device pixels per source pixel, the larger of the two image axes, when {@code image} is
     * drawn into the rectangle {@code (x, y, w, h)} of {@code g}.
     */
    static double deviceScale(Graphics2D g, BufferedImage image, int x, int y, int w, int h) {
        AffineTransform t = new AffineTransform(g.getTransform());
        t.translate(x, y);
        t.scale(w / (double) image.getWidth(), h / (double) image.getHeight());
        double sx = Math.hypot(t.getScaleX(), t.getShearY());
        double sy = Math.hypot(t.getShearX(), t.getScaleY());
        return Math.max(sx, sy);
    }

    /**
     * True when {@code g} rasterises into pixels Java2D owns: an on-screen or BufferedImage
     * graphics.  Printer and vector (SVG, PDF) graphics report a transform in their own units, not
     * device pixels, and should get the full-resolution image anyway.
     */
    static boolean isRasterTarget(Graphics2D g) {
        return "sun.java2d.SunGraphics2D".equals(g.getClass().getName());
    }

    /**
     * The image to draw for this paint: a mip level of {@code image} when it is minified by more
     * than half, otherwise {@code image} itself.  Always drawn into the same {@code (x, y, w, h)}.
     */
    public static BufferedImage select(Graphics2D g, BufferedImage image, int x, int y, int w, int h) {
        if (image == null || w == 0 || h == 0 || !isRasterTarget(g)) {
            return image;
        }
        if (image.getWidth() < MIN_SIZE && image.getHeight() < MIN_SIZE) {
            return image;
        }
        double scale = deviceScale(g, image, x, y, w, h);
        if (!(scale < 0.5) || scale <= 0) {
            return image;
        }
        // halve while the next level still has at least one source pixel per device pixel
        int level = 0;
        while (scale < 0.5 && (image.getWidth() >> level) > MIN_SIZE && (image.getHeight() >> level) > MIN_SIZE) {
            level++;
            scale *= 2;
        }
        if (level == 0) {
            return image;
        }
        Pyramid pyramid;
        synchronized (pyramids) {
            pyramid = pyramids.computeIfAbsent(image, k -> new Pyramid());
        }
        return pyramid.level(image, level);
    }

    /**
     * The mip levels of one image; level {@code n} is the source halved {@code n} times.
     */
    private static final class Pyramid {
        // index 0 is level 1; the source itself is never held here (it is the map key)
        private final List<SoftReference<BufferedImage>> levels = new ArrayList<>();

        synchronized BufferedImage level(BufferedImage source, int level) {
            BufferedImage current = source;
            for (int i = 1; i <= level; i++) {
                BufferedImage next = i <= levels.size() ? levels.get(i - 1).get() : null;
                if (next == null) {
                    next = i == 1 ? halveSource(source) : halve(current);
                    SoftReference<BufferedImage> ref = new SoftReference<>(next);
                    if (i <= levels.size()) {
                        levels.set(i - 1, ref);
                    } else {
                        levels.add(ref);
                    }
                }
                current = next;
            }
            return current;
        }
    }

    /**
     * True when the source has no alpha and its colours are grey, so a one byte per pixel level
     * loses nothing.
     */
    static boolean isOpaqueGray(BufferedImage image) {
        if (image.getColorModel().hasAlpha()) {
            return false;
        }
        int type = image.getType();
        if (type == BufferedImage.TYPE_BYTE_GRAY || type == BufferedImage.TYPE_USHORT_GRAY) {
            return true;
        }
        if (image.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY) {
            return true;
        }
        if (type == BufferedImage.TYPE_BYTE_BINARY || type == BufferedImage.TYPE_BYTE_INDEXED) {
            java.awt.image.IndexColorModel icm = (java.awt.image.IndexColorModel) image.getColorModel();
            for (int i = 0, n = icm.getMapSize(); i < n; i++) {
                int rgb = icm.getRGB(i);
                int r = (rgb >> 16) & 255, gr = (rgb >> 8) & 255, b = rgb & 255;
                if (r != gr || gr != b) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * First halving, from a source of any type: read through {@code getRGB} two rows at a time,
     * into a grey level for opaque grey sources and a premultiplied ARGB level otherwise.
     */
    static BufferedImage halveSource(BufferedImage src) {
        int type = src.getType();
        if (type == BufferedImage.TYPE_BYTE_GRAY || type == BufferedImage.TYPE_USHORT_GRAY) {
            return halveGraySamples(src);
        }
        int w = src.getWidth(), h = src.getHeight();
        int nw = (w + 1) / 2, nh = (h + 1) / 2;
        boolean gray = isOpaqueGray(src);
        BufferedImage dst = new BufferedImage(nw, nh,
                gray ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_ARGB_PRE);
        int[] row0 = new int[w];
        int[] row1 = new int[w];
        byte[] grayOut = gray ? ((DataBufferByte) dst.getRaster().getDataBuffer()).getData() : null;
        int[] argbOut = gray ? null : ((DataBufferInt) dst.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < nh; y++) {
            src.getRGB(0, 2 * y, w, 1, row0, 0, w);
            src.getRGB(0, Math.min(2 * y + 1, h - 1), w, 1, row1, 0, w);
            int out = y * nw;
            for (int x = 0; x < nw; x++) {
                int x0 = 2 * x, x1 = Math.min(x0 + 1, w - 1);
                int p0 = row0[x0], p1 = row0[x1], p2 = row1[x0], p3 = row1[x1];
                if (gray) {
                    // grey source: any channel is the grey value
                    grayOut[out + x] = (byte) (((p0 & 255) + (p1 & 255) + (p2 & 255) + (p3 & 255) + 2) >> 2);
                } else {
                    argbOut[out + x] = averagePremultiplied(p0, p1, p2, p3);
                }
            }
        }
        return dst;
    }

    /**
     * First halving of a {@code TYPE_BYTE_GRAY} or {@code TYPE_USHORT_GRAY} source, from its raw
     * samples.  Their {@code CS_GRAY} colour space is linear, so {@code getRGB} gamma-lifts the
     * mid-tones (128 reads as 188), yet {@code drawImage} paints the samples as they are; a level
     * built through {@code getRGB} would draw visibly lighter than the source it stands in for.
     */
    static BufferedImage halveGraySamples(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int nw = (w + 1) / 2, nh = (h + 1) / 2;
        // 16-bit samples are drawn by their high byte
        int shift = src.getType() == BufferedImage.TYPE_USHORT_GRAY ? 8 : 0;
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_BYTE_GRAY);
        byte[] out = ((DataBufferByte) dst.getRaster().getDataBuffer()).getData();
        java.awt.image.Raster raster = src.getRaster();
        int[] row0 = new int[w];
        int[] row1 = new int[w];
        for (int y = 0; y < nh; y++) {
            raster.getSamples(0, 2 * y, w, 1, 0, row0);
            raster.getSamples(0, Math.min(2 * y + 1, h - 1), w, 1, 0, row1);
            for (int x = 0; x < nw; x++) {
                int x0 = 2 * x, x1 = Math.min(x0 + 1, w - 1);
                int sum = (row0[x0] >> shift) + (row0[x1] >> shift) + (row1[x0] >> shift) + (row1[x1] >> shift);
                out[y * nw + x] = (byte) ((sum + 2) >> 2);
            }
        }
        return dst;
    }

    /**
     * Averages four non-premultiplied ARGB pixels, weighting colour by alpha, and returns the
     * premultiplied result.
     */
    private static int averagePremultiplied(int p0, int p1, int p2, int p3) {
        int a0 = p0 >>> 24, a1 = p1 >>> 24, a2 = p2 >>> 24, a3 = p3 >>> 24;
        int a = a0 + a1 + a2 + a3;
        if (a == 0) {
            return 0;
        }
        int r = (((p0 >> 16) & 255) * a0 + ((p1 >> 16) & 255) * a1 + ((p2 >> 16) & 255) * a2 + ((p3 >> 16) & 255) * a3);
        int g = (((p0 >> 8) & 255) * a0 + ((p1 >> 8) & 255) * a1 + ((p2 >> 8) & 255) * a2 + ((p3 >> 8) & 255) * a3);
        int b = ((p0 & 255) * a0 + (p1 & 255) * a1 + (p2 & 255) * a2 + (p3 & 255) * a3);
        // premultiplied channel = sum(c * a) / 255 / 4
        return ((a + 2) >> 2) << 24 | ((r + 510) / 1020) << 16 | ((g + 510) / 1020) << 8 | ((b + 510) / 1020);
    }

    /**
     * Later halvings, from a level this class made: both formats store channels that average
     * directly.
     */
    static BufferedImage halve(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int nw = (w + 1) / 2, nh = (h + 1) / 2;
        BufferedImage dst = new BufferedImage(nw, nh, src.getType());
        if (src.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            byte[] in = ((DataBufferByte) src.getRaster().getDataBuffer()).getData();
            byte[] out = ((DataBufferByte) dst.getRaster().getDataBuffer()).getData();
            for (int y = 0; y < nh; y++) {
                int r0 = 2 * y * w, r1 = Math.min(2 * y + 1, h - 1) * w;
                for (int x = 0; x < nw; x++) {
                    int x0 = 2 * x, x1 = Math.min(x0 + 1, w - 1);
                    out[y * nw + x] = (byte) (((in[r0 + x0] & 255) + (in[r0 + x1] & 255)
                            + (in[r1 + x0] & 255) + (in[r1 + x1] & 255) + 2) >> 2);
                }
            }
        } else {
            int[] in = ((DataBufferInt) src.getRaster().getDataBuffer()).getData();
            int[] out = ((DataBufferInt) dst.getRaster().getDataBuffer()).getData();
            for (int y = 0; y < nh; y++) {
                int r0 = 2 * y * w, r1 = Math.min(2 * y + 1, h - 1) * w;
                for (int x = 0; x < nw; x++) {
                    int x0 = 2 * x, x1 = Math.min(x0 + 1, w - 1);
                    int a = in[r0 + x0], b = in[r0 + x1], c = in[r1 + x0], d = in[r1 + x1];
                    int v = 0;
                    for (int shift = 0; shift < 32; shift += 8) {
                        v |= ((((a >>> shift) & 255) + ((b >>> shift) & 255)
                                + ((c >>> shift) & 255) + ((d >>> shift) & 255) + 2) >> 2) << shift;
                    }
                    out[y * nw + x] = v;
                }
            }
        }
        return dst;
    }
}
