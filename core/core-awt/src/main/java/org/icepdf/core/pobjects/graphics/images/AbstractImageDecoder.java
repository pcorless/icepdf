/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
package org.icepdf.core.pobjects.graphics.images;

import com.twelvemonkeys.image.AffineTransformOp;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.util.Defs;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.util.logging.Logger;

public abstract class AbstractImageDecoder implements ImageDecoder {

    private static final Logger logger =
            Logger.getLogger(AbstractImageDecoder.class.getName());

    public static int maxImageWidth = 10000;
    public static int maxImageHeight = 10000;
    public static int preferredSize = 1500;

    static {
        try {
            maxImageWidth = Integer.parseInt(Defs.sysProperty("org.icepdf.core.imageDecoder.maxwWidth",
                    String.valueOf(maxImageWidth)));

            maxImageHeight = Integer.parseInt(Defs.sysProperty("org.icepdf.core.imageDecoder.maxHeight",
                    String.valueOf(maxImageHeight)));

            preferredSize = Integer.parseInt(Defs.sysProperty("org.icepdf.core.imageDecoder.preferredSize",
                    String.valueOf(preferredSize)));
        } catch (NumberFormatException e) {
            logger.warning("Error reading buffered scale factor");
        }
    }

    protected ImageStream imageStream;
    protected GraphicsState graphicsState;

    public AbstractImageDecoder(ImageStream imageStream, GraphicsState graphicsState) {
        this.imageStream = imageStream;
        this.graphicsState = graphicsState;
    }

    @Override
    public abstract BufferedImage decode();

    public ImageStream getImageStream() {
        return imageStream;
    }

    /**
     * Check to make sure we don't have ludicrously large image that will likely pop the heap.  This is a rough check
     * to take images that are bigger the 10kx10k and scales them do something more manageable like 1.5k.
     *
     * @return true if the image is really, really, really big.
     */
    boolean isImageReallyBig(WritableRaster raster) {
        return isImageReallyBig(raster.getWidth(), raster.getHeight());
    }

    boolean isImageReallyBig(int width, int height) {
        // either dimension being absurd is enough to pop the heap once the image
        // is expanded/transformed for painting, so guard on OR not AND.
        return width > maxImageWidth || height > maxImageHeight;
    }

    /**
     * Scales images larger then org.icepdf.core.imageDecoder.maxwWidth x org.icepdf.core.imageDecoder.maxHeight.
     * The images will be scaled down to preferredSize on the longest edge.
     *
     * @param raster raster to scale.
     * @return scaled raster.
     */
    WritableRaster scaleReallyBigImages(WritableRaster raster) {
        AffineTransform at = new AffineTransform();
        double scale = Math.min(preferredSize / (double) raster.getWidth(), preferredSize / (double) raster.getHeight());
        at.scale(scale, scale);
        AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
        return scaleOp.filter(raster, null);
    }

    /**
     * Scales an already-decoded image whose dimensions exceed the configured
     * maximums down to {@link #preferredSize} on its longest edge.  Used by
     * decoders that produce a BufferedImage rather than a raw raster, so that a
     * very large image (e.g. a multi-thousand pixel CCITT fax) does not exhaust
     * the heap when Java2D later expands and transforms it for painting.
     *
     * @param image decoded image to bound.
     * @return a scaled copy, or the original image when no scaling is needed.
     */
    BufferedImage scaleReallyBigImage(BufferedImage image) {
        double scale = Math.min(preferredSize / (double) image.getWidth(),
                preferredSize / (double) image.getHeight());
        if (scale >= 1.0) {
            return image;
        }
        int width = (int) Math.ceil(image.getWidth() * scale);
        int height = (int) Math.ceil(image.getHeight() * scale);
        ColorModel cm = image.getColorModel();
        if (cm instanceof IndexColorModel) {
            // Indexed images carry their meaning in the palette, not the RGB
            // values: an image-mask stencil uses a transparent "paper" index that
            // IndexColorModel.hasAlpha() reports as false, so rebuilding it as RGB
            // (or a translucent ARGB buffer) collapses the paper onto an opaque
            // black background and the whole mask later paints solid.  Resample the
            // raster indices directly with nearest neighbour rather than going
            // through Graphics2D: a freshly created indexed image is filled with
            // index 0 (the opaque fill/ink colour for the default decode), so a
            // SrcOver drawImage would leave the transparent paper showing that ink.
            // Copying indices keeps the {0,1} values - including the transparent
            // index - exactly intact (bilinear would invent intermediate indices
            // anyway).
            WritableRaster src = image.getRaster();
            WritableRaster dst = cm.createCompatibleWritableRaster(width, height);
            int srcW = image.getWidth();
            int srcH = image.getHeight();
            for (int y = 0; y < height; y++) {
                int sy = Math.min(srcH - 1, (int) (y / scale));
                for (int x = 0; x < width; x++) {
                    int sx = Math.min(srcW - 1, (int) (x / scale));
                    dst.setSample(x, y, 0, src.getSample(sx, sy, 0));
                }
            }
            BufferedImage scaled = new BufferedImage(cm, dst, cm.isAlphaPremultiplied(), null);
            image.flush();
            return scaled;
        }
        // Non-indexed: key the target off the actual transparency (BITMASK as well
        // as full alpha), not just hasAlpha(), so any transparency survives.
        boolean transparent = cm.getTransparency() != Transparency.OPAQUE;
        BufferedImage scaled = transparent
                ? ImageUtility.createTranslucentCompatibleImage(width, height)
                : ImageUtility.createCompatibleImage(width, height);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        image.flush();
        return scaled;
    }
}
