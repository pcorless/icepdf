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


import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.PColorSpace;
import org.icepdf.core.util.Library;

import java.awt.image.BufferedImage;
import java.util.logging.Logger;

/**
 * ImageStream contains image data that is contains in an XObject of subtype
 * Image.
 *
 * @since 5.0
 */
public class ImageStream extends Stream {

    private static final Logger logger =
            Logger.getLogger(ImageStream.class.toString());

    public static final Name TYPE_VALUE = new Name("Image");

    private ImageParams imageParams;

    public ImageStream(Library l, DictionaryEntries h, byte[] rawBytes) {
        super(l, h, rawBytes);
        imageParams = new ImageParams(library, entries, null);
    }

    /**
     * Gets the image param wrapper class for quick access to parameters that are needed now!
     *
     * @return image params for the given image stream
     */
    public ImageParams getImageParams() {
        return imageParams;
    }

    /**
     * Gets the image object for the given resource.  This method can optionally
     * scale an image to reduce the total memory footprint or to increase the
     * perceived render quality on screen at low zoom levels.
     *
     * @param graphicsState graphic state for image or parent form
     * @param resources     resources containing image reference
     * @return new image object
     */
    public BufferedImage getImage(GraphicsState graphicsState, Resources resources){
        // check the pool encase we already parse this image.
        imageParams = new ImageParams(library, entries, resources);
        if (pObjectReference != null) {
            BufferedImage tmp = library.getImagePool().get(pObjectReference);
            if (tmp != null) {
                return tmp;
            }
        }
        // decode the given image.
        ImageDecoder imageDecoder = ImageDecoderFactory.createDecoder(this, graphicsState);
        BufferedImage decodedImage = imageDecoder.decode();

        // Fallback image code that will use pixel primitives to build out the image.
        if (decodedImage == null) {
            decodedImage = new RawDecoder(this, graphicsState).decode();
        }
        if (decodedImage != null) {
            if (imageParams.isImageMask()) {
                decodedImage = ImageUtility.applyExplicitMask(decodedImage, graphicsState.getFillColor());
            }
//            ImageUtility.displayImage(decodedImage, pObjectReference.toString() + decodedImage.getWidth() +
//                    " " + "x" + decodedImage.getHeight());
            // apply common mask and sMask processing
            ImageDecoder smaskDecoder = imageParams.getSMask(graphicsState);
            if (smaskDecoder != null) {
                BufferedImage smaskImage = smaskDecoder.decode();
//                ImageUtility.displayImage(smaskImage, "SMask " + entries.get(SMASK_KEY).toString());
                decodedImage = ImageUtility.applyExplicitSMask(decodedImage, smaskImage);
            }
            ImageDecoder maskDecoder = imageParams.getMask(graphicsState);
            if (maskDecoder != null) {
                BufferedImage maskImage = maskDecoder.decode();
//                ImageUtility.displayImage(maskImage, "Mask " + entries.get(MASK_KEY).toString());
                decodedImage = ImageUtility.applyExplicitMask(decodedImage, maskImage);
            }
//            if (maskDecoder != null || smaskDecoder != null)
//                ImageUtility.displayImage(decodedImage, "Final " + pObjectReference.toString());
        }
        return decodedImage;
    }

    public int getWidth() {
        return imageParams.getWidth();
    }

    public int getHeight() {
        return imageParams.getHeight();
    }

    public PColorSpace getColourSpace() {
        return imageParams.getColourSpace();
    }

    /**
     * Return a string description of the object.  Primarily used for debugging.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder(64);
        sb.append("Image stream= ");
        sb.append(entries);
        if (getPObjectReference() != null) {
            sb.append("  ");
            sb.append(getPObjectReference());
        }
        return sb.toString();
    }


}
