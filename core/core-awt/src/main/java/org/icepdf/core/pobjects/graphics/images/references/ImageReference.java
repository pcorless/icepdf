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
package org.icepdf.core.pobjects.graphics.images.references;

import org.icepdf.core.events.PageImageEvent;
import org.icepdf.core.events.PageLoadingEvent;
import org.icepdf.core.events.PageLoadingListener;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.graphics.images.ImageUtility;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract ImageReference defines the core methods used in ImageStreamReference
 * MipMappedImageReference and ScaledImageReference.  The creation of these
 * objects is handled by the ImageReferenceFactory.
 *
 * @since 5.0
 */
public abstract class ImageReference implements Callable<BufferedImage> {

    private static final Logger logger =
            Logger.getLogger(ImageReference.class.getName());

    public static boolean useProxy;

    static {
        // decide if large images will be scaled
        useProxy = Defs.booleanProperty("org.icepdf.core.imageProxy", true);
    }

    protected FutureTask<BufferedImage> futureTask;

    protected ImageStream imageStream;
    protected GraphicsState graphicsState;
    protected Resources resources;
    protected BufferedImage image;
    protected Reference reference;
    protected Name xobjectName;

    protected int imageIndex;
    protected Page parentPage;
    /**
     * Set when the most recent {@link #createImage()} failed <em>transiently</em>
     * (out of memory / GCLocker allocation stall while another thread held a JNI
     * critical section) rather than because the image is genuinely undecodable.
     * A transient failure must not latch the reference permanently off — memory
     * may recover and a later repaint should retry the decode.
     */
    protected volatile boolean transientFailure;

    /**
     * Wall-clock time of the last transient (out-of-memory) decode failure.  Repaints
     * that arrive within {@link #transientRetryBackoffMs} of it skip the decode
     * entirely rather than re-running an expensive full-resolution decode on every
     * frame -- which, with the image pool cleared under pressure, would thrash the
     * heap.  Once the window passes the decode is retried, so the image reappears
     * automatically as soon as memory frees.
     */
    protected volatile long lastTransientFailureTime;

    protected static final long transientRetryBackoffMs =
            Defs.intProperty("org.icepdf.core.imageProxy.retryBackoffMs", 750);

    /**
     * Maximum time a page paint will block on a single image's decode future before
     * giving up on it <em>for this pass</em> and letting the rest of the page finish.
     * This is a safety net against a genuinely hung/deadlocked decode freezing the
     * whole capture; it is deliberately generous (15s) so a slow-but-valid decode
     * (e.g. a large JPEG2000) still blocks to completion rather than flickering in a
     * paint later.  The image is retried on a subsequent paint if the window is hit.
     */
    protected static final long decodeGetTimeoutMs =
            Defs.intProperty("org.icepdf.core.imageProxy.decodeTimeoutMs", 15000);

    protected ImageReference(ImageStream imageStream, Name xobjectName, GraphicsState graphicsState,
                             Resources resources, int imageIndex, Page parentPage) {
        this.imageStream = imageStream;
        this.xobjectName = xobjectName;
        this.graphicsState = graphicsState;
        this.resources = resources;
        this.imageIndex = imageIndex;
        this.parentPage = parentPage;
    }

    public abstract int getWidth();

    public abstract int getHeight();

    public abstract BufferedImage getImage() throws InterruptedException;

    /**
     * Submits this reference's decode ({@link #call()}) to the image executor, de-duplicating against any decode
     * already in flight for the same image so two references to one image don't both decode it.  The completed
     * image is published to the {@link ImagePool} as soon as the decode finishes (closing the window between
     * decode completion and the first {@code getImage()} call), and the in-flight marker is always cleared.
     */
    protected void submitDecode() {
        final ImagePool imagePool = imageStream.getLibrary().getImagePool();
        FutureTask<BufferedImage> mine = new FutureTask<BufferedImage>(this) {
            @Override
            protected void done() {
                try {
                    BufferedImage result = get();
                    if (result != null && reference != null) {
                        imagePool.put(reference, result);
                    }
                } catch (Exception e) {
                    // decode failed/cancelled; nothing to publish.
                } finally {
                    imagePool.removeInProgress(reference);
                }
            }
        };
        FutureTask<BufferedImage> existing = imagePool.registerInProgress(reference, mine);
        if (existing != null) {
            // another reference is already decoding this image; wait on its result.
            futureTask = existing;
        } else {
            futureTask = mine;
            Library.executeImage(mine);
        }
    }

    public void drawImage(Graphics2D aG, int aX, int aY, int aW, int aH) throws InterruptedException {
        BufferedImage image = getImage();
        if (image != null) {
            try {
                // Java2D sizes drawImage's destination raster to the whole
                // transformed image; at high zoom that is a page-sized raster
                // (hundreds of MB) and, across several render threads at once,
                // exhausts the heap (GCLocker allocation stalls) so the image
                // silently drops.  When the graphics carries a clip smaller than
                // the image, blit only the source sub-region that maps into that
                // clip so the raster is bounded by the visible viewport, not the
                // whole page.  Skipped while a CMYK group is being rasterised, as
                // captureCmykInk assumes the full image was drawn.
                if (!drawClippedToViewport(aG, image, aX, aY, aW, aH)) {
                    aG.drawImage(image, aX, aY, aW, aH, null);
                }
                // GH-501 step 2: if a CMYK group is being rasterised, blit this
                // image's TRUE preserved CMYK samples into the ink sink, aligned to
                // the transform just used here (no-op on the normal render path).
                ImageUtility.captureCmykInk(imageStream, aG, aX, aY, aW, aH);
            } catch (OutOfMemoryError e) {
                // Java2D sizes the destination raster to the transformed image
                // bounds, so re-scaling the source cannot shrink it (and a
                // redraw would just OOM again). Skip this image and let the rest
                // of the page render rather than aborting the whole capture.
                logger.warning("Out of memory painting image, skipping " +
                        imageStream.getPObjectReference() +
                        " (" + imageStream.getImageParams().getWidth() + "x" +
                        imageStream.getImageParams().getHeight() + ")");
                image.flush();
                this.image = null;
            } catch (Exception e) {
                logger.warning("There was a problem painting image, falling back to scaled instance " +
                        imageStream.getPObjectReference() +
                        "(" + imageStream.getImageParams().getWidth() + "x" + imageStream.getImageParams().getHeight() + ")");
                int width = image.getWidth(null);
                Image scaledImage;
                // do image scaling on larger images.  This improves the softness
                // of some images that contains black and white text.
                if (width > 1000 && width < 2000) {
                    width = 1000;
                } else if (width > 2000) {
                    width = 2000;
                }
                scaledImage = image.getScaledInstance(width, -1, Image.SCALE_SMOOTH);
                image.flush();
                // try drawing the scaled image one more time.
                aG.drawImage(scaledImage, aX, aY, aW, aH, null);
                // store the scaled image for future repaints.
                this.image = ImageUtility.createBufferedImage(scaledImage);
            }
        }
    }

    /**
     * Blits only the portion of {@code image} that maps into the graphics' current
     * clip, so Java2D's destination raster is bounded by the visible viewport rather
     * than the whole (possibly page-sized) transformed image.  The image is drawn in
     * the unit-square user space of a PDF image, where {@code getClipBounds()} rounds
     * away all sub-pixel detail; the clip is therefore inverse-mapped into the image's
     * own pixel space (which is large and precise) to pick the visible source rectangle.
     *
     * @return {@code true} if the clipped sub-region was drawn (or was fully outside
     * the clip and skipped); {@code false} if the caller should fall back to a normal
     * full-image {@code drawImage}.
     */
    private boolean drawClippedToViewport(Graphics2D aG, BufferedImage image,
                                          int aX, int aY, int aW, int aH) {
        // captureCmykInk assumes the whole image was drawn -> don't trim under it.
        if (ImageUtility.isCmykInkCapturing()) {
            return false;
        }
        Shape clip = aG.getClip();
        if (clip == null || aW <= 0 || aH <= 0) {
            return false;
        }
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        if (imgW <= 0 || imgH <= 0) {
            return false;
        }
        // source-pixel (px,py) -> user: (aX + px*aW/imgW, aY + py*aH/imgH)
        AffineTransform imgToUser = new AffineTransform();
        imgToUser.translate(aX, aY);
        imgToUser.scale(aW / (double) imgW, aH / (double) imgH);
        final Rectangle2D srcBounds;
        try {
            srcBounds = imgToUser.createInverse().createTransformedShape(clip).getBounds2D();
        } catch (NoninvertibleTransformException e) {
            return false;
        }
        // clamp the visible source rectangle to the image, with a 1px margin so
        // edge interpolation has neighbours (the seam falls outside the clip anyway).
        int sx = Math.max(0, (int) Math.floor(srcBounds.getMinX()) - 1);
        int sy = Math.max(0, (int) Math.floor(srcBounds.getMinY()) - 1);
        int sx2 = Math.min(imgW, (int) Math.ceil(srcBounds.getMaxX()) + 1);
        int sy2 = Math.min(imgH, (int) Math.ceil(srcBounds.getMaxY()) + 1);
        int sw = sx2 - sx;
        int sh = sy2 - sy;
        if (sw <= 0 || sh <= 0) {
            // image lies entirely outside the visible clip; nothing to paint.
            return true;
        }
        if (sx == 0 && sy == 0 && sw == imgW && sh == imgH) {
            // whole image is visible; let the caller do the plain (cheaper) draw.
            return false;
        }
        // getSubimage shares the parent raster (no pixel copy); draw it in place by
        // composing the image->user map with the sub-image's pixel offset.
        BufferedImage sub = image.getSubimage(sx, sy, sw, sh);
        AffineTransform draw = new AffineTransform(imgToUser);
        draw.translate(sx, sy);
        aG.drawImage(sub, draw, null);
        return true;
    }

    /**
     * Gets the original image unaltered, bypassing any ImageReference modifications.
     *
     * @return
     */
    public BufferedImage getBaseImage() {
        return imageStream.getImage(graphicsState, resources);
    }

    /**
     * Creates a scaled image to match that of the instance vars width/height.
     *
     * @return decoded/encoded BufferedImage for the respective ImageStream.
     * @throws InterruptedException interrupted has occurred.
     */
    protected BufferedImage createImage() throws InterruptedException {
        transientFailure = false;
        try {
            // block until thread comes back, but only for a bounded time so a slow or
            // stuck decode can't freeze the whole page capture (see decodeGetTimeoutMs).
            if (futureTask != null) {
                image = futureTask.get(decodeGetTimeoutMs, TimeUnit.MILLISECONDS);
            }
            if (image == null) {
                image = call();
            }
        } catch (TimeoutException e) {
            // decode still running; keep the future so we resume waiting on the SAME
            // decode next time, and flag transient so getImage() neither latches the
            // image off nor re-hammers it every frame (a short backoff applies).  This
            // paint finishes without the image; a later paint fills it in.
            transientFailure = true;
            lastTransientFailureTime = System.currentTimeMillis();
            logger.warning(() -> "Image decode timed out (" + decodeGetTimeoutMs +
                    "ms), painting page without it for now: " + imageStream.getPObjectReference());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.fine("Image loading interrupted");
            throw new InterruptedException(e.getMessage());
        } catch (ExecutionException e) {
            // decode ran on the image pool; unwrap to tell a momentary heap
            // exhaustion (GCLocker stall) from a genuinely undecodable image.
            if (isTransientDecodeFailure(e.getCause())) {
                markTransientFailure(e.getCause());
            } else {
                logger.log(Level.WARNING, "Image loading execution exception", e);
            }
        } catch (OutOfMemoryError e) {
            // non-proxy path: the decode ran inline on this thread and OOM'd.
            markTransientFailure(e);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Image loading execution exception", e);
        }
        return image;
    }

    /**
     * A decode failure is transient (retryable) when it was caused by running out
     * of memory -- including the "Retried waiting for GCLocker too often" allocation
     * failure the JVM raises as an {@link OutOfMemoryError} when image ops holding a
     * JNI critical section stall the collector.  Such failures clear once other work
     * frees the heap, so the image must not be latched permanently off.
     */
    private static boolean isTransientDecodeFailure(Throwable cause) {
        return cause instanceof OutOfMemoryError;
    }

    /**
     * Flags the current decode as a transient failure and resets so the next
     * {@link #getImage()} re-decodes, rather than replaying the already-completed
     * failed future or treating the null result as a permanent "undecodable" verdict.
     */
    private void markTransientFailure(Throwable cause) {
        transientFailure = true;
        lastTransientFailureTime = System.currentTimeMillis();
        futureTask = null;
        image = null;
        logger.log(Level.WARNING, cause,
                () -> "Out of memory decoding image, backing off then retrying: " +
                        imageStream.getPObjectReference());
    }

    /**
     * True while a repaint should skip re-decoding after a recent transient (OOM)
     * failure, so rapid repaints don't re-run a full-resolution decode every frame
     * and thrash the heap.  Clears itself once {@link #transientRetryBackoffMs} has
     * elapsed, letting the image reappear automatically when memory frees.
     */
    protected boolean isInTransientBackoff() {
        return transientFailure &&
                (System.currentTimeMillis() - lastTransientFailureTime) < transientRetryBackoffMs;
    }

    public ImageStream getImageStream() {
        return imageStream;
    }

    public boolean isImage() {
        return image != null;
    }

    public Name getXobjectName() {
        return xobjectName;
    }

    protected void notifyPageImageLoadedEvent(long duration, boolean interrupted) {
        if (parentPage != null) {
            PageImageEvent pageLoadingEvent =
                    new PageImageEvent(parentPage, imageIndex,
                            parentPage.getImageCount(), duration, interrupted);
            PageLoadingListener client;
            List<PageLoadingListener> pageLoadingListeners =
                    parentPage.getPageLoadingListeners();
            for (int i = pageLoadingListeners.size() - 1; i >= 0; i--) {
                client = pageLoadingListeners.get(i);
                client.pageImageLoaded(pageLoadingEvent);
            }
        }
    }

    protected void notifyImagePageEvents(long duration) {
        // sound out image loading event.
        notifyPageImageLoadedEvent(duration, image == null);
        // check to see if we're done loading and all we were waiting on was
        // the completion of this image load.
        if (parentPage != null && imageIndex == parentPage.getImageCount() &&
                parentPage.isPageInitialized() && parentPage.isPagePainted()) {
            notifyPageLoadingEnded();
        }
    }

    protected void notifyPageLoadingEnded() {
        if (parentPage != null) {
            PageLoadingEvent pageLoadingEvent =
                    new PageLoadingEvent(parentPage, parentPage.isInitiated());
            PageLoadingListener client;
            List<PageLoadingListener> pageLoadingListeners =
                    parentPage.getPageLoadingListeners();
            for (int i = pageLoadingListeners.size() - 1; i >= 0; i--) {
                client = pageLoadingListeners.get(i);
                client.pageLoadingEnded(pageLoadingEvent);
            }
        }
    }
}
