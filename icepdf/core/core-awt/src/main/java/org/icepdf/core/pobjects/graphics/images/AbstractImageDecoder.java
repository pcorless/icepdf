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

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.logging.Logger;

public abstract class AbstractImageDecoder implements ImageDecoder {

    private static final Logger logger =
            Logger.getLogger(AbstractImageDecoder.class.toString());

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
        return raster.getWidth() > maxImageWidth && raster.getHeight() > maxImageHeight;
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
}
