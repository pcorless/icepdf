package org.icepdf.core.pobjects.graphics.images.references;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.graphics.images.ImageStream;

import java.awt.image.BufferedImage;

/**
 * ImageContentWriterReference isn't actually used for rendering and is instead used a state placeholder for
 * ImageStreams so the ImageDrawCmd can be converted to postscript by the PostScriptEncoder.  The ImageStream
 * object contains a Buffered image which will be encoded and inserted into a content stream.
 */
public class ImageContentWriterReference extends ImageReference {


    public ImageContentWriterReference(ImageStream imageStream, Name xobjectName) {
        super(imageStream, xobjectName, null, null, 0, null);
        image = imageStream.getDecodedImage();
    }

    @Override
    public int getWidth() {
        return image.getWidth();
    }

    @Override
    public int getHeight() {
        return image.getHeight();
    }

    @Override
    public BufferedImage getImage() throws InterruptedException {
        return image;
    }

    @Override
    public BufferedImage call() throws Exception {
        return image;
    }
}
