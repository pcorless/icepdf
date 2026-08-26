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
import org.icepdf.core.pobjects.graphics.CalGray;
import org.icepdf.core.pobjects.graphics.DeviceGray;
import org.icepdf.core.pobjects.graphics.PColorSpace;
import org.icepdf.core.pobjects.graphics.images.ImageDecoder;
import org.icepdf.core.pobjects.graphics.images.ImageParams;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.graphics.images.ImageUtility;
import org.icepdf.core.pobjects.graphics.images.references.ImageReference;

import java.awt.*;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
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
        // Where this drawing of the image sits.  Taken from the reference, which belongs to one Do,
        // rather than from the image stream, which is shared by every placement of the image.
        AffineTransform placement = imageReference.getPlacement();
        // update any mask as they can have a content for some scanned documents.
        checkAndBurnMasks(imageStream, placement, redactionPath, redactionColor);

        return burnImage(imageStream, placement, image, redactionPath, redactionColor, true);
    }

    private static void checkAndBurnMasks(ImageStream imageStream, AffineTransform placement,
                                          GeneralPath redactionPath, Color redactionColor) {
        ImageStream maskImageStream = imageStream.getImageParams().getMaskImageStream();
        ImageDecoder imageMaskDecoder = imageStream.getImageParams().getMask(null);
        if (imageMaskDecoder != null) {
            // The mask covers the same area of the page as the image it masks.
            BufferedImage imageMask = imageMaskDecoder.decode();
            burnImage(maskImageStream, placement, imageMask, redactionPath, redactionColor, false);
        }
        burnSoftMask(imageStream, placement, redactionPath);
    }

    /**
     * Makes the redacted area of a soft-masked image opaque.
     * <p>
     * An {@code /SMask} is a greyscale image whose samples are the base image's alpha - white is
     * opaque, black is transparent (PDF 32000-1 §11.6.5.3). Two things follow for a redaction.
     * <p>
     * The block burned into the base image is only visible where the soft mask lets it show, so a
     * redaction over a transparent part of the image paints a block nobody can see and the page
     * underneath shows through instead. The data is gone either way, but a redaction that appears to
     * have done nothing is not one anybody should ship.
     * <p>
     * The mask also outlines what was there. Even with the pixels replaced, the alpha channel still
     * carries the shape of the removed content - a cut-out signature or logo is recognisable from
     * its silhouette alone.
     * <p>
     * Both are answered by setting the covered samples to fully opaque. Note this is the opposite of
     * what filling the redaction colour would do: black in a soft mask means <em>transparent</em>, so
     * treating it like any other image would erase the redaction rather than apply it.
     */
    private static void burnSoftMask(ImageStream imageStream, AffineTransform placement,
                                     GeneralPath redactionPath) {
        ImageStream softMask = imageStream.getImageParams().getSMaskImageStream();
        ImageDecoder softMaskDecoder = imageStream.getImageParams().getSMask(null);
        if (softMask == null || softMaskDecoder == null) {
            return;
        }
        BufferedImage decoded = softMaskDecoder.decode();
        if (decoded == null) {
            return;
        }
        // A soft mask is greyscale by definition, and has to be written back that way.  Handed the
        // decoder's colour image, the raster encoder writes DeviceRGB - three bytes a pixel for a
        // one-channel image, and an /SMask that no longer says what §11.6.5.3 requires it to.
        BufferedImage mask = toGrayscale(decoded);
        Graphics2D graphics = mask.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.transform(userSpaceToImageSpace(placement, mask));
        graphics.fill(redactionPath);
        graphics.dispose();
        softMask.setDecodedImage(mask);
        if (softMask.getPObjectReference() != null) {
            softMask.getLibrary().getStateManager().addChange(new PObject(softMask,
                    softMask.getPObjectReference()));
        }
    }

    private static ImageStream burnImage(ImageStream imageStream, AffineTransform placement,
                                         BufferedImage image, GeneralPath redactionPath,
                                         Color redactionColor, boolean copyImage) {
        ImageParams imageParams = imageStream.getImageParams();
        if (imageParams.isImageMask()) {
            // A stencil says paint or do not paint, one bit per pixel, and takes its colour from
            // whatever fill colour is in force - so there is nothing here to fill with the redaction
            // colour.  Converting it to RGB like any other image threw away the one bit that
            // mattered and produced an image still declared /ImageMask true but carrying eight-bit
            // RGB samples, which no reader can make sense of.  Redacting one means setting its
            // samples to paint, so the region comes out solid instead of showing what was drawn.
            burnStencil(imageStream, placement, image, redactionPath);
            return imageStream;
        }
        if (copyImage && isGrayscale(imageParams)) {
            // A greyscale image stays greyscale.  Converting it to RGB the way everything else is
            // converted triples its data for no visible difference, which on a scanned page - the
            // common thing to redact - is most of the file.
            burnGrayscale(imageStream, placement, image, redactionPath, redactionColor);
            return imageStream;
        }
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
        imageGraphics.transform(userSpaceToImageSpace(placement, image));
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
     * Sets every sample a redaction covers to "paint", leaving a solid block of whatever colour the
     * stencil is drawn in.
     * <p>
     * The alternative - setting them to "do not paint" - erases just as much, but a redaction that
     * leaves a hole is easily read as an image that never had anything there. A solid block says
     * something was removed, which is what a redaction is for.
     *
     * @param imageStream   stencil being burned
     * @param image         its decoded one-bit raster
     * @param redactionPath area to remove, in user space
     */
    private static void burnStencil(ImageStream imageStream, AffineTransform placement,
                                    BufferedImage image, GeneralPath redactionPath) {
        Shape area = userSpaceToImageSpace(placement, image).createTransformedShape(redactionPath);
        Rectangle bounds = area.getBounds().intersection(
                new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        // Written through the raster rather than through Graphics2D: the paint value is a sample,
        // not a colour, and going via a colour would have it matched back to an index by whatever
        // the image's palette happens to be.
        WritableRaster raster = image.getRaster();
        int paintSample = paintSample(image);
        for (int y = bounds.y; y < bounds.y + bounds.height; y++) {
            for (int x = bounds.x; x < bounds.x + bounds.width; x++) {
                // Pixel centres, so a pixel counts as covered when the area actually crosses it.
                if (area.contains(x + 0.5, y + 0.5)) {
                    raster.setSample(x, y, 0, paintSample);
                }
            }
        }
        imageStream.setDecodedImage(image);
        if (imageStream.getPObjectReference() != null) {
            imageStream.getLibrary().getStateManager().addChange(new PObject(imageStream,
                    imageStream.getPObjectReference()));
        }
    }


    /**
     * Whether this image is a single greyscale channel that can be written back as one.
     * <p>
     * A colour-key mask is excluded: it needs the alpha channel of an RGB raster to carry which
     * pixels are masked out, which a single-channel image cannot hold.
     */
    private static boolean isGrayscale(ImageParams imageParams) {
        if (imageParams.isColorKeyMask() || imageParams.isImageMask()) {
            return false;
        }
        PColorSpace colourSpace = imageParams.getColourSpace();
        return (colourSpace instanceof DeviceGray || colourSpace instanceof CalGray)
                && imageParams.getBitsPerComponent() <= 8;
    }

    /**
     * Burns a greyscale image without turning it into a colour one.
     * <p>
     * The decoders hand back every image as RGB, so keeping an image greyscale means rebuilding a
     * single channel from what they return rather than merely declining to convert it.
     * <p>
     * Samples are written through the raster rather than filled with Graphics2D: painting a colour
     * into a greyscale image runs a colour-space conversion, and the sample that comes out is not the
     * one that went in - the same trap that had a soft mask's 160 written as 208.
     */
    private static void burnGrayscale(ImageStream imageStream, AffineTransform placement,
                                      BufferedImage image, GeneralPath redactionPath,
                                      Color redactionColor) {
        BufferedImage gray = toGrayscale(image);
        Shape area = userSpaceToImageSpace(placement, gray).createTransformedShape(redactionPath);
        Rectangle bounds = area.getBounds().intersection(
                new Rectangle(0, 0, gray.getWidth(), gray.getHeight()));
        WritableRaster raster = gray.getRaster();
        int sample = luminance(redactionColor);
        for (int y = bounds.y; y < bounds.y + bounds.height; y++) {
            for (int x = bounds.x; x < bounds.x + bounds.width; x++) {
                if (area.contains(x + 0.5, y + 0.5)) {
                    raster.setSample(x, y, 0, sample);
                }
            }
        }
        imageStream.setDecodedImage(gray);
        if (imageStream.getPObjectReference() != null) {
            imageStream.getLibrary().getStateManager().addChange(new PObject(imageStream,
                    imageStream.getPObjectReference()));
        }
    }

    /**
     * The redaction colour as a single greyscale sample. Black stays 0, which is what a redaction
     * uses by default; a colour is taken at its perceived brightness so the block reads the way the
     * caller meant it to.
     */
    private static int luminance(Color colour) {
        if (colour == null) {
            return 0;
        }
        return Math.min(255, Math.round(0.299f * colour.getRed()
                + 0.587f * colour.getGreen() + 0.114f * colour.getBlue()));
    }

    /**
     * Copies a decoded soft mask into a single-channel greyscale raster.
     * <p>
     * Sample by sample rather than by drawing it: painting an sRGB image into a greyscale one runs a
     * colour-space conversion, and a soft mask's samples are alpha values, not colours - 80 has to
     * come out as 80. The channels of a decoded mask are equal, so any of them is the sample.
     */
    private static BufferedImage toGrayscale(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            return image;
        }
        BufferedImage gray = new BufferedImage(image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = gray.getRaster();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                raster.setSample(x, y, 0, image.getRGB(x, y) & 0xFF);
            }
        }
        return gray;
    }

    /**
     * Which raster sample means "paint" in this decoded stencil.
     * <p>
     * A decoder resolves a stencil into an image whose painting samples are opaque and whose
     * remaining samples are transparent, so the palette says which is which. The mapping is not
     * fixed: it follows the image's {@code /Decode}, which is exactly the entry a redaction must not
     * assume.
     */
    private static int paintSample(BufferedImage image) {
        ColorModel colorModel = image.getColorModel();
        if (colorModel instanceof IndexColorModel) {
            IndexColorModel indexed = (IndexColorModel) colorModel;
            for (int index = 0, max = indexed.getMapSize(); index < max; index++) {
                if (indexed.getAlpha(index) != 0) {
                    return index;
                }
            }
        }
        // No palette to read it off, so fall back to the default reading, where 0 paints.
        return 0;
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
     * @param placement CTM in force at this drawing of the image
     * @param image     decoded raster, whose dimensions give the pixel grid
     * @return transform from user space to image space
     * @throws IllegalStateException if the placement cannot be used to locate the redaction
     */
    private static AffineTransform userSpaceToImageSpace(AffineTransform placement, BufferedImage image) {
        if (placement == null) {
            throw new IllegalStateException("Image has no placement matrix, so the area to redact " +
                    "cannot be located within it");
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
