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
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.util.concurrent.FutureTask;
import java.util.logging.Logger;

/**
 * The SmoothScaledImageReference scales large images using the
 * bufferedImage.getScaledInstance() method.  The scaled instance uses a
 * minimum of memory and can improve clarity of some CCITTFax images.
 *
 * @since 5.0
 */
public class SmoothScaledImageReference extends CachedImageReference {

    private static final Logger logger =
            Logger.getLogger(ScaledImageReference.class.toString());

    // scaled image size.
    private int width;
    private int height;

    protected SmoothScaledImageReference(ImageStream imageStream, Color fillColor, Resources resources) {
        super(imageStream, fillColor, resources);

        // get eh original image width.
        width = imageStream.getWidth();
        height = imageStream.getHeight();

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
                int width = this.width;
                Image scaledImage;
                // do image scaling on larger images.  This improves the softness
                // of some images that contains black and white text.
                double imageScale = 1.0;
                // do a little scaling on a the buffer
                if ((width >= 250 || height >= 250) && (width < 500 || height < 500)) {
                    imageScale = 0.90;
                } else if ((width >= 500 || height >= 500) && (width < 1000 || height < 1000)) {
                    imageScale = 0.80;
                } else if ((width >= 1000 || height >= 1000) && (width < 1500 || height < 1500)) {
                    imageScale = 0.70;
                } else if ((width >= 1500 || height >= 1500) && (width < 2000 || height < 2000)) {
                    imageScale = 0.60;
                } else if ((width >= 2000 || height >= 2000) && (width < 2500 || height < 2500)) {
                    imageScale = 0.50;
                } else if ((width >= 2500 || height >= 2500) && (width < 3000 || height < 3000)) {
                    imageScale = 0.40;
                } else if ((width >= 3000 || height >= 3000)) {
                    imageScale = 0.30;
                }
                // scale the image
                if (imageScale != 1.0) {
                    AffineTransform tx = new AffineTransform();
                    tx.scale(imageScale, imageScale);
                    AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BICUBIC);
                    BufferedImage sbim = op.filter(image, null);
                    image.flush();
                    image = sbim;
                }

            }
        } catch (Throwable e) {
            logger.warning("Error loading image: " + imageStream.getPObjectReference() +
                    " " + imageStream.toString());
        }
        return image;
    }
}
