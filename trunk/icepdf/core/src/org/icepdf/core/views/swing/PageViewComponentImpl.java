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
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
package org.icepdf.core.views.swing;

import org.icepdf.core.events.PaintPageEvent;
import org.icepdf.core.events.PaintPageListener;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.core.util.*;
import org.icepdf.core.views.AnnotationComponent;
import org.icepdf.core.views.DocumentView;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.DocumentViewModel;
import org.icepdf.core.views.common.AnnotationHandler;
import org.icepdf.core.views.common.TextSelectionPageHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.lang.ref.SoftReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>This class represents a single page view of a PDF document as a JComponent.
 * This component can be used in any swing application to display a PDF page.  The
 * default RI implemenation comes with four predefined page views which use this
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
 * @see org.icepdf.core.views.DocumentViewController
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
            int colorValue = ColorUtil.convertNamedColor(color);
            pageColor =
                    new Color(colorValue > 0 ? colorValue :
                            Integer.parseInt("FFFFFF", 16));

        } catch (NumberFormatException e) {
            logger.warning("Error reading page paper color.");
        }

    }

    private PageTree pageTree;
    private JScrollPane parentScrollPane;
    private int pageIndex;

    private Rectangle pageSize = new Rectangle();
    // cached page size, we call this a lot.
    private Rectangle defaultPageSize = new Rectangle();

    private boolean isPageSizeCalculated = false;
    private float currentZoom;
    private float currentRotation;

    protected DocumentView parentDocumentView;
    protected DocumentViewModel documentViewModel;
    protected DocumentViewController documentViewController;

    // annotation mouse, key and paint handler.
    protected AnnotationHandler annotationHandler;
    // text selection mouse/key and paint handler
    protected TextSelectionPageHandler textSelectionHandler;

    // the buffered image which will be painted to
    private SoftReference<Image> bufferedPageImageReference;
    // the bounds of the buffered image.
    private Rectangle bufferedPageImageBounds = new Rectangle();

    private Timer isDirtyTimer;
    //    private DirtyTimerAction dirtyTimerAction;
    private PageInitilizer pageInitilizer;
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

    static {
        // default value have been assigned.  Keep in mind that larger ratios will
        // result in more memory usage.
        try {
            verticalScaleFactor =
                    Double.parseDouble(Defs.sysProperty("org.icepdf.core.views.buffersize.vertical",
                            "1.0"));

            horizontalScaleFactor =
                    Double.parseDouble(Defs.sysProperty("org.icepdf.core.views.buffersize.horizontal",
                            "1.0"));
        } catch (NumberFormatException e) {
            logger.warning("Error reading buffered scale factor");
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
        setFocusable(true);
        // add focus listener
        addFocusListener(this);

        // needed to propagate mouse events.
        this.documentViewModel = documentViewModel;
        this.parentScrollPane = parentScrollPane;

        // annotation action, selection and creation
        annotationHandler = new AnnotationHandler(this, documentViewModel);
        // text selection
        textSelectionHandler = new TextSelectionPageHandler(this,
                documentViewModel);

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
     * If no DocumentView is used then the various mouse and keyboard
     * listeners must be added tothis component.  If there is a document
     * view then we let it delegate events to make life easier.
     */
    public void addPageViewComponentListeners() {
        // add listeners
        addMouseListener(this);
        addMouseMotionListener(this);
        addComponentListener(this);
        // annotation pickups
        // handles, multiple selections and new annotation creation. 
        addMouseListener(annotationHandler);
        addMouseMotionListener(annotationHandler);

        // text selection mouse handler
        addMouseMotionListener(textSelectionHandler);
        addMouseListener(textSelectionHandler);
    }

    /**
     * Adds the specified annotation to this page instance.  The annotation
     * is wrapped with a AnnotationComponent and added to this components layout
     * manager.
     *
     * @param annotation annotation to add to this page instance. .
     */
    public AnnotationComponent addAnnotation(Annotation annotation) {
        // delegate to handler.
        return annotationHandler.addAnnotationComponent(annotation);
    }

    /**
     * Removes the specified annotation from this page component
     *
     * @param annotationComp annotation to be removed.
     */
    public void removeAnnotation(AnnotationComponent annotationComp) {
        // delegate to handler. 
        annotationHandler.removeAnnotationComponent(annotationComp);
    }

    public void init() {
        if (inited) {
            return;
        }
        inited = true;

        // add repaint listener
        addPageRepaintListener();

        // timer will dictate when buffer repaints can take place
        DirtyTimerAction dirtyTimerAction = new DirtyTimerAction();
        isDirtyTimer = new Timer(250, dirtyTimerAction);
        isDirtyTimer.setInitialDelay(0);

        // PageInilizer and painter commands
        pageInitilizer = new PageInitilizer(this);
        pagePainter = new PagePainter();
    }

    public void invalidatePage() {
        if (inited) {
            Page page = pageTree.getPage(pageIndex, this);
            page.getLibrary().disposeFontResources();
            page.reduceMemory();
            pageTree.releasePage(page, this);
            currentZoom = -1;
        }
    }

    public void dispose() {

        disposing = true;
        if (isDirtyTimer != null)
            isDirtyTimer.stop();

        removeMouseListener(this);
        removeMouseMotionListener(this);
        removeComponentListener(this);

        // remove annotation listeners.
        removeMouseMotionListener(annotationHandler);
        removeMouseListener(annotationHandler);
        // todo unhook annotation handler. 

        // text selection
        removeMouseMotionListener(textSelectionHandler);
        removeMouseListener(textSelectionHandler);

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

        inited = false;
    }

    public Page getPageLock(Object lock) {
        return pageTree.getPage(pageIndex, lock);
    }

    public void releasePageLock(Page currentPage, Object lock) {
        pageTree.releasePage(currentPage, lock);
    }

    public void setDocumentViewCallback(DocumentView parentDocumentView) {
        this.parentDocumentView = parentDocumentView;
        documentViewController = this.parentDocumentView.getParentViewController();

        // set annotation callback
        annotationHandler.setDocumentViewController(documentViewController);
        // set text selection callback
        textSelectionHandler.setDocumentViewController(documentViewController);
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
            invalidate();
        } else if (isPageStateDirty()) {
            calculatePageSize(pageSize);
        }

        Graphics2D g = (Graphics2D) gg.create(0, 0, pageSize.width, pageSize.height);

        g.setColor(pageColor);
        g.fillRect(0, 0, pageSize.width, pageSize.height);


        if (isPageIntersectViewport() && !isDirtyTimer.isRunning()) {
            isDirtyTimer.start();
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
            }
            // paint annotations
            annotationHandler.paintAnnotations(g);

            // Lazy paint of highlight and select all text states.
            Page currentPage = this.getPageLock(this);
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

                // paint selected test sprites.
                textSelectionHandler.paintSelectedText(g);
            }
            this.releasePageLock(currentPage, this);
        }
    }

    /**
     * Mouse clicked event priority is given to annotation clicks.  Otherwise
     * the selected tool state is respected.
     *
     * @param e awt mouse event.
     */
    public void mouseClicked(MouseEvent e) {

        // depending on tool state propagate mouse state
        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {
            textSelectionHandler.mouseClicked(e);
        } else if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_ZOOM_IN) {
            // correct click for coordinate of this component
            Point p = e.getPoint();
            Point offset = documentViewModel.getPageBounds(pageIndex).getLocation();
            p.setLocation(p.x + offset.x, p.y + offset.y);
            // request a zoom center on the new point
            documentViewController.setZoomIn(p);
        } else if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_ZOOM_OUT) {
            // correct click for coordinate of this component
            Point p = e.getPoint();
            // request a zoom center on the new point
            documentViewController.setZoomOut(p);
        } else if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION) {
            annotationHandler.mouseClicked(e);
        }
    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    public void mousePressed(MouseEvent e) {

        // request page focus
//        requestFocusInWindow();

        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {
            textSelectionHandler.mousePressed(e);
        } else if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION ||
                documentViewModel.getViewToolMode() ==
                        DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION) {
            annotationHandler.mousePressed(e);
        }

    }

    public void clearSelectedText() {
        if (textSelectionHandler != null) {
            textSelectionHandler.clearSelection();
        }
    }

    public void mouseReleased(MouseEvent e) {
        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {
            textSelectionHandler.mouseReleased(e);
        } else if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION ||
                documentViewModel.getViewToolMode() ==
                        DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION) {
            annotationHandler.mouseReleased(e);
        }
    }

    public void mouseDragged(MouseEvent e) {
        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {
            textSelectionHandler.mouseDragged(e);
        } else if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION ||
                documentViewModel.getViewToolMode() ==
                        DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION) {
            annotationHandler.mouseDragged(e);
        }
    }

    public void setTextSelectionRectangle(Point cursorLocation, Rectangle selection) {
        textSelectionHandler.setSelectionRectangle(cursorLocation, selection);
    }

    public void mouseMoved(MouseEvent e) {

        // process text selection.
        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {
            textSelectionHandler.mouseMoved(e);
        } else if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION) {
            annotationHandler.mouseMoved(e);
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
            Page currentPage = pageTree.getPage(pageIndex, this);
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
            pageTree.releasePage(currentPage, this);
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
    private void createBufferedPageImage(PagePainter pagePainter) {
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
        pageBufferImage = bufferedPageImageReference.get();
        // draw the clean buffer
        if (isPageStateDirty || pageBufferImage == null) {
            // clear old buffer
            if (pageBufferImage != null) {
                pageBufferImage.flush();
            }

            // checkMemroy, but before we flush the old
            int neededMemory = bufferedPageImageBounds.width *
                    bufferedPageImageBounds.height * 3;

            if (MemoryManager.getInstance().checkMemory(neededMemory)) {
                // create new image and get graphics context from image
                GraphicsConfiguration gc = getGraphicsConfiguration();
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
            } else {
                if (logger.isLoggable(Level.FINE)) {
                    logger.info("Error creating buffer, not enough memory: page " + pageIndex);
                }
                // mark as dirty, so that it tries again to create buffer
                currentZoom = -1;
            }
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
                }
// The following block was an attempt stop painting during a paint when scrolling.  However
                // the effect was not easy on the eyes.
//                else if ( pagePainter.isLastPaintDirty() &&
//                          pagePainter.isBufferDirty()
//                          && lastGoodCopyAreaBounds.intersects(normalizedClipBounds)
//                          && lastGoodCopyAreaBounds.intersects(bufferedPageImageBounds)
//                        ){
//                    System.out.println("Copy last good");
//
//                    // only want the visible area
//                    lastGoodCopyAreaBounds.setBounds(lastGoodCopyAreaBounds.intersection(normalizedClipBounds));
//
//                    // setup graphics context for copy, we need to use old buffer bounds
//                    int xTransOld = 0 - oldBufferedPageImageBounds.x;
//                    int yTransOld = 0 - oldBufferedPageImageBounds.y;
//
//                    // copy the old area, relative to whole page
//                    int dx = oldBufferedPageImageBounds.x - bufferedPageImageBounds.x;
//                    int dy = oldBufferedPageImageBounds.y - bufferedPageImageBounds.y;
//
//                    // notice the appending of xTransOld and yTransOld
//                    // this is the same as a imageGraphics.translate(xTransOld,yTransOld)
//                    // but the copyArea method on the mac does not respect the translate, Doh!
//                    imageGraphics.copyArea(lastGoodCopyAreaBounds.x + xTransOld,
//                                           lastGoodCopyAreaBounds.y + yTransOld,
//                                           lastGoodCopyAreaBounds.width,
//                                           lastGoodCopyAreaBounds.height,
//                                           dx,
//                                           dy);
//
//                    // calculate the clip to set
//                    Area copyArea = new Area(lastGoodCopyAreaBounds);
//                    Area bufferArea = new Area(bufferedPageImageBounds);
//                    bufferArea.subtract(copyArea);
//
//                    // set the new clip, relative to whole page
//                    imageGraphics.translate(xTrans, yTrans);
//                    imageGraphics.setClip(bufferArea);
//
//                    // restore graphics context.
//                    imageGraphics.translate(-xTrans, -yTrans);
//                }
//                else if (
//                            pagePainter.isBufferDirty()
//                            && pagePainter.isLastPaintDirty()
                else {
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
            if (pageTree != null) {
                Page page = pageTree.getPage(pageIndex, this);
                page.paint(imageGraphics,
                        GraphicsRenderingHints.SCREEN,
                        documentViewModel.getPageBoundary(),
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom(),
                        pagePainter, false, false);
                // clean up
                pageTree.releasePage(page, this);

                if (pagePainter.isStopPaintingRequested()) {
                    pagePainter.setIsLastPaintDirty(true);
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

        Page currentPage = pageTree.getPage(pageIndex, this);
        if (currentPage != null) {
            currentPage.addPaintPageListener(this);
        }
        pageTree.releasePage(currentPage, this);
    }

    private void removePageRepaintListener() {
        if (inited) {
            Page currentPage = pageTree.getPage(pageIndex, this);
            if (currentPage != null) {
                currentPage.removePaintPageListener(this);
            }
            pageTree.releasePage(currentPage, this);
        }

    }

    private boolean isPageStateDirty() {
        return currentZoom != documentViewModel.getViewZoom() ||
                currentRotation != documentViewModel.getViewRotation()
                || oldClipBounds.width != clipBounds.width
                || oldClipBounds.height != clipBounds.height
                ;
    }

    private boolean isPageIntersectViewport() {
        Rectangle pageBounds = documentViewModel.getPageBounds(pageIndex);
        return pageBounds != null && this.isShowing() &&
                pageBounds.intersects(parentScrollPane.getViewport().getViewRect());
    }

    public void paintPage(PaintPageEvent event) {
        Object source = event.getSource();
        Page page = pageTree.getPage(pageIndex, this);
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
        pageTree.releasePage(page, this);


    }

    public class PagePainter implements Runnable {

        private boolean isRunning;
        private boolean isLastPaintDirty;
        private boolean isBufferyDirty;
        private boolean isStopRequested;

        private final Object isRunningLock = new Object();

        private boolean hasBeenQueued;

        public synchronized boolean isLastPaintDirty() {
            return isLastPaintDirty;
        }

        public void setIsLastPaintDirty(boolean isDirty) {
            isLastPaintDirty = isDirty;
        }

        public void setIsBufferDirty(boolean isDirty) {
            isBufferyDirty = isDirty;
        }

        public boolean isBufferDirty() {
            return isBufferyDirty;
        }

        public boolean isStopPaintingRequested() {
            return isStopRequested;
        }

        // stop painting
        public synchronized void stopPaintingPage() {
            isStopRequested = true;
            isLastPaintDirty = true;
        }

        public void run() {
            synchronized (isRunningLock) {
                isRunning = true;
                hasBeenQueued = false;
            }

            try {
                createBufferedPageImage(this);
            }
            catch (Throwable e) {
                logger.log(Level.FINE,
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
            this.hasBeenQueued = hasBeenQueued;
        }

        public boolean isRunning() {
            synchronized (isRunningLock) {
                return isRunning;
            }
        }
    }

    private class PageInitilizer implements Runnable {

        private boolean isRunning;
        private final Object isRunningLock = new Object();

        private AbstractPageViewComponent pageComponent;
        private boolean hasBeenQueued;

        private PageInitilizer(AbstractPageViewComponent pageComponent) {
            this.pageComponent = pageComponent;
        }

        public void run() {
            synchronized (isRunningLock) {
                isRunning = true;
            }

            try {
                Page page = pageTree.getPage(pageIndex, this);
                page.init();
                // add annotation components to container, this only done
                // once, but Annotation state can be refreshed with the api
                // when needed.
                annotationHandler.initializeAnnotationComponents(
                        page.getAnnotations());
                // fire page annotation initialized callback
                if (documentViewController.getAnnotationCallback() != null) {
                    documentViewController.getAnnotationCallback()
                            .pageAnnotationsInitialized(page);
                }
                pageTree.releasePage(page, this);
            }
            catch (Throwable e) {
                logger.log(Level.FINE,
                        "Error initiating page: " + pageIndex, e);
                // make sure we don't try to re-initialize
                pageInitilizer.setHasBeenQueued(true);
                return;
            }

            synchronized (isRunningLock) {
                pageInitilizer.setHasBeenQueued(false);
                isRunning = false;
            }
        }

        public boolean hasBeenQueued() {
            return hasBeenQueued;
        }

        public void setHasBeenQueued(boolean hasBeenQueued) {
            this.hasBeenQueued = hasBeenQueued;
        }

        public boolean isRunning() {
            synchronized (isRunningLock) {
                return isRunning;
            }
        }
    }


    private class DirtyTimerAction implements ActionListener {

        public void actionPerformed(ActionEvent e) {
            if (disposing || !isPageIntersectViewport()) {
                isDirtyTimer.stop();

                // stop painting and mark buffer as dirty
                if (pagePainter.isRunning()) {
                    pagePainter.stopPaintingPage();
                    currentZoom = -1;
                }
                return;
            }

            // if we are scrolling, no new threads
            if (!disposing) {

                // we don't want to draw if we are scrolling
                if (parentScrollPane != null &&
                        parentScrollPane.getVerticalScrollBar().getValueIsAdjusting()) {
                    return;
                }

                // lock page
                Page page = pageTree.getPage(pageIndex, this);
                // load the page content
                if (page != null && !page.isInitiated() &&
                        !pageInitilizer.isRunning() &&
                        !pageInitilizer.hasBeenQueued()) {
                    try {
                        pageInitilizer.setHasBeenQueued(true);
                        documentViewModel.executePageInitialization(pageInitilizer);
                    }
                    catch (InterruptedException ex) {
                        pageInitilizer.setHasBeenQueued(false);
                        if (logger.isLoggable(Level.FINE)) {
                            logger.fine("Page Initialization Interrupted: " + pageIndex);
                        }
                    }
                }

                // paint page content
                boolean isBufferDirty = isBufferDirty();
                if (page != null &&
                        !pageInitilizer.isRunning() &&
                        page.isInitiated() &&
                        !pagePainter.isRunning() &&
                        !pagePainter.hasBeenQueued() &&
                        (isPageStateDirty() || isBufferDirty)
                        ) {

                    try {
                        pagePainter.setHasBeenQueued(true);
                        pagePainter.setIsBufferDirty(isBufferDirty);
                        documentViewModel.executePagePainter(pagePainter);
                    }
                    catch (InterruptedException ex) {
                        pagePainter.setHasBeenQueued(false);
                        if (logger.isLoggable(Level.FINE)) {
                            logger.fine("Page Painter Interrupted: " + pageIndex);
                        }
                    }
                }

                // paint page content
                if (page != null &&
                        !pageInitilizer.isRunning() &&
                        page.isInitiated() &&
                        !pagePainter.hasBeenQueued() &&
                        pagePainter.isRunning()
                        ) {
                    // stop painting and mark buffer as dirty
                    if (isPageStateDirty()) {
                        pagePainter.stopPaintingPage();
                    }
                }

                // unlock page
                pageTree.releasePage(page, this);
            }
        }
    }
}
