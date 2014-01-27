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
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.util.Defs;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract ImageReference defines the core methods used in ImageStreamReference
 * MipMappedImageReference and ScaledImageReference.  The creation of these
 * objects is handled by the ImageReferenceFactory.
 *
 * @since 5.0
 */
public abstract class ImageReference implements Callable<BufferedImage> {

    private static final Logger logger =
            Logger.getLogger(ImageReference.class.toString());

    protected static boolean useProxy;

    static {
        // decide if large images will be scaled
        useProxy = Defs.booleanProperty("org.icepdf.core.imageProxy", true);
    }

    protected FutureTask<BufferedImage> futureTask;

    protected ImageStream imageStream;
    protected Color fillColor;
    protected Resources resources;
    protected BufferedImage image;
    protected Reference reference;

    protected ImageReference(ImageStream imageStream, Color fillColor, Resources resources) {
        this.imageStream = imageStream;
        this.fillColor = fillColor;
        this.resources = resources;
    }

    public abstract int getWidth();

    public abstract int getHeight();

    public abstract BufferedImage getImage();

    public void drawImage(Graphics2D aG, int aX, int aY, int aW, int aH) {
        BufferedImage image = getImage();
        if (image != null) {
            try {
                aG.drawImage(image, aX, aY, aW, aH, null);
            } catch (Throwable e) {
                logger.warning("There was a problem painting image, falling back to scaled instance " +
                        imageStream.getPObjectReference() +
                        "(" + imageStream.getWidth() + "x" + imageStream.getHeight() + ")");
                int width = image.getWidth(null);
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
                // try drawing the scaled image one more time.
                aG.drawImage(scaledImage, aX, aY, aW, aH, null);
                // store the scaled image for future repaints.
                this.image = ImageUtility.createBufferedImage(scaledImage);
            }
        }
    }

    /**
     * Creates a scaled image to match that of the instance vars width/height.
     *
     * @return decoded/encoded BufferedImge for the respective ImageStream.
     */
    protected BufferedImage createImage() {
        try {
            // block until thread comes back.
            if (futureTask != null) {
                image = futureTask.get();
            }
            if (image == null) {
                image = call();
            }
        } catch (InterruptedException e) {
            logger.warning("Image loading interrupted");
        } catch (Exception e) {
            logger.log(Level.FINE, "Image loading execution exception", e);
        }
        return image;
    }

    public ImageStream getImageStream() {
        return imageStream;
    }

    public boolean isImage() {
        return image != null;
    }
}
