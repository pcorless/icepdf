/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
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
import org.icepdf.core.pobjects.annotations.FreeTextAnnotation;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.core.util.*;
import org.icepdf.ri.common.tools.SelectionBoxHandler;
import org.icepdf.ri.common.tools.TextSelectionPageHandler;
import org.icepdf.ri.common.views.annotations.AbstractAnnotationComponent;
import org.icepdf.ri.common.views.annotations.FreeTextAnnotationComponent;
import org.icepdf.ri.common.views.annotations.PopupAnnotationComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>This class represents a single page view of a PDF document as a JComponent.
 * This component can be used in any swing application to display a PDF page.  The
 * default RI implementation comes with four predefined page views which use this
 * component.  If custom page views are need then the following class should
 * be referenced: </p>
 * <p/>
 * <p>This component assumes that white paper is the default and thus uses white
 * as the default background color for buffers and page painting if no color
 * is specified by the PDF.  This default colour can be changed using the
 * system property org.icepdf.core.views.page.paper.color.  This property
 * takes RRGGBB hex colours as values.  eg. black=000000 or white=FFFFFFF.</p>
 *
 * @see org.icepdf.ri.common.views.OneColumnPageView
 * @see org.icepdf.ri.common.views.OnePageView
 * @see org.icepdf.ri.common.views.TwoColumnPageView
 * @see org.icepdf.ri.common.views.TwoPageView
 * @see org.icepdf.ri.common.views.AbstractDocumentView
 * @see org.icepdf.ri.common.views.AbstractDocumentViewModel
 * @see DocumentViewController
 *      <p/>
 *      <p>The page view takes advantage of a buffered display to speed up page scrolling
 *      and provide users with a better overall UI experiance.
 *      The size of the buffer can also be set with the system properties
 *      "org.icepdf.core.views.buffersize.vertical" and
 *      "org.icepdf.core.views.buffersize.horizontal".  These system
 *      properties define the vertical and horizontal ratios in which the current
 *      viewport will be extended to define the buffer size.</p>
 * @since 2.5
 */
public class PageViewComponentImpl extends
        AbstractPageViewComponent
        implements PaintPageListener,
        FocusListener, ComponentListener {

    private static final Logger logger =
            Logger.getLogger(PageViewComponentImpl.class.toString());

    private static Color pageColor;

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

    }

    // turn off page image buffer proxy loading.
    private static boolean enablePageLoadingProxy =
            Defs.booleanProperty("org.icepdf.core.views.page.proxy", true);

    private PageTree pageTree;
    private JScrollPane parentScrollPane;
    private int previousScrollValue;
    private int pageIndex;

    private Rectangle pageSize = new Rectangle();
    // cached page size, we call this a lot.
    private Rectangle defaultPageSize = new Rectangle();

    private boolean isPageSizeCalculated = false;
    private float currentZoom;
    private float currentRotation;

    // the buffered image which will be painted to
    private SoftReference<Image> bufferedPageImageReference;
    // the bounds of the buffered image.
    private Rectangle bufferedPageImageBounds = new Rectangle();

    private Timer isDirtyTimer;
    private PageInitializer pageInitializer;
    private PagePainter pagePainter;
    private final Object paintCopyAreaLock = new Object();
    private boolean disposing = false;

    // current clip
    private Rectangle clipBounds;
    private Rectangle oldClipBounds;

    private boolean inited;

    // vertical scale factor to extend buffer
    private static double verticalScaleFactor;
    // horizontal  scale factor to extend buffer
    private static double horizontalScaleFactor;

    // dirty refresh timer call interval
    private static int dirtyTimerInterval = 5;

    private static int scrollInitThreshold = 150;

    // graphics configuration
    private static GraphicsConfiguration gc;

    static {
        // default value have been assigned.  Keep in mind that larger ratios will
        // result in more memory usage.
        try {
            verticalScaleFactor =
                    Double.parseDouble(Defs.sysProperty("org.icepdf.core.views.buffersize.vertical",
                            "1.015"));

            horizontalScaleFactor =
                    Double.parseDouble(Defs.sysProperty("org.icepdf.core.views.buffersize.horizontal",
                            "1.015"));
        } catch (NumberFormatException e) {
            logger.warning("Error reading buffered scale factor");
        }
        try {
            dirtyTimerInterval =
                    Defs.intProperty("org.icepdf.core.views.dirtytimer.interval",
                            5);
        } catch (NumberFormatException e) {
            logger.log(Level.FINE, "Error reading dirty timer interval");
        }
        try {
            scrollInitThreshold =
                    Defs.intProperty("org.icepdf.core.views.scroll.initThreshold",
                            150);
        } catch (NumberFormatException e) {
            logger.log(Level.FINE, "Error reading dirty timer interval");
        }
    }


    public PageViewComponentImpl(DocumentViewModel documentViewModel,
                                 PageTree pageTree, int pageNumber,
                                 JScrollPane parentScrollPane) {
        this(documentViewModel, pageTree, pageNumber, parentScrollPane, 0, 0);
    }

    public PageViewComponentImpl(DocumentViewModel documentViewModel,
                                 PageTree pageTree, int pageNumber,
                                 JScrollPane parentScrollPane,
                                 int width, int height) {
        // removed focasable until we can build our own focus manager
        // for moving though a large number of pages.
        setFocusable(true);
        // add focus listener
        addFocusListener(this);

        // needed to propagate mouse events.
        this.documentViewModel = documentViewModel;
        this.parentScrollPane = parentScrollPane;

        currentRotation = documentViewModel.getViewRotation();
        currentZoom = documentViewModel.getViewRotation();

        this.pageTree = pageTree;
        this.pageIndex = pageNumber;

        clipBounds = new Rectangle();
        oldClipBounds = new Rectangle();

        bufferedPageImageReference = new SoftReference<Image>(null);

        // initialize page size
        if (width == 0 && height == 0) {
            calculatePageSize(pageSize);
            isPageSizeCalculated = true;
        } else {
            pageSize.setSize(width, height);
            defaultPageSize.setSize(width, height);
        }
    }

    /**
     * Adds the specified annotation to this page instance.  The annotation
     * is wrapped with a AnnotationComponent and added to this components layout
     * manager.
     *
     * @param annotation annotation to add to this page instance. .
     */
    public void addAnnotation(AnnotationComponent annotation) {
        // delegate to handler.
        if (annotationComponents == null) {
            annotationComponents = new ArrayList<AnnotationComponent>();
        }
        annotationComponents.add(annotation);
        if (annotation instanceof PopupAnnotationComponent) {
            this.add((AbstractAnnotationComponent) annotation, JLayeredPane.POPUP_LAYER);
        } else {
            this.add((AbstractAnnotationComponent) annotation, JLayeredPane.DEFAULT_LAYER);
        }
    }

    /**
     * Removes the specified annotation from this page component
     *
     * @param annotationComp annotation to be removed.
     */
    public void removeAnnotation(AnnotationComponent annotationComp) {
        annotationComponents.remove(annotationComp);
        this.remove((AbstractAnnotationComponent) annotationComp);
    }

    public void init() {
        if (inited) {
            return;
        }
        inited = true;

        // add repaint listener
        addPageRepaintListener();

        // timer will dictate when buffer repaints can take place
        DirtyTimerAction dirtyTimerAction = new DirtyTimerAction(this);
        isDirtyTimer = new Timer(dirtyTimerInterval, dirtyTimerAction);
        isDirtyTimer.setInitialDelay(0);

        // PageInitializer and painter commands
        pageInitializer = new PageInitializer(this);
        pagePainter = new PagePainter();
    }

    /**
     * Should only be called by the demo application as it triggers the
     * current page state to be marked as uninitialized and will trigger a
     * full parse of the page if accessed again.
     */
    public void invalidatePage() {
        Page page = pageTree.getPage(pageIndex);
        page.getLibrary().disposeFontResources();
        page.resetInitializedState();
        currentZoom = -1;
        pagePainter.setIsBufferDirty(true);
    }

    public void invalidatePageBuffer() {
        currentZoom = -1;
    }

    public void dispose() {

        disposing = true;
        if (isDirtyTimer != null)
            isDirtyTimer.stop();

        removeComponentListener(this);

        // remove annotation listeners.
        removeMouseMotionListener(currentToolHandler);
        removeMouseListener(currentToolHandler);

        // remove focus listener
        removeFocusListener(this);

        // remove repaint listener
        removePageRepaintListener();

        if (bufferedPageImageReference != null) {
            Image pageBufferImage = bufferedPageImageReference.get();
            if (pageBufferImage != null) {
                pageBufferImage.flush();
            }
        }

        // dispose annotations components
        if (annotationComponents != null) {
            for (int i = 0, max = annotationComponents.size(); i < max; i++) {
                annotationComponents.get(i).dispose();
            }
        }

        inited = false;
    }

    public Page getPage() {
        return pageTree.getPage(pageIndex);
    }

    public void setDocumentViewCallback(DocumentView parentDocumentView) {
        this.parentDocumentView = parentDocumentView;
        documentViewController = this.parentDocumentView.getParentViewController();
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public Dimension getPreferredSize() {
        return pageSize.getSize();
    }

    public void invalidate() {
        calculateRoughPageSize(pageSize);
        if (pagePainter != null) {
            pagePainter.setIsBufferDirty(true);
        }
        super.invalidate();
    }

    public void paintComponent(Graphics gg) {
        if (!inited) {
            init();
        }

        // make sure the initiate the pages size
        if (!isPageSizeCalculated) {
            calculatePageSize(pageSize);
            pagePainter.setIsBufferDirty(true);
        } else if (isPageStateDirty()) {
            calculatePageSize(pageSize);
        }

        Graphics2D g = (Graphics2D) gg.create(0, 0, pageSize.width, pageSize.height);

        g.setColor(pageColor);
        g.fillRect(0, 0, pageSize.width, pageSize.height);

        // multi thread page load
        if (enablePageLoadingProxy && isPageIntersectViewport() && !isDirtyTimer.isRunning()) {
            isDirtyTimer.start();
        }
        // single threaded load of page content on awt thread (no flicker)
        else if (!enablePageLoadingProxy && isPageIntersectViewport() &&
                (isPageStateDirty() || isBufferDirty())) {
            pageInitializer.run();
            pagePainter.run();
        }

        // Update clip data
        // first choice is to use the parent view port but the clip will do otherwise
        if (parentScrollPane == null) {
            oldClipBounds.setBounds(clipBounds);
            clipBounds.setBounds(g.getClipBounds());
            if (oldClipBounds.width == 0 && oldClipBounds.height == 0) {
                oldClipBounds.setBounds(clipBounds);
            }
        }

        if (bufferedPageImageReference != null) {
            Image pageBufferImage = bufferedPageImageReference.get();
            // draw the clean buffer
            if (pageBufferImage != null && !isPageStateDirty()) {
                // block, if copy area is being done in painter thread
                synchronized (paintCopyAreaLock) {
                    g.drawImage(pageBufferImage, bufferedPageImageBounds.x,
                            bufferedPageImageBounds.y, this);
                }
//                if (!isPageRepaintListenerEnabled && !pagePainter.isRunning()){
//                    addPageRepaintListener();
//                }

//                g.setColor(Color.blue);
//                g.drawRect(oldBufferedPageImageBounds.x + 1,
//                           oldBufferedPageImageBounds.y + 1,
//                           oldBufferedPageImageBounds.width - 3,
//                           oldBufferedPageImageBounds.height- 3);
//                g.setColor(Color.red);
//                g.drawRect(bufferedPageImageBounds.x + 1,
//                           bufferedPageImageBounds.y + 1,
//                           bufferedPageImageBounds.width - 2,
//                           bufferedPageImageBounds.height- 2);

            }
            // no pageBuffer to paint thus, we must recreate it.
            else {
                // mark as dirty
                currentZoom = -1;
                if (!isDirtyTimer.isRunning()) {
                    isDirtyTimer.start();
                }
            }

            // paint the annotations components
            paintAnnotations(g);

            // Lazy paint of highlight and select all text states.
            Page currentPage = this.getPage();
            if (currentPage != null && currentPage.isInitiated()) {
                PageText pageText = currentPage.getViewText();

                // paint any highlighted words
                DocumentSearchController searchController =
                        documentViewController.getParentController()
                                .getDocumentSearchController();
                if (searchController.isSearchHighlightRefreshNeeded(pageIndex, pageText)) {
                    searchController.searchHighlightPage(pageIndex);
                }
                // if select all we'll want to paint the selected text.
                if (documentViewModel.isSelectAll()) {
                    documentViewModel.addSelectedPageText(this);
                    pageText.selectAll();
                }
                // paint selected text.
                TextSelectionPageHandler.paintSelectedText(g, this, documentViewModel);
            }
            // paint annotation handler effect if any.
            if (currentToolHandler != null) {
                currentToolHandler.paintTool(g);
            }
        }
    }

    private void paintAnnotations(Graphics g) {
        Page currentPage = getPage();
        if (currentPage != null && currentPage.isInitiated()) {
            if (annotationComponents != null) {
                Graphics2D gg2 = (Graphics2D) g;
                // save draw state.
                AffineTransform prePaintTransform = gg2.getTransform();
                Color oldColor = gg2.getColor();
                Stroke oldStroke = gg2.getStroke();
                // apply page transform.
                AffineTransform at = currentPage.getPageTransform(
                        documentViewModel.getPageBoundary(),
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom());
                gg2.transform(at);
                // get current tool state, we don't want to draw the highlight
                // state if the selection tool is selected.
                boolean notSelectTool =
                        documentViewModel.getViewToolMode() !=
                                DocumentViewModel.DISPLAY_TOOL_SELECTION;

                // paint all annotations on top of the content buffer
                AnnotationComponent annotation;
                for (int i = 0, max = annotationComponents.size(); i < max; i++) {
                    annotation = annotationComponents.get(i);
                    if (((Component) annotation).isVisible() &&
                            !(annotation.getAnnotation() instanceof FreeTextAnnotation
                                    && ((FreeTextAnnotationComponent) annotation).isActive())) {
                        annotation.getAnnotation().render(gg2,
                                GraphicsRenderingHints.SCREEN,
                                documentViewModel.getViewRotation(),
                                documentViewModel.getViewZoom(),
                                annotation.hasFocus() && notSelectTool);
                    }
                }
                // post paint clean up.
                gg2.setColor(oldColor);
                gg2.setStroke(oldStroke);
                gg2.setTransform(prePaintTransform);
            }
        }
    }

    public void clearSelectedText() {
        // on mouse click clear the currently selected sprints
        Page currentPage = getPage();
        // clear selected text.
        if (currentPage.getViewText() != null) {
            currentPage.getViewText().clearSelected();
        }

    }

    public void setSelectionRectangle(Point cursorLocation, Rectangle selection) {
        if (currentToolHandler instanceof SelectionBoxHandler) {
            ((SelectionBoxHandler) currentToolHandler).setSelectionRectangle(
                    cursorLocation, selection);
        }
    }

    public void clearSelectionRectangle() {
        if (currentToolHandler instanceof SelectionBoxHandler) {
            ((SelectionBoxHandler) currentToolHandler).clearRectangle(this);
        }
    }

    public void focusGained(FocusEvent e) {
        int oldCurrentPage = documentViewModel.getViewCurrentPageIndex();
        documentViewModel.setViewCurrentPageIndex(pageIndex);
        documentViewController.firePropertyChange(PropertyConstants.DOCUMENT_CURRENT_PAGE,
                oldCurrentPage,
                pageIndex);
    }

    public void focusLost(FocusEvent e) {

    }

    public void componentHidden(ComponentEvent e) {
    }

    public void componentMoved(ComponentEvent e) {
    }

    public void componentResized(ComponentEvent e) {
    }

    public void componentShown(ComponentEvent e) {
    }

    private void calculateRoughPageSize(Rectangle pageSize) {
        // use a ratio to figure out what the dimension should be a after
        // a specific scale.

        float width = defaultPageSize.width;
        float height = defaultPageSize.height;
        float totalRotation = documentViewModel.getViewRotation();

        if (totalRotation == 0 || totalRotation == 180) {
            // Do nothing
        }
        // Rotated sideways
        else if (totalRotation == 90 || totalRotation == 270) {
            float temp = width;
            // width equals hight is ok in this case
            width = height;
            height = temp;
        }
        // Arbitrary rotation
        else {
            AffineTransform at = new AffineTransform();
            double radians = Math.toRadians(totalRotation);
            at.rotate(radians);
            Rectangle2D.Double boundingBox = new Rectangle2D.Double(0.0, 0.0, 0.0, 0.0);
            Point2D.Double src = new Point2D.Double();
            Point2D.Double dst = new Point2D.Double();
            src.setLocation(0.0, height);    // Top left
            at.transform(src, dst);
            boundingBox.add(dst);
            src.setLocation(width, height);  // Top right
            at.transform(src, dst);
            boundingBox.add(dst);
            src.setLocation(0.0, 0.0);       // Bottom left
            at.transform(src, dst);
            boundingBox.add(dst);
            src.setLocation(width, 0.0);     // Bottom right
            at.transform(src, dst);
            boundingBox.add(dst);
            width = (float) boundingBox.getWidth();
            height = (float) boundingBox.getHeight();
        }
        pageSize.setSize((int) (width * documentViewModel.getViewZoom()),
                (int) (height * documentViewModel.getViewZoom()));
    }

    private void calculatePageSize(Rectangle pageSize) {
        if (pageTree != null) {
            Page currentPage = pageTree.getPage(pageIndex);
            if (currentPage != null) {
                pageSize.setSize(currentPage.getSize(
                        documentViewModel.getPageBoundary(),
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom()).toDimension());

                defaultPageSize.setSize(currentPage.getSize(
                        documentViewModel.getPageBoundary(),
                        0,
                        1).toDimension());
            }
            isPageSizeCalculated = true;
        }
    }

    private boolean isBufferDirty() {
        if (disposing)
            return false;

        // if the page size is smaller then the view port then we will always be clean
        // as this is the policy defined when creating the buffer.
        if (pageSize.height <= clipBounds.height &&
                pageSize.width <= clipBounds.width) {
            return false;
        }

        Rectangle tempClipBounds = new Rectangle(clipBounds);
        if (parentScrollPane != null) {
            tempClipBounds.setBounds(parentScrollPane.getViewport().getViewRect());
        }

        // get the pages current bounds, may have changed sin
        Rectangle pageBounds = documentViewModel.getPageBounds(pageIndex);

        // adjust the buffered Page bounds to component space
        Rectangle normalizedBounds = new Rectangle(bufferedPageImageBounds);
        normalizedBounds.x += pageBounds.x;
        normalizedBounds.y += pageBounds.y;

        // if the normalized bounds are not contained in the intersection of
        // pageBounds and the clipBound then we have dirty buffer, that is, empty
        // white space is visible where page content should be.
        return !normalizedBounds.contains(pageBounds.intersection(tempClipBounds));
    }

    /**
     * Utility method for setting up the buffered image painting. Take care
     * of all the needed transformation.
     *
     * @param pagePainter painter doing the painting work.
     */
    private synchronized void createBufferedPageImage(Page page, PagePainter pagePainter) {
        if (disposing)
            return;

        boolean isPageStateDirty = isPageStateDirty();
        // Mark the image size as being fixed, we don't want the timer to
        // accidentally stop the painting
        currentRotation = documentViewModel.getViewRotation();
        currentZoom = documentViewModel.getViewZoom();
        // clear the image if it is dirty, we don't want to paint the wrong size buffer
        Image pageBufferImage = bufferedPageImageReference.get();
        // draw the clean buffer
        if (isPageStateDirty && pageBufferImage != null) {
            pageBufferImage.flush();
        }

        // update buffer states, before we change bufferedPageImageBounds
        Rectangle oldBufferedPageImageBounds = new Rectangle(bufferedPageImageBounds);

        // get the pages current bounds, may have changed sin
        Rectangle pageBounds = documentViewModel.getPageBounds(pageIndex);

        if (parentScrollPane != null) {
            oldClipBounds.setBounds(clipBounds);
            clipBounds.setBounds(parentScrollPane.getViewport().getViewRect());
            if (oldClipBounds.width == 0 && oldClipBounds.height == 0) {
                oldClipBounds.setBounds(clipBounds);
            }
        }

        // calculate the intersection of the clipBounds with the page size.
        // This will give us the basis for most of our calculations
        bufferedPageImageBounds.setBounds(pageBounds.intersection(clipBounds));

        // calculate the size of the buffers width, if the page is smaller then
        // the clip bounds, then we use the page size as the buffer size.
        if (pageSize.width <= clipBounds.width) {
            bufferedPageImageBounds.x = 0;
            bufferedPageImageBounds.width = pageSize.width;
        }
        // otherwise we use the size of the clipBounds * horizontal scale factor
        else {
            if (horizontalScaleFactor > 1.0) {
                double width = ((clipBounds.width * horizontalScaleFactor) / 2.0);
                bufferedPageImageBounds.x = (int) (clipBounds.x - width);
                bufferedPageImageBounds.width = (int) (clipBounds.width + (width * 2.0));
            }
            // we want clip bounds width
            else {
                bufferedPageImageBounds.width = clipBounds.width;
            }
            // but we need to normalize the x coordinate to component space
            bufferedPageImageBounds.x -= pageBounds.x;
        }
        // calculate the size of the buffers height
        if (pageSize.height <= clipBounds.height) {
            bufferedPageImageBounds.y = 0;
            bufferedPageImageBounds.height = clipBounds.height;
        }
        // otherwise we use the size of the clipBounds * horizontal scale factor
        else {

            if (verticalScaleFactor > 1.0) {
                double height = ((clipBounds.height * verticalScaleFactor) / 2.0);
                bufferedPageImageBounds.y = (int) (clipBounds.y - height);
                bufferedPageImageBounds.height = (int) (clipBounds.height + (height * 2.0));
            }
            // we want clip bounds height
            else {
                bufferedPageImageBounds.height = clipBounds.height;
            }
            // but we need to normalize the y coordinate to component space
            bufferedPageImageBounds.y -= pageBounds.y;
        }

        // clean up old image if available, this is done before we correct the bounds,
        // keeps the same buffer size for the zoom/rotation, but manipulate its bounds
        // to avoid creating a series of new buffers and thus more flicker
        // Boolean isBufferDirty = isBufferDirty();
        // draw the clean buffer
        if (isPageStateDirty || pageBufferImage == null) {
            // clear old buffer
            if (pageBufferImage != null) {
                pageBufferImage.flush();
            }

            // create new image and get graphics context from image
            if (gc == null) {
                gc = getGraphicsConfiguration();
            }
            if (gc != null && this.isShowing()) {
                // get the optimal image for the platform
                pageBufferImage = gc.createCompatibleImage(
                        bufferedPageImageBounds.width,
                        bufferedPageImageBounds.height);
                // paint white, try to avoid black flicker
                Graphics g = pageBufferImage.getGraphics();
                g.setColor(pageColor);
                g.fillRect(0, 0, pageSize.width, pageSize.height);
            }

            bufferedPageImageReference =
                    new SoftReference<Image>(pageBufferImage);
            // IMPORTANT! we don't won't to do a copy area if the page state is dirty.
            pagePainter.setIsBufferDirty(false);
        }

        // correct horizontal dimensions
        if (bufferedPageImageBounds.x < 0) {
            bufferedPageImageBounds.x = 0;
        }
        if ((bufferedPageImageBounds.x + bufferedPageImageBounds.width) > pageSize.width) {
            bufferedPageImageBounds.width = pageSize.width - bufferedPageImageBounds.x;
        }

        // correctly vertical dimensions
        if (bufferedPageImageBounds.y < 0) {
            bufferedPageImageBounds.y = 0;
        }
        if ((bufferedPageImageBounds.y + bufferedPageImageBounds.height) > pageSize.height) {
            bufferedPageImageBounds.height = pageSize.height - bufferedPageImageBounds.y;
        }

        if (pageBufferImage != null) {
            // get graphics context
            Graphics2D imageGraphics = (Graphics2D) pageBufferImage.getGraphics();
            // jdk 1.3.1 doesn't like a none (0,0)location for the clip,
            imageGraphics.setClip(new Rectangle(0, 0,
                    bufferedPageImageBounds.width,
                    bufferedPageImageBounds.height));

            // this is really important translate the image graphics
            // context so that we paint the correct area of the page to the image
            int xTrans = 0 - bufferedPageImageBounds.x;
            int yTrans = 0 - bufferedPageImageBounds.y;

            // copyRect is used for copy area from last good paint where possible.
            Rectangle copyRect;

            // block awt from repainting during copy area calculation and clear
            // background painting
            synchronized (paintCopyAreaLock) {

                // adjust the buffered Page bounds to component space
                Rectangle normalizedClipBounds = new Rectangle(clipBounds);
                normalizedClipBounds.x -= pageBounds.x;
                normalizedClipBounds.y -= pageBounds.y;

                // start an an area copy from the old buffer to the new buffer.
                if (!pagePainter.isLastPaintDirty() &&
                        pagePainter.isBufferDirty() &&
                        bufferedPageImageBounds.intersects(oldBufferedPageImageBounds)) {

                    // calculate intersection for buffer copy of a visible area, as we
                    // can only copy graphics that are visible.
                    copyRect = bufferedPageImageBounds.intersection(oldBufferedPageImageBounds);
                    copyRect = copyRect.intersection(normalizedClipBounds);

                    // setup graphics context for copy, we need to use old buffer bounds
                    int xTransOld = 0 - oldBufferedPageImageBounds.x;
                    int yTransOld = 0 - oldBufferedPageImageBounds.y;

                    // copy the old area, relative to whole page
                    int dx = oldBufferedPageImageBounds.x - bufferedPageImageBounds.x;
                    int dy = oldBufferedPageImageBounds.y - bufferedPageImageBounds.y;

                    // notice the appending of xTransOld and yTransOld
                    // this is the same as a imageGraphics.translate(xTransOld,yTransOld)
                    // but the copyArea method on the mac does not respect the translate, Doh!
                    imageGraphics.copyArea(copyRect.x + xTransOld,
                            copyRect.y + yTransOld,
                            copyRect.width,
                            copyRect.height,
                            dx,
                            dy);
                    // calculate the clip to set
                    Area copyArea = new Area(copyRect);
                    Area bufferArea = new Area(bufferedPageImageBounds);
                    bufferArea.subtract(copyArea);

                    // set the new clip, relative to whole page
                    imageGraphics.translate(xTrans, yTrans);
                    imageGraphics.setClip(bufferArea);

                    // restore graphics context.
                    imageGraphics.translate(-xTrans, -yTrans);
                } else {
                    // set the new clip, relative to whole page
                    imageGraphics.translate(xTrans, yTrans);
                    imageGraphics.setClip(bufferedPageImageBounds);
                    // restore graphics context.
                    imageGraphics.translate(-xTrans, -yTrans);
                }
                // setup graphics context for repaint
                imageGraphics.translate(xTrans, yTrans);

                // paint background to white to avoid old buffer garbage paint.
                imageGraphics.setColor(pageColor);
                imageGraphics.fillRect(bufferedPageImageBounds.x,
                        bufferedPageImageBounds.y,
                        bufferedPageImageBounds.width,
                        bufferedPageImageBounds.height);
            }

            // Paint the page content
            if (page != null) {
                page.paint(imageGraphics,
                        GraphicsRenderingHints.SCREEN,
                        documentViewModel.getPageBoundary(),
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom(),
                        false, false);

                if (pagePainter.isStopPaintingRequested()) {
                    pagePainter.setIsLastPaintDirty(true);
                    pagePainter.setIsBufferDirty(true);
                } else {
                    pagePainter.setIsLastPaintDirty(false);
                    pagePainter.setIsBufferDirty(false);
                }

                // one last paint once everything is done
                Runnable doSwingWork = new Runnable() {
                    public void run() {
                        if (!disposing)
                            repaint();
                    }
                };
                SwingUtilities.invokeLater(doSwingWork);
            }
            imageGraphics.dispose();
        }
    }

    private void addPageRepaintListener() {

        Page currentPage = pageTree.getPage(pageIndex);
        if (currentPage != null) {
            currentPage.addPaintPageListener(this);
        }
    }

    private void removePageRepaintListener() {
        if (inited) {
            Page currentPage = pageTree.getPage(pageIndex);
            if (currentPage != null) {
                currentPage.removePaintPageListener(this);
            }
        }

    }

    private boolean isPageStateDirty() {
        boolean tmp = currentZoom != documentViewModel.getViewZoom() ||
                currentRotation != documentViewModel.getViewRotation()
                || oldClipBounds.width != clipBounds.width
                || oldClipBounds.height != clipBounds.height;
        return tmp;
    }

    private boolean isPageIntersectViewport() {
        Rectangle pageBounds = documentViewModel.getPageBounds(pageIndex);
        return pageBounds != null && this.isShowing() &&
                pageBounds.intersects(parentScrollPane.getViewport().getViewRect());
    }

    public void paintPage(PaintPageEvent event) {
        Object source = event.getSource();
        Page page = pageTree.getPage(pageIndex);
        if (page.equals(source)) {
            Runnable doSwingWork = new Runnable() {
                public void run() {
                    if (!disposing) {
                        repaint();
                    }
                }
            };
            // initiate the repaint
            SwingUtilities.invokeLater(doSwingWork);
        }
    }

    public class PagePainter implements Runnable {

        private boolean isRunning;
        private boolean isLastPaintDirty;
        private boolean isBufferyDirty;
        private boolean isStopRequested;

        private Page page;

        private final Object isRunningLock = new Object();

        private boolean hasBeenQueued;

        public synchronized boolean isLastPaintDirty() {
            synchronized (isRunningLock) {
                return isLastPaintDirty;
            }
        }

        public void setIsLastPaintDirty(boolean isDirty) {
            synchronized (isRunningLock) {
                isLastPaintDirty = isDirty;
            }
        }

        public void setIsBufferDirty(boolean isDirty) {
            synchronized (isRunningLock) {
                isBufferyDirty = isDirty;
            }
        }

        public boolean isBufferDirty() {
            synchronized (isRunningLock) {
                return isBufferyDirty;
            }
        }

        public boolean isStopPaintingRequested() {
            synchronized (isRunningLock) {
                return isStopRequested;
            }
        }

        // stop painting
        public synchronized void stopPaintingPage() {
            synchronized (isRunningLock) {
                isStopRequested = true;
                isLastPaintDirty = true;
                getPage().requestInterrupt();
            }
        }

        public void setPage(Page page) {
            this.page = page;
        }

        public void run() {
            synchronized (isRunningLock) {
                isRunning = true;
                hasBeenQueued = false;
            }

            try {
                // revalidate the annotation components.
                if (isPageStateDirty()) {
                    Runnable doSwingWork = new Runnable() {
                        public void run() {
                            invalidate();
                            validate();
                        }
                    };
                    SwingUtilities.invokeLater(doSwingWork);
                }
                createBufferedPageImage(page, this);
                // add annotation components to container, we try this again
                // as the page might have been initialized via some other path
                // like thumbnails, searches or text extraction.
                refreshAnnotationComponents(page);
                isBufferyDirty = false;
                page = null;
                if (isPageStateDirty()) {
                    // one more paint for the road.
                    Runnable doSwingWork = new Runnable() {
                        public void run() {
                            repaint();
                        }
                    };
                    SwingUtilities.invokeLater(doSwingWork);
                }
            } catch (Throwable e) {
                logger.log(Level.WARNING,
                        "Error creating buffer, page: " + pageIndex, e);
                // mark as dirty, so that it tries again to create buffer
                currentZoom = -1;
            }

            synchronized (isRunningLock) {
                isStopRequested = false;
                isRunning = false;
            }
        }

        public boolean hasBeenQueued() {
            synchronized (isRunningLock) {
                return hasBeenQueued;
            }
        }

        public void setHasBeenQueued(boolean hasBeenQueued) {
            synchronized (isRunningLock) {
                this.hasBeenQueued = hasBeenQueued;
            }
        }

        public boolean isRunning() {
            synchronized (isRunningLock) {
                return isRunning;
            }
        }
    }

    private class PageInitializer implements Runnable {

        private boolean isRunning;
        private final Object isRunningLock = new Object();

        private AbstractPageViewComponent pageComponent;
        private boolean hasBeenQueued;

        private Page page;

        private PageInitializer(AbstractPageViewComponent pageComponent) {
            this.pageComponent = pageComponent;
        }

        private void setPage(Page page) {
            this.page = page;
        }

        public void run() {
            synchronized (isRunningLock) {
                isRunning = true;
            }
            try {
                page = pageTree.getPage(pageIndex);
                page.init();

                // add annotation components to container, this only done
                // once, but Annotation state can be refreshed with the api
                // when needed.
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        refreshAnnotationComponents(page);
                        page = null;
                    }
                });
                // fire page annotation initialized callback
                if (documentViewController.getAnnotationCallback() != null) {
                    documentViewController.getAnnotationCallback()
                            .pageAnnotationsInitialized(page);
                }
            } catch (Throwable e) {
                logger.log(Level.WARNING,
                        "Error initiating page: " + pageIndex, e);
                // make sure we don't try to re-initialize
                hasBeenQueued = true;
                return;
            }

            synchronized (isRunningLock) {
                pageInitializer.setHasBeenQueued(false);
                isRunning = false;
            }
        }

        public boolean hasBeenQueued() {
            synchronized (isRunningLock) {
                return hasBeenQueued;
            }
        }

        public void setHasBeenQueued(boolean hasBeenQueued) {
            synchronized (isRunningLock) {
                this.hasBeenQueued = hasBeenQueued;
            }
        }

        public boolean isRunning() {
            synchronized (isRunningLock) {
                return isRunning;
            }
        }
    }

    private class DirtyTimerAction implements ActionListener {

        private AbstractPageViewComponent pageComponent;

        private Page page;

        private DirtyTimerAction(AbstractPageViewComponent pageComponent) {
            this.pageComponent = pageComponent;
        }

        public void actionPerformed(ActionEvent e) {
            if (disposing || !isPageIntersectViewport()) {
                isDirtyTimer.stop();
                page = null;

                // stop painting and mark buffer as dirty
                if (pagePainter.isRunning()) {
                    pagePainter.stopPaintingPage();
                    currentZoom = -1;
                }
                return;
            }

            // if we are scrolling, no new threads
            if (!disposing && isPageIntersectViewport()) {

                boolean isBufferDirty = pagePainter.isBufferDirty() || isBufferDirty();

                // we don't want to draw if we are scrolling
                // calculate the number of pixels moved
                int diff = Math.abs(previousScrollValue -
                        parentScrollPane.getVerticalScrollBar().getValue());
                previousScrollValue = parentScrollPane.getVerticalScrollBar().getValue();
                // we don't want to draw if scroll distance is great then 150 px.
                if (parentScrollPane != null && diff > scrollInitThreshold) {
                    return;
                }
                page = pageTree.getPage(pageIndex);
                // load the page content
                if (isBufferDirty &&
                        page != null && !page.isInitiated() &&
                        !pageInitializer.isRunning() &&
                        !pageInitializer.hasBeenQueued()) {
                    pageInitializer.setHasBeenQueued(true);
                    pageInitializer.setPage(page);
                    Library.execute(pageInitializer);
                }

                // paint page content
                boolean tmp = !pageInitializer.isRunning() &&
                        page != null &&
                        page.isInitiated() &&
                        !pagePainter.isRunning() &&
                        !pagePainter.hasBeenQueued();
                isBufferDirty = pagePainter.isBufferDirty() || isBufferDirty();
                if (page != null &&
                        tmp &&
                        (isPageStateDirty() || isBufferDirty)
                        ) {
                    pagePainter.setPage(page);
                    pagePainter.setHasBeenQueued(true);
                    pagePainter.setIsBufferDirty(isBufferDirty);
                    Library.executePainter(pagePainter);
                }
                // paint page content
                if (page != null &&
                        !pageInitializer.isRunning() &&
                        page.isInitiated() &&
                        !pagePainter.hasBeenQueued() &&
                        pagePainter.isRunning()
                        ) {
                    // stop painting and mark buffer as dirty
                    if (isPageStateDirty()) {
                        pagePainter.stopPaintingPage();
                    }
                }
                // check annotation states, there is a possibility that the
                // page was initialized by some other process and in such
                // a case the pageInitializer would not have build the up the
                // annotationComponents.
//                if (!pageInitializer.isRunning() &&
//                        page != null &&
//                        page.isInitiated() &&
//                        page.getAnnotations() != null &&
//                        annotationComponents == null) {
//                    SwingUtilities.invokeLater(new Runnable() {
//                        public void run() {
//                            refreshAnnotationComponents(page);
//                        }
//                    });
//                }
            }
        }
    }
}
