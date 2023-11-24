package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.graphics.images.ImageStream;
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
        Rectangle2D bbox = imageStream.getNormalizedBounds();
        // image coords need to be adjusted for any layout scaling
        double xScale = image.getWidth() / bbox.getWidth();
        double yScale = image.getHeight() / bbox.getHeight();
        Graphics2D imageGraphics = image.createGraphics();
        imageGraphics.setColor(Color.BLACK);
        imageGraphics.scale(xScale, -yScale);
        imageGraphics.translate(0, -bbox.getHeight());
        imageGraphics.translate(-bbox.getX(), -bbox.getY());
        imageGraphics.fill(redactionPath);
        imageGraphics.dispose();
        // update the imageReference BufferedImage, as we may have multiple burns to apply
        imageStream.setDecodedImage(image);
        return imageStream;
    }
}
