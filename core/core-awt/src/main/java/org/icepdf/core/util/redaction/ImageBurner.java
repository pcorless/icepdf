package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.graphics.images.ImageDecoder;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.graphics.images.ImageUtility;
import org.icepdf.core.pobjects.graphics.images.references.ImageReference;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Burn the redactionPath into the given image stream.  The image stream holds the fully decode image with masking
 * data as well a converted colour space.
 */
public class ImageBurner {
    public static ImageStream burn(ImageReference imageReference, GeneralPath redactionPath) throws InterruptedException {
        ImageStream imageStream = imageReference.getImageStream();
        BufferedImage image = imageStream.getDecodedImage();
        if (image == null) {
            image = imageReference.getBaseImage();
        }
        // update any mask as they can have a content for some scanned documents.
        checkAndBurnMasks(imageStream, redactionPath);

        return burnImage(imageStream, image, redactionPath, true);
    }

    private static void checkAndBurnMasks(ImageStream imageStream, GeneralPath redactionPath) {
        ImageStream maskImageStream = imageStream.getImageParams().getMaskImageStream();
        ImageDecoder imageMaskDecoder = imageStream.getImageParams().getMask(null);
        if (imageMaskDecoder != null) {
            maskImageStream.setGraphicsTransformMatrix(imageStream.getGraphicsTransformMatrix());
            BufferedImage imageMask = imageMaskDecoder.decode();
            burnImage(maskImageStream, imageMask, redactionPath, false);
        }
    }

    private static ImageStream burnImage(ImageStream imageStream, BufferedImage image, GeneralPath redactionPath,
                                         boolean copyImage) {

        Rectangle2D bbox = imageStream.getNormalizedBounds();
        // image coords need to be adjusted for any layout scaling
        double xScale = image.getWidth() / bbox.getWidth();
        double yScale = image.getHeight() / bbox.getHeight();
        // try a new image to get around index colour space issue.
        if (copyImage) {
            image = ImageUtility.createBufferedImage(image, BufferedImage.TYPE_INT_RGB);
        }
        Graphics2D imageGraphics = image.createGraphics();
        imageGraphics.setColor(Color.BLACK);
        imageGraphics.scale(xScale, -yScale);
        imageGraphics.translate(0, -bbox.getHeight());
        imageGraphics.translate(-bbox.getX(), -bbox.getY());
        imageGraphics.fill(redactionPath);
        imageGraphics.dispose();
        // update the imageReference BufferedImage, as we may have multiple burns to apply
        imageStream.setDecodedImage(image);
        if (imageStream.getPObjectReference() != null) {
            imageStream.getLibrary().getStateManager().addChange(new PObject(imageStream,
                    imageStream.getPObjectReference()));
        }
        ImageUtility.displayImage(imageStream.getDecodedImage(),
                imageStream.getPObjectReference().toString() + image.getWidth() +
                        " " + "x" + image.getHeight());
        return imageStream;
    }
}
