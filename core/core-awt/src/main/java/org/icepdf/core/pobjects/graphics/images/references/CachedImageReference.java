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
package org.icepdf.core.pobjects.graphics.images.references;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.images.ImageStream;

import java.awt.image.BufferedImage;

/**
 * The Abstract CachedImageReference stores the decoded BufferedImage data in
 * an ImagePool referenced by the images PDF object number to ensure that if
 * a page is garbage collected the image can re fetched from the pool if
 * necessary.
 *
 * @since 5.0
 */
public abstract class CachedImageReference extends ImageReference {

    private final ImagePool imagePool;
    private boolean isNull;

    protected CachedImageReference(ImageStream imageStream, Name xobjectName, GraphicsState graphicsState,
                                   Resources resources, int imageIndex,
                                   Page page) {
        super(imageStream, xobjectName, graphicsState, resources, imageIndex, page);
        imagePool = imageStream.getLibrary().getImagePool();
        this.reference = imageStream.getPObjectReference();
    }

    /**
     * The decoded image, from the pool when it is there.  A pooled image is not kept in
     * this reference: the page's draw commands hold their references for as long as the
     * page is initialised, so a strong field here would pin every decoded image of every
     * open page and defeat the pool's soft references (1.pdf: ~300 MB that could never be
     * reclaimed).  An image without a pool key (an unnamed stream) is still held here.
     */
    public BufferedImage getImage() throws InterruptedException {
        if (isNull) {
            return null;
        }
        if (reference == null) {
            if (image == null && !isInTransientBackoff()) {
                image = createImage();
                if (image == null && !transientFailure) {
                    isNull = true;
                }
            }
            return image;
        }
        BufferedImage cached = imagePool.get(reference);
        if (cached != null) {
            image = null;
            return cached;
        }
        if (image != null) {
            // decoded before a pool entry existed (a constructor's eager decode).
            BufferedImage decoded = image;
            image = null;
            imagePool.put(reference, decoded);
            return decoded;
        }
        if (isInTransientBackoff()) {
            // a very recent decode ran out of memory; skip this frame's re-decode so
            // rapid repaints don't thrash the heap.  The image reappears once the
            // backoff window passes and memory has had a chance to free up.
            return null;
        }
        BufferedImage im = createImage();
        image = null;
        if (im != null) {
            imagePool.put(reference, im);
        } else if (!transientFailure) {
            // Only latch the image permanently off when it is genuinely
            // undecodable.  A transient failure (out of memory) must stay
            // retryable so the image reappears once the heap recovers.
            isNull = true;
        }
        return im;
    }

}
