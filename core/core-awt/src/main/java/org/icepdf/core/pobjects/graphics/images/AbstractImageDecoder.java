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
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
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
        BufferedImage scaled = ImageUtility.hasAlpha(image)
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
