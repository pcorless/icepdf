package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.graphics.images.ImageUtility;
import org.icepdf.core.pobjects.graphics.images.references.ImageReference;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;

/**
 * Burn the redactionPath into the given image stream.
 */
public class ImageBurner {
    public static void burn(ImageReference imageReference, GeneralPath redactionPath) throws InterruptedException {
        ImageStream imageStream = imageReference.getImageStream();
        BufferedImage image = imageReference.getImage();
        Graphics2D imageGraphics = image.createGraphics();
        imageGraphics.setColor(Color.BLACK);
        imageGraphics.scale(1, -1);
        imageGraphics.translate(0, -image.getHeight());
        // todo revert offset as the image start at 0, 0, not x,y in the layout
        imageGraphics.fill(redactionPath);
        imageGraphics.dispose();

        ImageUtility.displayImage(image, imageStream.getPObjectReference().toString() + image.getWidth() +
                " " + "x" + image.getHeight());

        // create a mask using the redactionPaths

        // apply the mask to the image
        // write a new images stream, add new colorspace,
    }
}
