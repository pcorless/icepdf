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
import org.icepdf.core.pobjects.ImageUtility;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.util.Library;

import java.awt.*;
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
                if (width > 1000 && width < 2000) {
                    width = 1000;
                } else if (width > 2000) {
                    width = 2000;
                }
                scaledImage = image.getScaledInstance(
                        width, -1, Image.SCALE_SMOOTH);
                image.flush();
                // store the scaled image for future repaints.
                image = ImageUtility.createBufferedImage(scaledImage);
            }
        } catch (Throwable e) {
            logger.warning("Error loading image: " + imageStream.getPObjectReference() +
                    " " + imageStream.toString());
        }
        return image;
    }
}
