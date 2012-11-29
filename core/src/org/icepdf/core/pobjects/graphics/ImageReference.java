/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.ImageStream;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Resources;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Abstract ImageReference defines the core methods used in ImageStreamReference
 * MipMappedImageReference and ScaledImageReference.  The creation of these
 * objects is handled by the ImageReferenceFactory.
 *
 * @since 5.0
 */
public abstract class ImageReference implements Runnable {

    protected final ReentrantLock lock = new ReentrantLock();

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
            aG.drawImage(image, aX, aY, aW, aH, null);
        }
    }

    /**
     * Creates a scaled image to match that of the instance vars width/height.
     *
     * @return decoded/encoded BufferedImge for the respective ImageStream.
     */
    protected BufferedImage createImage() {
        // block until thread comes back.
        try {
            lock.lock();
            return image;
        } finally {
            lock.unlock();
        }
    }
}
