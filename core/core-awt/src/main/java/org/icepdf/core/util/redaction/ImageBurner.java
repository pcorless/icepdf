/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.graphics.images.ImageDecoder;
import org.icepdf.core.pobjects.graphics.images.ImageParams;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.graphics.images.ImageUtility;
import org.icepdf.core.pobjects.graphics.images.references.ImageReference;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.util.logging.Logger;

/**
 * Burn the redactionPath into the given image stream.  The image stream holds the fully decode image with masking
 * data as well a converted colour space.
 *
 * @since 7.2.0
 */
public class ImageBurner {

    private static final Logger logger = Logger.getLogger(ImageBurner.class.getName());
    public static ImageStream burn(ImageReference imageReference, GeneralPath redactionPath,
                                   Color redactionColor) throws InterruptedException {
        ImageStream imageStream = imageReference.getImageStream();
        BufferedImage image = imageStream.getDecodedImage();
        if (image == null) {
            image = imageReference.getBaseImage();
        }
        // update any mask as they can have a content for some scanned documents.
        checkAndBurnMasks(imageStream, redactionPath, redactionColor);

        return burnImage(imageStream, image, redactionPath, redactionColor, true);
    }

    private static void checkAndBurnMasks(ImageStream imageStream, GeneralPath redactionPath,
                                          Color redactionColor) {
        ImageStream maskImageStream = imageStream.getImageParams().getMaskImageStream();
        ImageDecoder imageMaskDecoder = imageStream.getImageParams().getMask(null);
        if (imageMaskDecoder != null) {
            maskImageStream.setGraphicsTransformMatrix(imageStream.getGraphicsTransformMatrix());
            BufferedImage imageMask = imageMaskDecoder.decode();
            burnImage(maskImageStream, imageMask, redactionPath, redactionColor, false);
        }
    }

    private static ImageStream burnImage(ImageStream imageStream, BufferedImage image, GeneralPath redactionPath,
                                         Color redactionColor, boolean copyImage) {
        ImageParams imageParams = imageStream.getImageParams();
        // try a new image to get around index colour space issue.
        if (copyImage && !imageParams.isColorKeyMask()) {
            image = ImageUtility.createBufferedImage(image, BufferedImage.TYPE_INT_RGB);
        } else if (imageParams.isColorKeyMask()) {
            image = ImageUtility.createBufferedImage(image, BufferedImage.TYPE_INT_ARGB);
        }
        Graphics2D imageGraphics = image.createGraphics();
        imageGraphics.setColor(redactionColor);
        // Edge pixels must be fully painted; an antialiased edge keeps a fraction of what was
        // underneath, which is exactly what a redaction is removing.
        imageGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        imageGraphics.transform(userSpaceToImageSpace(imageStream, image));
        imageGraphics.fill(redactionPath);
        imageGraphics.dispose();
        // update the imageReference BufferedImage, as we may have multiple burns to apply
        imageStream.setDecodedImage(image);
        if (imageStream.getPObjectReference() != null) {
            imageStream.getLibrary().getStateManager().addChange(new PObject(imageStream,
                    imageStream.getPObjectReference()));
        }
        // check if we need to update the colorKeyMask.
        if (imageParams.isColorKeyMask()) {
            ImageUtility.encodeColorKeyMask(imageStream);
        }
        return imageStream;
    }

    /**
     * Maps user space onto the image's own pixel grid.
     * <p>
     * An image occupies the unit square in user space, placed by the CTM in force at its {@code Do},
     * so going the other way is that CTM inverted followed by the unit square scaled onto the pixel
     * grid - with y flipped, since PDF puts the origin at the bottom left and a raster puts it at the
     * top left.
     * <p>
     * Deriving this from the CTM rather than from the axis-aligned bounding box matters as soon as a
     * placement is anything but upright: a rotated or sheared image has a bounding box larger than
     * itself, and scaling by its width and height puts the redaction in the wrong part of the raster
     * and distorts its shape. A 90 degree rotation also swaps which of the box's dimensions
     * corresponds to the image's width.
     *
     * <p>
     * There is deliberately no approximate fallback. A missing placement matrix means the parser
     * never recorded one, and a matrix that cannot be inverted means the image was drawn collapsed
     * to a line or a point; in either case the area to redact cannot be located within the raster.
     * Burning some other part of the image would leave the caller believing content had been
     * removed when it had not, which is the one failure a redaction must not have. Better to stop
     * and say so.
     * <p>
     * Neither state is reachable through the content parser as it stands - it sets the matrix at
     * every {@code Do}, and an image collapsed to no area intersects no redaction, so the burn is
     * never asked for. These are precondition checks against a future caller, not a case with a
     * test behind it.
     *
     * @param imageStream image being burned
     * @param image       decoded raster, whose dimensions give the pixel grid
     * @return transform from user space to image space
     * @throws IllegalStateException if the placement cannot be used to locate the redaction
     */
    private static AffineTransform userSpaceToImageSpace(ImageStream imageStream, BufferedImage image) {
        AffineTransform placement = imageStream.getGraphicsTransformMatrix();
        if (placement == null) {
            throw new IllegalStateException("Image has no placement matrix, so the area to redact " +
                    "cannot be located within it: " + imageStream.getPObjectReference());
        }
        AffineTransform userToImage = new AffineTransform(
                image.getWidth(), 0, 0, -image.getHeight(), 0, image.getHeight());
        try {
            userToImage.concatenate(placement.createInverse());
        } catch (NoninvertibleTransformException e) {
            throw new IllegalStateException("Image placement matrix cannot be inverted, so the " +
                    "area to redact cannot be located within it: " + placement, e);
        }
        return userToImage;
    }
}
