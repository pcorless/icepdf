/*
 * Copyright 2006-2013 ICEsoft Technologies Inc.
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

import org.icepdf.core.pobjects.ImageStream;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.util.concurrent.FutureTask;
import java.util.logging.Logger;

/**
 * The ScaledImageReference stores the original image data  as well as several
 * smaller images instances which are painted at lower zoom values to improve
 * paint speeds.
 *
 * @since 5.0
 */
public class ScaledImageReference extends CachedImageReference {

    private static final Logger logger =
            Logger.getLogger(ScaledImageReference.class.toString());

    // scaled image size.
    private int width;
    private int height;

    protected ScaledImageReference(ImageStream imageStream, Color fillColor, Resources resources) {
        super(imageStream, fillColor, resources);

        // get eh original image width.
        int width = imageStream.getWidth();
        int height = imageStream.getHeight();

        // apply scaling factor
        double scaleFactor = 1.0;
        if (width > 1000 && width < 1500) {
            scaleFactor = 0.75;
        } else if (width > 1500) {
            scaleFactor = 0.5;
        }
        // update image size for any scaling.
        if (scaleFactor < 1.0) {
            this.width = (int) Math.ceil(width * scaleFactor);
            this.height = (int) Math.ceil(height * scaleFactor);
        } else {
            this.width = width;
            this.height = height;
        }

        // kick off a new thread to load the image, if not already in pool.
        ImagePool imagePool = imageStream.getLibrary().getImagePool();
        if (useProxy && imagePool.get(reference) == null) {
            futureTask = new FutureTask<BufferedImage>(this);
            Library.executeImage(futureTask);
        } else if (!useProxy && imagePool.get(reference) == null) {
            image = call();
        }
    }

    public ScaledImageReference(ImageReference imageReference, Color fillColor, Resources resources,
                                int width, int height) {
        super(imageReference.getImageStream(), fillColor, resources);

        this.width = width;
        this.height = height;

        // check for an repeated scale via a call from MipMap
        if (imageReference.isImage()) {
            image = imageReference.getImage();
        }

        // kick off a new thread to load the image, if not already in pool.
        ImagePool imagePool = imageStream.getLibrary().getImagePool();
        if (useProxy && imagePool.get(reference) == null) {
            futureTask = new FutureTask<BufferedImage>(this);
            Library.executeImage(futureTask);
        } else if (!useProxy && imagePool.get(reference) == null) {
            image = call();
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public BufferedImage call() {
        BufferedImage image = null;
        try {
            // get the stream image if need, otherwise scale what you have.
            if (image == null) {
                image = imageStream.getImage(fillColor, resources);
            }
            if (image != null) {
                int width = image.getWidth();
                int height = image.getHeight();
                // scale images if this.width/height were altered in the constructor
                if (width != this.width || height != this.height) {
                    ColorModel colorModel = image.getColorModel();
                    BufferedImage scaled = new BufferedImage(
                            colorModel,
                            colorModel.createCompatibleWritableRaster(this.width, this.height),
                            image.isAlphaPremultiplied(),
                            null
                    );
                    Graphics2D g = scaled.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.drawImage(image, 0, 0, this.width, this.height, null);
                    g.dispose();
                    image = scaled;
                }
            }
        } catch (Throwable e) {
            logger.warning("Error loading image: " + imageStream.getPObjectReference() +
                    " " + imageStream.toString());
        }
        return image;
    }
}
