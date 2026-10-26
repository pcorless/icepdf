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
import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.images.ImageUtility;
import org.icepdf.core.pobjects.graphics.images.references.ImageDownsampler;
import org.icepdf.core.pobjects.graphics.images.references.ImageReference;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * An image painted through a glyph clip (text render mode 4-7).  Java2D never antialiases a clip, so a
 * glyph-shaped clip leaves ragged letters.  When the image is one opaque colour, as it is for spot-coloured
 * text exported as "clip to the glyphs, paint a swatch", the glyph outline is filled with that colour.
 * Any other image is filled into the outline as a texture, mapped exactly where drawImage would put it.
 * Java2D antialiases both fills.  A printer or vector target, a CMYK group capture, or an image too big to
 * use as a texture is drawn through the hard clip as before.
 * <br>
 * The outline is in the image's user space (the transform in effect when this command runs), and the hard
 * clip in effect here has already been widened to the clip outside the text, see
 * {@code GlyphClipFill}.
 *
 * @since 8.0
 */
public class GlyphClippedImageDrawCmd extends ImageDrawCmd {

    // larger images are never one colour in practice, and scanning them would decode them on the paint thread.
    static final int MAX_SOLID_SCAN_PIXELS = 1 << 20;
    // the reduced-decode threshold (16 MB of ARGB): a bigger image is left to drawImage, which can decode it smaller.
    static final int MAX_TEXTURE_PIXELS = 1 << 22;

    private final ImageDrawCmd imageDrawCmd;
    private final Shape glyphClip;
    // benign race: two threads may both scan, and reach the same answer.
    private volatile boolean scanned;
    private volatile Color solidColor;

    public GlyphClippedImageDrawCmd(ImageDrawCmd imageDrawCmd, Shape glyphClip) {
        super(imageDrawCmd.getImageReference());
        this.imageDrawCmd = imageDrawCmd;
        this.glyphClip = glyphClip;
    }

    public Shape getGlyphClip() {
        return glyphClip;
    }

    @Override
    public Shape paintOperand(Graphics2D g, Page parentPage, Shape currentShape,
                              Shape clip, AffineTransform base,
                              OptionalContentState optionalContentState,
                              boolean paintAlpha, PaintTimer paintTimer) throws InterruptedException {
        if (!optionalContentState.isVisible()) {
            return currentShape;
        }
        // a CMYK group capture needs the image's own ink samples, which only drawImage records.
        if (!ImageUtility.isCmykInkCapturing() && TextSprite.hitClip(g, glyphClip.getBounds2D())) {
            Color solid = solidColor();
            if (solid != null) {
                fill(g, solid);
                return currentShape;
            }
            // printers and vector targets clip exactly, and a big image keeps drawImage's reduced decode.
            ImageReference reference = imageDrawCmd.getImageReference();
            if (ImageDownsampler.isRasterTarget(g)
                    && (long) reference.getWidth() * reference.getHeight() <= MAX_TEXTURE_PIXELS) {
                Image image = imageDrawCmd.getImage();
                if (image instanceof BufferedImage) {
                    // drawImage maps the image onto the unit square of this user space.
                    BufferedImage level = ImageDownsampler.select(g, (BufferedImage) image, 0, 0, 1, 1);
                    Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    fill(g, new TexturePaint(level, new Rectangle2D.Double(0, 0, 1, 1)));
                    if (interpolation != null) {
                        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
                    }
                    return currentShape;
                }
            }
        }
        Shape previousClip = g.getClip();
        g.clip(glyphClip);
        imageDrawCmd.paintOperand(g, parentPage, currentShape, clip, base, optionalContentState,
                paintAlpha, paintTimer);
        g.setClip(previousClip);
        return currentShape;
    }

    private void fill(Graphics2D g, Paint fillPaint) {
        Paint paint = g.getPaint();
        g.setPaint(fillPaint);
        g.fill(glyphClip);
        g.setPaint(paint);
    }

    private Color solidColor() throws InterruptedException {
        if (!scanned) {
            ImageReference reference = imageDrawCmd.getImageReference();
            if ((long) reference.getWidth() * reference.getHeight() <= MAX_SOLID_SCAN_PIXELS) {
                solidColor = solidColor(imageDrawCmd.getImage());
            }
            scanned = true;
        }
        return solidColor;
    }

    // a flat swatch run through DCT can come back a level or two off in places.
    private static final int SOLID_TOLERANCE = 2;

    /**
     * @return the image's colour if every pixel is that opaque colour, within {@link #SOLID_TOLERANCE}
     * levels per channel, else null.
     */
    static Color solidColor(Image image) {
        if (!(image instanceof BufferedImage)) {
            return null;
        }
        BufferedImage bufferedImage = (BufferedImage) image;
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        if (width == 0 || height == 0) {
            return null;
        }
        int[] row = new int[width];
        int first = bufferedImage.getRGB(0, 0);
        if ((first >>> 24) != 0xFF) {
            return null;
        }
        for (int y = 0; y < height; y++) {
            bufferedImage.getRGB(0, y, width, 1, row, 0, width);
            for (int argb : row) {
                if (argb != first && !close(argb, first)) {
                    return null;
                }
            }
        }
        return new Color(first);
    }

    private static boolean close(int argb, int reference) {
        if ((argb >>> 24) != 0xFF) {
            return false;
        }
        for (int shift = 0; shift < 24; shift += 8) {
            if (Math.abs(((argb >> shift) & 0xFF) - ((reference >> shift) & 0xFF)) > SOLID_TOLERANCE) {
                return false;
            }
        }
        return true;
    }
}
