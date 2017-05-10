package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.ImageStream;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.Library;

import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * BlurredImageReference graph attempts to smooth out images using convolution.  The implementation takes advantage of
 * the java.awt.image.Kernel class to apply the matrix that defines the convolution.  The default blur factor is 3 which
 * seems to provide a nice balance,  the value can go as low as 2 for just a light blur or as high as 25 for a very
 * blurred image result.  This image refernce format is best suited for TIFF images that are hard to read at small zoom
 * levels.
 * <p>
 * The blur value can be set with the system property org.icepdf.core.imageReference.blurred.dimension and once again
 * the default value is 3.  The blurring algorithm has a minimum image size associated with it.  To small an image and
 * the blur effect looks less then ideal.  The default values for minimum image size is 1800x2200 and can be set with either
 * org.icepdf.core.imageReference.blurred.minwidth or org.icepdf.core.imageReference.blurred.minheight.  However
 * smaller values can lead  to missing content on low res images.
 *
 * @since 6.2.4
 */
public class BlurredImageReference extends CachedImageReference {

    private static final Logger logger =
            Logger.getLogger(ImageStreamReference.class.toString());

    private static int dimension, minWidth, minHeight;

    static {
        dimension = Defs.intProperty("org.icepdf.core.imageReference.blurred.dimension", 3);
        minWidth = Defs.intProperty("org.icepdf.core.imageReference.blurred.minwidth", 1800);
        minHeight = Defs.intProperty("org.icepdf.core.imageReference.blurred.minheight", 2200);
    }

    private static float[] matrix;

    static {
        float size = dimension * dimension;
        matrix = new float[(int) size];
        for (int i = 0; i < size; i++) {
            matrix[i] = 1.0f / size;
        }
    }

    protected BlurredImageReference(ImageStream imageStream, GraphicsState graphicsState,
                                    Resources resources, int imageIndex, Page page) {
        super(imageStream, graphicsState, resources, imageIndex, page);
        // kick off a new thread to load the image, if not already in pool.
        ImagePool imagePool = imageStream.getLibrary().getImagePool();
        if (useProxy && imagePool.get(reference) == null) {
            futureTask = new FutureTask<BufferedImage>(this);
            Library.executeImage(futureTask);
        } else if (!useProxy && imagePool.get(reference) == null) {
            image = call();
        }
    }

    public BufferedImage call() {
        BufferedImage image = null;
        long start = System.nanoTime();
        try {
            image = imageStream.getImage(graphicsState, resources);
            if (image.getWidth() > minWidth && image.getHeight() > minHeight) {
                BufferedImageOp op = new ConvolveOp(new Kernel(dimension, dimension, matrix));
                image = op.filter(image, null);
            }
        } catch (Throwable e) {
            logger.log(Level.WARNING, "Error loading image: " + imageStream.getPObjectReference() +
                    " " + imageStream.toString(), e);
        }
        long end = System.nanoTime();
        notifyImagePageEvents((end - start));
        return image;
    }

    @Override
    public int getWidth() {
        return imageStream.getWidth();
    }

    @Override
    public int getHeight() {
        return imageStream.getHeight();
    }

}
