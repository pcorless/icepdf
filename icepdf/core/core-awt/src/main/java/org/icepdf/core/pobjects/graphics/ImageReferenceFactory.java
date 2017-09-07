/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.util.Defs;

/**
 * The ImageReferenceFactory determines which implementation of the
 * Image Reference should be created.  The ImageReference type can be specified
 * by the following system properties or alternatively by the enum type.
 * <ul>
 * <li>org.icepdf.core.imageReference = default</li>
 * <li>org.icepdf.core.imageReference = scaled</li>
 * <li>org.icepdf.core.imageReference = mipmap</li>
 * <li>org.icepdf.core.imageReference = smoothScaled</li>
 * </ul>
 * The default value returns an unaltered image,  scaled returns a scaled
 * image instance and there MIP mapped returns/picks a scaled image that
 * best fits the current zoom level for a balance of render speed and quality.
 *
 * @see MipMappedImageReference
 * @see ImageStreamReference
 * @see ScaledImageReference
 * @since 5.0
 */
public class ImageReferenceFactory {

    // allow scaling of large images to improve clarity on screen

    public enum ImageReference {
        DEFAULT, SCALED, MIP_MAP, SMOOTH_SCALED, BLURRED;


    }

    public static ImageReference imageReferenceType;

    static {
        // decide if large images will be scaled
        String imageReferenceType =
                Defs.sysProperty("org.icepdf.core.imageReference",
                        "default");
        ImageReferenceFactory.imageReferenceType = getImageReferenceType(imageReferenceType);
    }

    private ImageReferenceFactory() {
    }

    public static ImageReference getImageReferenceType() {
        return imageReferenceType;
    }

    public static void setImageReferenceType(ImageReference imageReferenceType) {
        ImageReferenceFactory.imageReferenceType = imageReferenceType;
    }

    /**
     * Takes a given imageReferenceType name and returns the associated enum type.
     *
     * @param imageReferenceType image type to get enum for.
     * @return associated ImageReference enum or ImageReference.DEFAULT if no mapping can be found.
     */
    public static ImageReference getImageReferenceType(String imageReferenceType) {
        ImageReference scaleType;
        if ("scaled".equals(imageReferenceType) || "SCALED".equals(imageReferenceType)) {
            scaleType = ImageReference.SCALED;
        } else if ("mipmap".equals(imageReferenceType) || "MIP_MAP".equals(imageReferenceType)) {
            scaleType = ImageReference.MIP_MAP;
        } else if ("smoothScaled".equals(imageReferenceType) || "SMOOTH_SCALED".equals(imageReferenceType)) {
            scaleType = ImageReference.SMOOTH_SCALED;
        } else if ("blurred".equals(imageReferenceType) || "BLURRED".equals(imageReferenceType)) {
            scaleType = ImageReference.BLURRED;
        } else {
            scaleType = ImageReference.DEFAULT;
        }
        return scaleType;
    }

    /**
     * Gets an instance of an ImageReference object for the given image data.
     * The ImageReference is specified by the system property org.icepdf.core.imageReference
     * or by the static instance variable scale type.
     *
     * @param imageStream   image data
     * @param resources     parent resource object.
     * @param graphicsState image graphic state.
     * @return newly create ImageReference.
     */
    public static org.icepdf.core.pobjects.graphics.ImageReference
    getImageReference(ImageStream imageStream, Resources resources, GraphicsState graphicsState,
                      Integer imageIndex, Page page) {
        switch (imageReferenceType) {
            case SCALED:
                return new ScaledImageReference(imageStream, graphicsState, resources, imageIndex, page);
            case SMOOTH_SCALED:
                return new SmoothScaledImageReference(imageStream, graphicsState, resources, imageIndex, page);
            case MIP_MAP:
                return new MipMappedImageReference(imageStream, graphicsState, resources, imageIndex, page);
            case BLURRED:
                return new BlurredImageReference(imageStream, graphicsState, resources, imageIndex, page);
            default:
                return new ImageStreamReference(imageStream, graphicsState, resources, imageIndex, page);
        }
    }

}
