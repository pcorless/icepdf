/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 4.1 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.util;


import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The <code>ImageCache</code> class is designed to cache image data to the
 * file system.  The cached file can then be read file the file system at a
 * later time.  The <code>ImageCache</code> class was created to help minimize
 * the amount of data that has to be kept in memory when opening graphic
 * heavy PDF files.  The <code>ImageCache</code> class also speeds up the
 * loading of a previously decoded image stream as it is much more effecient
 * to read the decode image from file then it is to decode the image bit stream
 * every time it is required.
 * <p/>
 * The cached image is writen to the user temporary folder and is identeifed by
 * "PDFImageStream" followed by seriers of numbers assigned from the objects
 * hash code.  Every time a new image cache is created the full path to the file
 * is recored in the CacheManager class which allows the CacheManager to delete
 * a documents cached images when the doucment is closed.
 *
 * @since 1.1
 */
public class ImageCache {

    private static final Logger logger =
            Logger.getLogger(ImageCache.class.toString());

    // temp file that is created for the cache
    private File tempFile = null;

    // length in bytes of the the image data
    private long length = 0;

    // is caching enabled or disabled
    private boolean isCached = false;

    // cache manager reference
    private CacheManager cacheManager = null;

    // flag for image scaling, only want to scale it once
    private boolean isScaled = false;

    // If caching is disabled, it is stored in memory with this var
    private BufferedImage imageStore;

    // disable/enable file caching
    private static boolean isCachingEnabled;

    // disable/inable file cahcing, overrides fileCachingSize.
    private static boolean scaleImages;


    static {
        // NOTE: currently disabled as file caching tends to slow things down more
        // then it helps.

        // sets if file caching is enabled or disabled.
        isCachingEnabled =
                Defs.sysPropertyBoolean("org.icepdf.core.imagecache.enabled",
                        true);

        // deside if large images will be scaled
        scaleImages =
                Defs.sysPropertyBoolean("org.icepdf.core.scaleImages",
                        true);

    }

    /**
     * Create new instance of ImageCache
     *
     * @param library reference to documents library
     */
    public ImageCache(Library library) {
        // get pointer to cache manager.
        cacheManager = library.getCacheManager();
    }

    /**
     * Writes Buffered imate to this object.  If images caching is enabled, the
     * images is written to disk, otherwise it is kept in memory.
     *
     * @param image
     */
    public void setImage(BufferedImage image) {
        setImage(image, isCached);
    }

    /**
     * Write the <code>image</code> to a temporary file in the users temp
     * directory.
     *
     * @param image      image to be cached.
     * @param useCaching use caching when saving image.
     */
    private void setImage(BufferedImage image, boolean useCaching) {
        try {
            if (useCaching && isCached && imageStore == image)
                return;
            if (imageStore != null && imageStore != image) {
                imageStore.flush();
                imageStore = null;
            }

            // if caching, write the image to file;
            if (useCaching) {//isCachingEnabled) {
                // create tmp file and write bytes to it.
                tempFile = File.createTempFile("PDFImageStream" +
                        this.getClass().hashCode(),
                        ".tmp");
                cacheManager.addCachedFile(tempFile.getAbsolutePath());

                // Delete temp file on exits, but dispose should do this too
                tempFile.deleteOnExit();

                ImageIO.write(image, "png", tempFile);

                // clean up the stream
                length = tempFile.length();
                // set cached flag
                isCached = true;
            } else {
                // use the store to keep track of the image.
                imageStore = image;
                isCached = false;
            }
        } catch (IOException e) {
            logger.log(Level.FINE,
                    "Error creating ImageCache temporary file.", e);
        }
    }

    /**
     * Read the cached image file and return the corresponding buffered image.
     *
     * @return buffered image contained in the image cache temporary file.
     */
    public BufferedImage readImage() {
        if (imageStore != null)
            return imageStore;

        // if caching, write the image to file;
        if (isCached) {//isCachingEnabled) {
            BufferedImage image = null;
            try {

                image = ImageIO.read(tempFile);
                // get length of temp file
                length = tempFile.length();

            } catch (IOException e) {
                logger.log(Level.FINE,
                        "Error creating ImageCache temporary file ", e);
            }
            // imageStore = image;
            return image;
        }
        return null;
    }

    /**
     * Dispose of this objects resources
     *
     * @param cache                    if true, image will be cached to disk, otherwise, image
     *                                 resources will be released.
     * @param imageRecoverableElsewise If the ImageCache is not the only way
     *                                 of getting back at the image later
     */
    public void dispose(boolean cache, boolean imageRecoverableElsewise) {
        // empty the image store
        if (imageStore != null) {
            // We don't look at if( !cache && isCached ) to delete
            //   the temp file, because the CacheManager handles it

            // cache to disk for fast access at a later time
            if (cache && isCachingEnabled && !isCached && !imageRecoverableElsewise) {
                setImage(imageStore, true);
            }
            // delete the resources used by the image source
            if (!cache || isCached || imageRecoverableElsewise) {
                //imageStore.flush();
                imageStore = null;
            }
        }
    }

    public void scaleImage(int width, int height) {
        if (scaleImages) {
            // Calling scaleBufferedImage() causes a spike in memory usage
            // For example, a source image that's 100KB in size, we spike 5.5MB,
            //   with 2.8MB remaining and 2.7MB getting gc'ed
            // System.out.println("Mem free: " + Runtime.getRuntime().freeMemory() + ", total:" + Runtime.getRuntime().totalMemory() + ", used: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()));

            setImage(scaleBufferedImage(readImage(), width, height));
            isScaled = true;
        } else {
            isScaled = false;
        }
    }

    public void setIsScaled(boolean flag) {
        isScaled = flag;
    }

    /**
     * Return the number of bytes used by this images when it is cached.
     *
     * @return number of bytes of the cached file.
     */
    public long getLength() {
        return length;
    }

    public boolean isScaled() {
        return isScaled;
    }

    public boolean isCachedSomehow() {
        return isCached || (imageStore != null);
    }

    public static BufferedImage scaleBufferedImage(BufferedImage bim, int width, int height) {
        // TODO: Use size of image to determine if will scale
        /*
        DataBuffer db = bim.getRaster().getDataBuffer();
        int dbNumElements = db.getSize();
        int dbBitsPerElement = DataBuffer.getDataTypeSize( db.getDataType() );
        int dbBytes = dbNumElements * dbBitsPerElement / 8;
        */

        double imageScale = 1.0;
        // do a little scaling on a the buffer
        if ((width >= 500 || height >= 500) &&
                (width < 1000 || height < 1000)) {
            imageScale = 0.80;
        } else if ((width >= 1000 || height >= 1000) &&
                (width < 1500 || height < 1500)) {
            imageScale = 0.70;
        } else if ((width >= 1500 || height >= 1500) &&
                (width < 2000 || height < 2000)) {
            imageScale = 0.60;
        } else if ((width >= 2000 || height >= 2000) &&
                (width < 2500 || height < 2500)) {
            imageScale = 0.50;
        } else if ((width >= 2500 || height >= 2500) &&
                (width < 3000 || height < 3000)) {
            imageScale = 0.40;
        } else if ((width >= 3000 || height >= 3000)) {
            imageScale = 0.30;
        }
        // scale the image
        if (imageScale < 1.0) {
            AffineTransform tx = new AffineTransform();
            tx.scale(imageScale, imageScale);
            AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
            BufferedImage sbim = op.filter(bim, null);
            bim.flush();
            bim = sbim;
        }

        return bim;
    }
}
