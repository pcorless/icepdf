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
package org.icepdf.ri.common.views;

import org.icepdf.core.events.PaintPageEvent;
import org.icepdf.core.events.PaintPageListener;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.util.*;
import org.icepdf.ri.common.views.listeners.DefaultPageViewLoadingListener;
import org.icepdf.ri.common.views.listeners.PageViewLoadingListener;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.lang.ref.SoftReference;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class contains all the functionality for showing a pages content.   This view works closely with the clip
 * provided by a parent JScrollPane component to optimize memory usage.  Page content is painted to a back buffer
 * which is painted by the component when ready.  The back buffer is scaled on subsequent paints to show content and
 * is later replaced with a new buffer that is painted with the current page properties.
 */
public abstract class AbstractPageViewComponent
        extends JLayeredPane
        implements PageViewComponent {

    private static final Logger logger =
            Logger.getLogger(AbstractPageViewComponent.class.getName());

    protected static final int PAGE_BOUNDARY_BOX = Page.BOUNDARY_CROPBOX;

    private static Color pageColor;
    protected static int pageBufferPadding;
    protected static boolean progressivePaint;

    static {
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.paper.color", "#FFFFFF");
            int colorValue = ColorUtil.convertColor(color);
            pageColor =
                    new Color(colorValue >= 0 ? colorValue :
                            Integer.parseInt("FFFFFF", 16));
        } catch (NumberFormatException e) {
            logger.warning("Error reading page paper color.");
        }
        // buffer size padding in pixels
        pageBufferPadding = Defs.intProperty("org.icepdf.core.views.bufferpadding", 250);
        // progressive paint of first page loat.
        progressivePaint = Defs.booleanProperty("org.icepdf.core.views.page.progressivePaint", true);
    }

    // Whether the worker bakes annotations into the page back buffer. True for plain page views
    // (e.g. thumbnails); PageViewComponentImpl sets it false because it renders annotations as
    // separate live Swing components on top of the buffer instead.
    protected boolean paintAnnotations = true;
    // Always false: search highlights are painted on the EDT in paintTextSelection() so they can
    // track live search state, rather than being baked into the worker's buffer.
    protected final boolean paintSearchHighlight = false;

    // view mvc parents
    protected DocumentView parentDocumentView;
    protected DocumentViewModel documentViewModel;
    protected DocumentViewController documentViewController;

    // scrollPane is very important for optimization of multiple page views.
    protected PageTree pageTree;
    protected int pageIndex;

    // page properties for a given view state.
    protected Rectangle pageSize;
    protected float pageZoom, pageRotation;
    protected int pageBoundaryBox;
    protected PageBufferStore pageBufferStore;
    // systems graphics configuration for creating a pages back buffer.
    protected GraphicsConfiguration graphicsConfiguration;

    // Main worker task. Assigned/read on the EDT (paint and property-change paths) but cancelled in
    // dispose(), which may run from arbitrary teardown threads, so volatile for reference visibility.
    protected volatile FutureTask<Object> pageImageCaptureTask;

    public AbstractPageViewComponent(DocumentViewModel documentViewModel, PageTree pageTree,
                                     final int pageIndex, int width, int height) {
        // needed to propagate mouse events.
        this.documentViewModel = documentViewModel;
        this.pageTree = pageTree;
        this.pageIndex = pageIndex;

        // current state.
        if (documentViewModel != null) {
            pageZoom = documentViewModel.getViewZoom();
            pageRotation = documentViewModel.getViewRotation();
            pageBoundaryBox = documentViewModel.getPageBoundary();
        } else {
            pageZoom = 1.0f;
            pageRotation = 0;
            pageBoundaryBox = PAGE_BOUNDARY_BOX;
        }

        // set up the store for the back buffer and current clip; its pin budget is
        // shared per-document so pages of the same document share a pin cap.
        pageBufferStore = new PageBufferStore(budgetFor(documentViewModel));

        // initialize page size
        pageSize = new Rectangle();
        if (documentViewModel != null && width == 0 && height == 0) {
            calculatePageSize(pageSize, documentViewModel.getViewRotation(), documentViewModel.getViewZoom());
        } else {
            pageSize.setSize(width, height);
        }
    }

    public Dimension getPreferredSize() {
        return pageSize.getSize();
    }

    // Intentionally reports the logical page size (zoom/rotation applied) rather than the
    // component's allocated bounds, so callers that query getSize() get page dimensions.
    public Dimension getSize() {
        return pageSize.getSize();
    }

    public void clearSelectedText() {
        // on mouse click clear the currently selected sprints
        Page currentPage = getPage();
        // clear selected text.
        if (currentPage.isInitiated()) {
            try {
                if (currentPage.getViewText() != null) {
                    currentPage.getViewText().clearSelected();
                }
            } catch (InterruptedException e) {
                logger.finer("Text selection clear interrupted");
            }
        }
    }

    /**
     * Sets the text that is contained in the specified rectangle and the
     * given mouse pointer.  The cursor and selection rectangle must be in
     * page space.
     *
     * @param cursorLocation location of cursor or mouse.
     * @param selection      rectangle of text to include in selection.
     */
    public void setSelectionRectangle(Point cursorLocation, Rectangle selection) {

    }

    /**
     * Clear any internal data structures that represent selected text and
     * repaint the component.
     */
    public void clearSelectionRectangle() {

    }

    public void reinitialize() {
        Page currentPage = getPage();
        currentPage.resetInitializedState();
        pageBufferStore.setDirty(true);
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public Page getPage() {
        return pageTree.getPage(pageIndex);
    }

    public void setDocumentViewCallback(DocumentView parentDocumentView) {
        this.parentDocumentView = parentDocumentView;
        documentViewController = this.parentDocumentView.getParentViewController();
    }

    public DocumentView getParentDocumentView() {
        return parentDocumentView;
    }

    public static boolean isAnnotationTool(final int displayTool) {
        return displayTool == DocumentViewModel.DISPLAY_TOOL_SELECTION ||
                displayTool == DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION ||
                displayTool == DocumentViewModel.DISPLAY_TOOL_HIGHLIGHT_ANNOTATION ||
                displayTool == DocumentViewModel.DISPLAY_TOOL_SQUIGGLY_ANNOTATION ||
                displayTool == DocumentViewModel.DISPLAY_TOOL_STRIKEOUT_ANNOTATION ||
                displayTool == DocumentViewModel.DISPLAY_TOOL_UNDERLINE_ANNOTATION;
    }

    /**
     * Called from parent controls when a UI control has manipulated the view, property
     * change is picked up and the view is updated accordingly. Responds to
     * PropertyConstants.DOCUMENT_VIEW_ROTATION_CHANGE and
     * PropertyConstants.DOCUMENT_VIEW_ZOOM_CHANGE.  If the worker is currently working
     * is cancel with interrupts.
     *
     * @param propertyConstant document view change property.
     * @param oldValue         old value
     * @param newValue         new value
     */
    public void updateView(String propertyConstant, Object oldValue, Object newValue) {
        if (pageImageCaptureTask != null && !pageImageCaptureTask.isDone()) {
            pageImageCaptureTask.cancel(true);
        }
        if (PropertyConstants.DOCUMENT_VIEW_ROTATION_CHANGE.equals(propertyConstant)) {
            pageRotation = (Float) newValue;
        } else if (PropertyConstants.DOCUMENT_VIEW_ZOOM_CHANGE.equals(propertyConstant)) {
            pageZoom = (Float) newValue;
        } else if (PropertyConstants.DOCUMENT_VIEW_REFRESH_CHANGE.equals(propertyConstant)) {
            // nothing to do but repaint
        }
        calculatePageSize(pageSize, pageRotation, pageZoom);
        pageBufferStore.setDirty(true);
    }

    /**
     * Checks if this page intersects the viewport. Called from the worker thread, where the view
     * may be tearing down, so a missing model or scroll pane is treated as "not visible".
     *
     * @return true if page is visible in viewport, false otherwise (including when the model or
     * scroll pane is unavailable).
     */
    /**
     * Releases the strong buffer pin when this page is not intersecting the viewport,
     * letting an off-screen page's back buffer be GC-reclaimed (it stays reachable via
     * the soft fallback until then).  On-screen pages keep their pin so GC cannot yank
     * the buffer mid-render and trigger a re-capture/re-decode storm.  Invoked by the
     * document view on scroll and by the capture task when a page tears down.
     */
    public void releaseBufferPinIfOffscreen() {
        if (!isPageIntersectViewport()) {
            pageBufferStore.releasePin();
        }
    }

    private boolean isPageIntersectViewport() {
        if (documentViewModel == null) {
            return false;
        }
        Rectangle pageBounds = documentViewModel.getPageComponents() != null ?
                documentViewModel.getPageBounds(pageIndex) : getBounds();
        JScrollPane parentScrollPane = documentViewModel.getDocumentViewScrollPane();
        return pageBounds != null && parentScrollPane != null && this.isShowing() &&
                pageBounds.intersects(parentScrollPane.getViewport().getViewRect());
    }

    /**
     * Calculates the page size for the rotation and zoom.  The new values are assigned to the pageSize.
     *
     * @param pageSize rectangle to update,  new rectangle will not be created.
     * @param rotation rotation of page.
     * @param zoom     zoom of page
     */
    protected void calculatePageSize(Rectangle pageSize, float rotation, float zoom) {
        if (pageTree != null) {
            Page currentPage = pageTree.getPage(pageIndex);
            if (currentPage != null) {
                pageSize.setSize(currentPage.getSize(pageBoundaryBox,
                        rotation, zoom).toDimension());
            }
        }
    }

    protected static double calculateScaleForDefaultScreen() {
        try {
            return GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice() // could be an issue if multiple screens
                    .getDefaultConfiguration()
                    .getDefaultTransform()
                    .getScaleX();
        } catch (Exception e) {
            return 1.0;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        // create a copy, so we can set our own state without affecting the parent graphics content.
        Graphics2D g2d = (Graphics2D) g.create(0, 0, pageSize.width, pageSize.height);
        GraphicsRenderingHints grh = GraphicsRenderingHints.getDefault();
        g2d.setRenderingHints(grh.getRenderingHints(GraphicsRenderingHints.SCREEN));
        // page location in the entire view.
        calculateBufferLocation();

        // paint the paper
        g2d.setColor(pageColor);
        g2d.fillRect(0, 0, pageSize.width, pageSize.height);

        // paint the back buffer, but get the latest copy in case it was returned extra quick.
        // read the buffer reference, location, zoom and rotation as one consistent snapshot so a
        // concurrent worker swap can't pair a new buffer with a stale location/zoom.
        PageBufferStore.Snapshot snapshot = pageBufferStore.getSnapshot();
        BufferedImage pageImage = snapshot.image;
        if (pageImage != null) {
            Rectangle paintingClip = snapshot.imageLocation;
            // If the buffer was captured at a different zoom/rotation, scale/rotate it
            // so it fills the page smoothly until the fresh capture lands.  The stale
            // buffer is flagged dirty in calculateBufferLocation (which then submits a
            // new capture); we deliberately do NOT setDirty()+repaint() here.  Doing so
            // spun a tight EDT loop while the (slow) capture was still in flight -- the
            // capture-completion repaint (see PageImageCaptureTask.call) is what drives
            // convergence, exactly once, when the new buffer is actually ready.
            if (pageZoom != snapshot.pageZoom ||
                    pageRotation != snapshot.pageRotation) {
                g2d.transform(calculateBufferAffineTransform(snapshot));
            }
            // will scale buffer to fit the current clip with smooths out any artifacts from screen scale factor
            g2d.drawImage(pageImage, paintingClip.x, paintingClip.y, paintingClip.width, paintingClip.height, null);
        }
        g2d.dispose();
    }

    /**
     * Calculates where we should be painting the new buffer and kicks off the worker if the buffer
     * is deemed dirty. The Parent scrollpane viewport is taken into account to set up the clipping.
     */
    protected void calculateBufferLocation() {

        JScrollPane parentScrollPane = documentViewModel.getDocumentViewScrollPane();
        // grab a reference to the graphics configuration via the AWT thread,  if we get it on the worker thread
        // it sometimes return null.
        graphicsConfiguration = parentScrollPane.getGraphicsConfiguration();

        // update page size as we may have a page that's larger than the average document size.
        calculatePageSize(pageSize, pageRotation, pageZoom);

        // page location in the entire view.
        Rectangle pageLocation = documentViewModel != null ?
                documentViewModel.getPageBounds(pageIndex) : new Rectangle(pageSize);
        Rectangle viewPort = parentScrollPane.getViewport().getViewRect();
        Rectangle imageLocation;
        Rectangle imageClipLocation;
        if (pageLocation.width < viewPort.width && pageLocation.height < viewPort.height) {
            // Only buffer the whole page when it fits the viewport in BOTH dimensions.
            // With '||' a page narrower than the viewport but taller than it (a portrait
            // page zoomed in, scrolling vertically) took this branch and allocated a
            // full-page buffer sized to the zoomed page height -- hundreds of MB at high
            // zoom, retained per page via the SoftReference store.  '&&' keeps such pages
            // on the viewport-clipped branch below so the buffer stays viewport-bounded.
            imageLocation = new Rectangle(0, 0, pageLocation.width, pageLocation.height);
            imageClipLocation = new Rectangle(imageLocation);
        } else {
            // otherwise we create a buffer based on the viewport size plus some padding
            imageClipLocation = viewPort.intersection(pageLocation);
            // move the clip relative to page coordinates
            imageClipLocation.setLocation(
                    imageClipLocation.x - pageLocation.x, imageClipLocation.y - pageLocation.y);
            // we want the image to be a bit bigger to make scrolling look a little smoother.
            imageLocation = new Rectangle(imageClipLocation.x - pageBufferPadding,
                    imageClipLocation.y - pageBufferPadding,
                    imageClipLocation.width + pageBufferPadding * 2,
                    imageClipLocation.height + pageBufferPadding * 2);
            // we're using the AWT thread to check for scroll repaints,
            if (pageImageCaptureTask != null && pageBufferStore.getImageLocation() != null) {
                Rectangle imageAbsoluteLocation = new Rectangle(pageBufferStore.getImageLocation());
                imageAbsoluteLocation.setLocation(imageAbsoluteLocation.x + pageLocation.x,
                        imageAbsoluteLocation.y + pageLocation.y);
                if (!imageAbsoluteLocation.contains(viewPort.intersection(pageLocation))) {
                    pageBufferStore.setDirty(true);
                }
            }
        }

        // A buffer captured at a different zoom/rotation than the live view is stale
        // and needs a fresh capture.  Detecting it here (rather than via a setDirty()
        // +repaint() in paintComponent) means the capture is submitted once and the
        // capture-completion repaint drives convergence, instead of a busy EDT loop.
        if (pageBufferStore.getImageReference() != null &&
                (pageZoom != pageBufferStore.getPageZoom() ||
                        pageRotation != pageBufferStore.getPageRotation())) {
            pageBufferStore.setDirty(true);
        }

        // check if we need to create or refresh the back buffer.
        if (pageBufferStore.isDirty() || pageBufferStore.getImageReference() == null) {
            // start future task to paint the back buffer
            if (pageImageCaptureTask == null || pageImageCaptureTask.isDone() || pageImageCaptureTask.isCancelled()) {
                pageImageCaptureTask = new FutureTask<>(
                        new PageImageCaptureTask(this, imageLocation, imageClipLocation,
                                pageZoom,
                                pageRotation));
                Library.execute(pageImageCaptureTask);
            }
        }
    }

    /**
     * Calculates the affine transform that paints the old buffered image using the current scale and rotation.  This
     * avoid the back buffer flicker.  Once the worker captures the new buffer we swap in the new buffer.
     * todo, still needs some work with regards to rotation of the buffer.
     *
     * @return transform needed to paint the previous out of sync buffer in the correct place.
     */
    private AffineTransform calculateBufferAffineTransform(PageBufferStore.Snapshot snapshot) {
        AffineTransform at = new AffineTransform();
        if (pageZoom != snapshot.pageZoom) {
            double pageScale = pageZoom / (double) snapshot.pageZoom;
            at.scale(pageScale, pageScale);
        }
        // get the page size of the currently painted image we are trying to scale or rotate.
        if (pageRotation != snapshot.pageRotation) {
            double rotation;
            rotation = snapshot.pageRotation - pageRotation;
            if (rotation < 0) {
                rotation += 360;
            }
            Rectangle imageLocation = snapshot.pageSize;
            if (rotation == 90) {
                at.translate(imageLocation.height, 0);
            } else if (rotation == 180) {
                at.translate(imageLocation.width, 0);
            } else if (rotation == 270) {
                at.translate(imageLocation.height, -imageLocation.width);
            }
            double theta = rotation * Math.PI / 180.0;
            at.rotate(theta);
        }
        return at;
    }

    /**
     * The worker of any successful page paint.  The worker takes a snapshot of the given page state
     * and paint the desired image to buffer.  One completed the new buffer is stuffed into
     * the pageBufferStore instance with properties so that it can be painted in the correct thread
     * when the component is repainted.
     */
    public class PageImageCaptureTask implements Callable<Object>, PaintPageListener {

        private final float zoom;
        private final float rotation;
        private final Rectangle imageLocation;
        private final Rectangle imageClipLocation;
        // defensive copy of the page size; the outer pageSize field is mutated in place on the EDT
        // by calculatePageSize(), so capture it here to avoid a torn read on the worker thread.
        private final Rectangle pageSize;
        private final JComponent parent;

        public PageImageCaptureTask(JComponent parent, Rectangle imageLocation, Rectangle imageClipLocation,
                                    float zoom, float rotation) {
            this.zoom = zoom;
            this.rotation = rotation;
            this.parent = parent;
            this.imageLocation = imageLocation;
            this.imageClipLocation = imageClipLocation;
            this.pageSize = new Rectangle(AbstractPageViewComponent.this.pageSize);
        }

        public Object call() {
            if (!isPageIntersectViewport()) {
                // page teardown when out of view; drop the strong buffer pin so the
                // off-screen buffer can be reclaimed.
                pageBufferStore.releasePin();
                pageTeardownCallback();
                return null;
            }
            // paint page.
            Page page = pageTree.getPage(pageIndex);
            // page loading progress
            PageViewLoadingListener pageLoadingListener = new DefaultPageViewLoadingListener(parent,
                    documentViewController);
            boolean isFirstProgressivePaint = false;
            try {
                // be careful that the document hasn't been closed on awt thread.
                if (documentViewController != null && documentViewController.getDocumentViewModel() == null)
                    return null;
                if (documentViewController != null) page.addPageProcessingListener(pageLoadingListener);
                // page init, interruptible
                page.init();
                pageInitializedCallback(page);

                double scale = AbstractPageViewComponent.calculateScaleForDefaultScreen();
                BufferedImage pageBufferImage = graphicsConfiguration.createCompatibleImage(
                        (int) (imageLocation.width * scale),
                        (int) (imageLocation.height * scale),
                        BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = pageBufferImage.createGraphics();
                g2d.scale(scale, scale);


                // if we don't have a soft reference then we are likely on a first clean paint at which
                // point we can kick off the animated paint.
                if (progressivePaint && pageBufferStore.getImageReference() == null) {
                    page.addPaintPageListener(this);
                    isFirstProgressivePaint = true;
                    pageBufferStore.setState(pageBufferImage, imageLocation, imageClipLocation, pageSize,
                            zoom, rotation, true);
                }
                g2d.setClip(0, 0, imageLocation.width, imageLocation.height);
                g2d.translate(-imageLocation.x, -imageLocation.y);
                // paint page interruptable
                page.paint(g2d, GraphicsRenderingHints.SCREEN, pageBoundaryBox, rotation, zoom,
                        paintAnnotations, paintSearchHighlight);
                g2d.dispose();
                // init and paint thread were not interrupted, we can move the back buffer to the front.
                pageBufferStore.setState(pageBufferImage, imageLocation, imageClipLocation, pageSize,
                        zoom, rotation, false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.finer("Interrupted page capture task: " + e.getMessage() + " " + pageIndex);
                // flush the buffer if this is our first paint.
                if (isFirstProgressivePaint) pageBufferStore.setImageReference(null);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during page capture task: " + e.getMessage() + " " + pageIndex, e);
                // avoid a repaint as we'll likely get caught in an infinite loop.
            } finally {
                page.removePaintPageListener(this);
                page.removePageProcessingListener(pageLoadingListener);
            }
            // queue a repaint, regardless of outcome
            SwingUtilities.invokeLater(AbstractPageViewComponent.this::repaint);

            return null;
        }

        public void paintPage(PaintPageEvent event) {
            SwingUtilities.invokeLater(AbstractPageViewComponent.this::repaint);
        }
    }

    // Maximum strongly-pinned page buffers PER DOCUMENT.  A pin keeps a page's live
    // buffer from being GC'd mid-render (which would trigger a re-capture); the
    // per-document budget bounds total pinned memory even when a page leaves the
    // viewport WITHOUT the release path firing (e.g. a window resize or view-mode
    // change raises no scrollbar AdjustmentEvent, so releaseBufferPinIfOffscreen is
    // never called).  Default 6 (~a screen of pages plus neighbours).
    private static final int MAX_PINNED_BUFFERS =
            Math.max(1, Defs.intProperty("org.icepdf.core.views.maxPinnedPageBuffers", 6));

    // One PinnedBufferBudget per open document, keyed weakly by its view model so a
    // closed document's budget is released with it.  Per-document (not a single
    // JVM-wide pool) so two documents in two viewers don't evict each other's pins.
    private static final java.util.Map<DocumentViewModel, PinnedBufferBudget> PIN_BUDGETS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    // Fallback for a null view model (headless / detached page): its own isolated
    // budget so it can't be starved by, or starve, real documents.
    private static final PinnedBufferBudget NO_MODEL_PIN_BUDGET = new PinnedBufferBudget();

    private static PinnedBufferBudget budgetFor(DocumentViewModel model) {
        if (model == null) {
            return NO_MODEL_PIN_BUDGET;
        }
        synchronized (PIN_BUDGETS) {
            return PIN_BUDGETS.computeIfAbsent(model, m -> new PinnedBufferBudget());
        }
    }

    /**
     * An access-ordered LRU of pinned {@link PageBufferStore}s for a single document.
     * When a store pins a buffer beyond {@link #MAX_PINNED_BUFFERS}, the eldest
     * (least-recently captured) store's pin is released so its buffer falls back to
     * its {@link SoftReference} and becomes GC-eligible.  Actively rendered pages
     * re-capture (moving them to newest), so the visible pages stay pinned.
     */
    private static final class PinnedBufferBudget {
        private final java.util.LinkedHashMap<PageBufferStore, Boolean> lru =
                new java.util.LinkedHashMap<>(16, 0.75f, true);

        // Register store as most-recently-pinned; release any pin beyond the cap.
        // Evicted pins are released OUTSIDE the lock to avoid nested locking.
        void pin(PageBufferStore store) {
            java.util.List<PageBufferStore> evicted = null;
            synchronized (lru) {
                lru.put(store, Boolean.TRUE);
                java.util.Iterator<PageBufferStore> it = lru.keySet().iterator();
                while (lru.size() > MAX_PINNED_BUFFERS && it.hasNext()) {
                    PageBufferStore eldest = it.next();
                    if (eldest == store) {
                        continue; // never evict the buffer we just pinned
                    }
                    if (evicted == null) {
                        evicted = new java.util.ArrayList<>();
                    }
                    evicted.add(eldest);
                    it.remove();
                }
            }
            if (evicted != null) {
                for (PageBufferStore s : evicted) {
                    s.releasePin();
                }
            }
        }

        void drop(PageBufferStore store) {
            synchronized (lru) {
                lru.remove(store);
            }
        }
    }

    /**
     * Synchronized page buffer property store, insures that a page capture occurs using the correct properties.
     */
    protected static class PageBufferStore {

        // last page buffer store.  The buffer is held two ways: a strong "pin" while
        // the page is on-screen so GC cannot reclaim the buffer out from under the
        // render loop (which would make getImageReference() return null and trigger
        // an endless re-capture / re-decode storm), plus a SoftReference fallback so
        // an off-screen page whose pin has been released can still be GC-reclaimed
        // under memory pressure (pages are not disposed until the document closes).
        private BufferedImage pinnedImage;
        private SoftReference<BufferedImage> imageReference;
        // paint location if buffer is clipped to be smaller than the page size.
        private Rectangle imageLocation;
        // location of the current clip,  generally the viewport intersection with the page bounds.
        private Rectangle imageClipLocation;
        private float pageZoom, pageRotation;
        // page size at the given zoom and location.
        private Rectangle pageSize;
        // dirty flag.
        private boolean isDirty;

        private final Object objectLock = new Object();

        // The pin budget that caps how many of this DOCUMENT's page buffers stay
        // strongly pinned (see PinnedBufferBudget).  Scoped per view model so two
        // documents open in two viewers each keep their own visible buffers pinned
        // instead of evicting each other's from a single JVM-wide pool.
        private final PinnedBufferBudget pinBudget;

        PageBufferStore(PinnedBufferBudget pinBudget) {
            this.pinBudget = pinBudget;
            imageReference = new SoftReference<>(null);
        }

        // Track this store as most-recently-pinned (or drop it, for a null buffer)
        // within its document's budget, which releases any pin beyond the cap.
        private void registerPin(BufferedImage buffer) {
            if (buffer == null) {
                pinBudget.drop(this);
            } else {
                pinBudget.pin(this);
            }
        }

        /**
         * Immutable, consistent view of the buffer state captured under a single lock.  Painting reads
         * the image reference, its location, zoom and rotation together so a concurrent worker
         * {@link #setState} can't pair a new buffer with a stale location/zoom mid-paint.  Note this
         * snapshots the image <em>reference</em>, not the pixels, so progressive painting into the
         * shared buffer still shows through.
         */
        protected static final class Snapshot {
            final BufferedImage image;
            final Rectangle imageLocation;
            final Rectangle pageSize;
            final float pageZoom;
            final float pageRotation;

            Snapshot(BufferedImage image, Rectangle imageLocation, Rectangle pageSize,
                     float pageZoom, float pageRotation) {
                this.image = image;
                this.imageLocation = imageLocation;
                this.pageSize = pageSize;
                this.pageZoom = pageZoom;
                this.pageRotation = pageRotation;
            }
        }

        Snapshot getSnapshot() {
            synchronized (objectLock) {
                return new Snapshot(currentImage(), imageLocation, pageSize, pageZoom, pageRotation);
            }
        }

        void setState(BufferedImage pageBufferImage, Rectangle imageLocation, Rectangle imageClipLocation,
                      Rectangle pageSize, float pageZoom, float pageRotation, boolean isDirty) {
            synchronized (objectLock) {
                this.pinnedImage = pageBufferImage;
                this.imageReference = new SoftReference<>(pageBufferImage);
                this.imageLocation = imageLocation;
                this.imageClipLocation = imageClipLocation;
                this.pageSize = pageSize;
                this.pageZoom = pageZoom;
                this.pageRotation = pageRotation;
                this.isDirty = isDirty;
            }
            registerPin(pageBufferImage);
        }

        void setImageReference(BufferedImage bufferedImage) {
            synchronized (objectLock) {
                this.pinnedImage = bufferedImage;
                this.imageReference = new SoftReference<>(bufferedImage);
            }
            registerPin(bufferedImage);
        }

        public BufferedImage getImageReference() {
            synchronized (objectLock) {
                return currentImage();
            }
        }

        // Prefer the strong pin; fall back to the soft reference for an off-screen
        // page whose pin was released but whose buffer GC has not yet reclaimed.
        private BufferedImage currentImage() {
            if (pinnedImage != null) {
                return pinnedImage;
            }
            return imageReference != null ? imageReference.get() : null;
        }

        /**
         * Releases the strong pin (keeping the soft fallback) so an off-screen page's
         * buffer becomes eligible for GC.  Called when the page scrolls out of the
         * viewport.  A no-op re-pin happens automatically on the next capture.
         */
        void releasePin() {
            synchronized (objectLock) {
                this.pinnedImage = null;
            }
            pinBudget.drop(this);
        }

        Rectangle getImageLocation() {
            synchronized (objectLock) {
                return imageLocation;
            }
        }

        Rectangle getImageClipLocation() {
            synchronized (objectLock) {
                return imageClipLocation;
            }
        }

        Rectangle getPageSize() {
            synchronized (objectLock) {
                return pageSize;
            }
        }

        float getPageZoom() {
            synchronized (objectLock) {
                return pageZoom;
            }
        }

        float getPageRotation() {
            synchronized (objectLock) {
                return pageRotation;
            }
        }

        public boolean isDirty() {
            synchronized (objectLock) {
                return isDirty;
            }
        }

        public void setDirty(boolean dirty) {
            synchronized (objectLock) {
                this.isDirty = dirty;
            }
        }
    }

}
