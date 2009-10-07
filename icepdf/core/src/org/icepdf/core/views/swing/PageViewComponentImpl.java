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
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.core.util.MemoryManager;
import org.icepdf.core.util.ColorUtil;
import org.icepdf.core.views.DocumentView;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.DocumentViewModel;

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.lang.ref.SoftReference;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.logging.Level;

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
        implements PaintPageListener, MouseInputListener,
        FocusListener, ComponentListener {

    private static final Logger logger =
            Logger.getLogger(PageViewComponentImpl.class.toString());

    // disable/enable file caching, overrides fileCachingSize.
    private static boolean isInteractiveAnnotationsEnabled;

    private static Color pageColor;
    private static Color annotationHighlightColor;
    private static float annotationHighlightAlpha;

    static{
        // enables interactive annotation support.
        isInteractiveAnnotationsEnabled =
                Defs.sysPropertyBoolean(
                        "org.icepdf.core.annotations.interactive.enabled", true);

        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.paper.color", "#FFFFFF");
            int colorValue = ColorUtil.convertNamedColor(color);
            pageColor =
                    new Color( colorValue > 0? colorValue :
                            Integer.parseInt("FFFFFF", 16 ));

        } catch (NumberFormatException e) {
            logger.warning("Error reading page paper color.");
        }

        // sets annotation selected highlight colour
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.highlight.color", "#000000");
            int colorValue = ColorUtil.convertColor(color);
            annotationHighlightColor =
                    new Color( colorValue > 0? colorValue :
                            Integer.parseInt("000000", 16 ));

        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading page annotation highlight colour");
            }
        }

        // set the annotation alpha value.
        // sets annotation selected highlight colour
        try {
            String alpha = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.highlight.alpha", "0.4");
            annotationHighlightAlpha = Float.parseFloat(alpha);

        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading page annotation highlight alpha");
            }
            annotationHighlightAlpha = 0.4f;
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

    private int mediaBox = Page.BOUNDARY_CROPBOX;

    protected DocumentView parentDocumentView;
    protected DocumentViewModel documentViewModel;
    protected DocumentViewController documentViewController;

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

    // annotation support
    private Annotation currentAnnotation;
    private boolean isMousePressed = false;


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

    public void init() {
        if (inited) {
            return;
        }
        inited = true;
        // add listeners
        addMouseListener(this);
        addMouseMotionListener(this);
        addComponentListener(this);
        // add repaint listener
        addPageRepaintListener();

        // timer will dictate when buffer repaints can take place
        DirtyTimerAction dirtyTimerAction = new DirtyTimerAction();
        isDirtyTimer = new Timer(250, dirtyTimerAction);
        isDirtyTimer.setInitialDelay(0);

        // PageInilizer and painter commands
        pageInitilizer = new PageInitilizer();
        pagePainter = new PagePainter();
    }

    public void invalidatePage(){
        if (inited){
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

    public void setDocumentViewCallback(DocumentView parentDocumentView) {
        this.parentDocumentView = parentDocumentView;
        documentViewController = this.parentDocumentView.getParentViewController();
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void setMediaType(final int pageBoundary) {
        mediaBox = pageBoundary;
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
            Page currentPage = pageTree.getPage(pageIndex, this);
            if (currentPage != null && currentPage.isInitiated()) {
                Vector annotations = currentPage.getAnnotations();
                if (annotations != null) {

                    Graphics2D gg2 = (Graphics2D) gg;
                    AffineTransform at = currentPage.getPageTransform(
                            mediaBox,
                            documentViewModel.getViewRotation(),
                            documentViewModel.getViewZoom());
                    gg2.transform(at);

                    // paint all annotations on top of the content buffer
                    Object tmp;
                    Annotation annotation;
                    for (Object annotation1 : annotations) {
                        tmp = annotation1;
                        if (tmp instanceof Annotation) {
                            annotation = (Annotation) tmp;
                            annotation.render(gg2, GraphicsRenderingHints.SCREEN,
                                    documentViewModel.getViewRotation(), documentViewModel.getViewZoom(), false);
                        }
                    }

                    // annotation appearance dictionary, rollover, down appearance.
                    if (currentAnnotation != null &&
                            currentAnnotation.allowScreenRolloverMode() &&
                            isMousePressed) {
                        if (currentAnnotation instanceof LinkAnnotation) {
                            LinkAnnotation linkAnnotation = (LinkAnnotation) currentAnnotation;
                            int highlightMode = linkAnnotation.getHighlightMode();
                            if (highlightMode == LinkAnnotation.HIGHLIGHT_INVERT) {
                                Rectangle2D rect = currentAnnotation.getUserSpaceRectangle();
                                gg2.setColor(annotationHighlightColor);
                                gg2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                                        annotationHighlightAlpha));
                                gg2.fillRect((int) rect.getX(),
                                        (int) rect.getY(),
                                        (int) rect.getWidth(),
                                        (int) rect.getHeight());
                            } else if (highlightMode == LinkAnnotation.HIGHLIGHT_OUTLINE) {
                                Rectangle2D rect = currentAnnotation.getUserSpaceRectangle();
                                gg2.setColor(annotationHighlightColor);
                                gg2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                                        annotationHighlightAlpha));
                                gg2.drawRect((int) rect.getX(),
                                        (int) rect.getY(),
                                        (int) rect.getWidth(),
                                        (int) rect.getHeight());
                            } else if (highlightMode == LinkAnnotation.HIGHLIGHT_PUSH) {
                                Rectangle2D rect = currentAnnotation.getUserSpaceRectangle();
                                gg2.setColor(annotationHighlightColor);
                                gg2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                                        annotationHighlightAlpha));
                                gg2.drawRect((int) rect.getX(),
                                        (int) rect.getY(),
                                        (int) rect.getWidth(),
                                        (int) rect.getHeight());
                            }
                        }
                    }
                }
            }
            pageTree.releasePage(currentPage, this);
        }
    }

    public void mouseClicked(MouseEvent e) {
        // depending on tool state propagate mouse state
        if (documentViewModel.getViewToolMode() ==
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
        }
        // if cuurrentAnnotation exists, we want to process the click.
        if (currentAnnotation != null &&
                documentViewController.getAnnotationCallback() != null) {
            documentViewController.getAnnotationCallback()
                    .proccessAnnotationAction(currentAnnotation);
        }
    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    public void mousePressed(MouseEvent e) {
        Point p = e.getPoint();
        Point offset = this.getLocation();
        p.setLocation(p.x + offset.x, p.y + offset.y);
        MouseEvent newEvent =
                new MouseEvent((Component) e.getSource(), e.getID(), e.getWhen(),
                        e.getModifiers(), p.x, p.y, e.getClickCount(),
                        e.isPopupTrigger());

        // mouse pressed is picked up for panning and annotations
        // if currentAnnotation is not null then we can pan,
        if (currentAnnotation == null &&
                parentDocumentView != null) {
            parentDocumentView.mousePressed(newEvent);
        }
        // setup visual effect when the mouse button is pressed or held down
        // inside the active area of the annotation.
        isMousePressed = true;
        if (currentAnnotation != null) {
            repaint();
        }

    }

    public void mouseReleased(MouseEvent e) {
        if (currentAnnotation == null &&
                parentDocumentView != null) {
            parentDocumentView.mouseReleased(e);
        }
        isMousePressed = false;
        if (currentAnnotation != null) {
            repaint();
        }
    }

    public void mouseDragged(MouseEvent e) {
        if (parentDocumentView != null) {
            parentDocumentView.mouseDragged(e);
        }
    }

    public void mouseMoved(MouseEvent e) {
        if (parentDocumentView != null) {
            parentDocumentView.mouseMoved(e);
        }
        Page currentPage = pageTree.getPage(pageIndex, this);
        if (currentPage != null &&
                currentPage.isInitiated() &&
                isInteractiveAnnotationsEnabled) {
            Vector annotations = currentPage.getAnnotations();
            if (annotations != null) {
                Annotation annotation;
                Object tmp;
                Point mouseLocation;
                AffineTransform at = currentPage.getPageTransform(
                        mediaBox,
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom());
                mouseLocation = e.getPoint();

                try {
                    at.inverseTransform(mouseLocation, mouseLocation);
                } catch (NoninvertibleTransformException e1) {
                    e1.printStackTrace();
                }

                for (Object annotation1 : annotations) {
                    tmp = annotation1;
                    if (tmp instanceof Annotation) {
                        annotation = (Annotation) tmp;
                        // repaint an annotation. 
                        if (annotation.getUserSpaceRectangle().contains(
                                mouseLocation.getX(), mouseLocation.getY())) {
                            currentAnnotation = annotation;
                            documentViewController.setViewCursor(DocumentViewController.CURSOR_HAND_ANNOTATION);
//                            repaint(annotation.getUserSpaceRectangle().getBounds());
                            repaint();
                            break;
                        } else {
                            currentAnnotation = null;
                        }
                    }
                }
                if (currentAnnotation == null) {
                    int toolMode = documentViewModel.getViewToolMode();
                    if (toolMode == DocumentViewModel.DISPLAY_TOOL_PAN) {
                        documentViewController.setViewCursor(DocumentViewController.CURSOR_HAND_OPEN);
                    } else if (toolMode == DocumentViewModel.DISPLAY_TOOL_ZOOM_IN) {
                        documentViewController.setViewCursor(DocumentViewController.CURSOR_ZOOM_IN);
                    } else if (toolMode == DocumentViewModel.DISPLAY_TOOL_ZOOM_OUT) {
                        documentViewController.setViewCursor(DocumentViewController.CURSOR_ZOOM_OUT);
                    }
                    repaint();
                }
            }
        }
        pageTree.releasePage(currentPage, this);
    }

    public void focusGained(FocusEvent e) {
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
                        mediaBox,
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom()).toDimension());

                defaultPageSize.setSize(currentPage.getSize(
                        mediaBox,
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
                        mediaBox,
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom(),
                        pagePainter, false);
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
        return pageBounds != null &&
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

        private boolean hasBeenQueued;

        public void run() {
            synchronized (isRunningLock) {
                isRunning = true;
            }

            try {
                Page page = pageTree.getPage(pageIndex, this);
                page.init();
                // fire page annotation initialized callback
                if (documentViewController.getAnnotationCallback() != null){
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
