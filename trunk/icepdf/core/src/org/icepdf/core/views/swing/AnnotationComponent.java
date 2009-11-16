package org.icepdf.core.views.swing;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.core.util.ColorUtil;
import org.icepdf.core.util.Defs;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.DocumentViewModel;

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents an editable annotations component. This class wraps an Annotation
 * object which already knows how to paint itself and adds a hooks for
 * editing the annotation via mouse manipulation.
 *
 * @since 4.0
 */
public class AnnotationComponent extends JComponent implements FocusListener,
        MouseInputListener {

    private static final Logger logger =
            Logger.getLogger(AnnotationComponent.class.toString());

    // disable/enable file caching, overrides fileCachingSize.
    private static boolean isInteractiveAnnotationsEnabled;
    private static Color annotationHighlightColor;
    private static float annotationHighlightAlpha;

    static {
        // enables interactive annotation support.
        isInteractiveAnnotationsEnabled =
                Defs.sysPropertyBoolean(
                        "org.icepdf.core.annotations.interactive.enabled", true);

        // sets annotation selected highlight colour
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.highlight.color", "#000000");
            int colorValue = ColorUtil.convertColor(color);
            annotationHighlightColor =
                    new Color(colorValue > 0 ? colorValue :
                            Integer.parseInt("000000", 16));

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


    public static final int resizeBoxSize = 4;

    // reusable border
    private static ResizableBorder resizableBorder =
            new ResizableBorder(resizeBoxSize);

    private AbstractPageViewComponent pageViewComponent;
    private DocumentViewController documentViewController;
    private DocumentViewModel documentViewModel;

    private float currentZoom;
    private float currentRotation;

    protected Annotation annotation;
    private boolean isMousePressed;
    private boolean resized;

    // border state flags.
    private boolean isEditable;
    private boolean isRollover;
    private boolean isLinkAnnot;
    private boolean isBorderStyle;

    // selection, move and resize handling.
    private int cursor;
    private Point startPos = null;

    public AnnotationComponent(Annotation annotation,
                               DocumentViewController documentViewController,
                               AbstractPageViewComponent pageViewComponent,
                               DocumentViewModel documentViewModel) {
        this.pageViewComponent = pageViewComponent;
        this.documentViewModel = documentViewModel;
        this.documentViewController = documentViewController;
        this.annotation = annotation;
        setFocusable(true);
        setLayout(new BorderLayout());

        addMouseListener(this);
        addMouseMotionListener(this);

        addFocusListener(this);

        // setup a resizable border.
        setBorder(resizableBorder);

        // set component location and original size.
        Page currentPage = pageViewComponent.getPageLock(this);
        AffineTransform at = currentPage.getPageTransform(
                documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(),
                documentViewModel.getViewZoom());
        pageViewComponent.releasePageLock(currentPage, this);
        Rectangle location =
                at.createTransformedShape(annotation.getUserSpaceRectangle()).getBounds();
        setBounds(location);

        // update zoom and rotation state
        currentRotation = documentViewModel.getViewRotation();
        currentZoom = documentViewModel.getViewZoom();

        // get border info
        isLinkAnnot = annotation instanceof LinkAnnotation;
        isBorderStyle = annotation.isBorder();

    }

    public void removeMouseListeners() {
        removeMouseListener(this);
        removeMouseMotionListener(this);
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public void focusGained(FocusEvent e) {
        repaint();
    }

    public void focusLost(FocusEvent e) {
        repaint();
    }

    private void resize() {
        if (getParent() != null) {
            ((JComponent) getParent()).revalidate();
        }
        resized = true;
    }

    public void validate() {
        if (currentZoom != documentViewModel.getViewZoom() ||
                currentRotation != documentViewModel.getViewRotation()) {
            Page currentPage = pageViewComponent.getPageLock(this);
            AffineTransform at = currentPage.getPageTransform(
                    documentViewModel.getPageBoundary(),
                    documentViewModel.getViewRotation(),
                    documentViewModel.getViewZoom());

            GeneralPath shapePath = new GeneralPath(
                    annotation.getUserSpaceRectangle().getBounds());
            shapePath.transform(at);
            setBounds(shapePath.getBounds());

            currentRotation = documentViewModel.getViewRotation();
            currentZoom = documentViewModel.getViewZoom();
            pageViewComponent.releasePageLock(currentPage, this);

            currentRotation = documentViewModel.getViewRotation();
            currentZoom = documentViewModel.getViewZoom();
        }

        if (resized) {
            Page currentPage = pageViewComponent.getPageLock(this);
            AffineTransform at = currentPage.getPageTransform(
                    documentViewModel.getPageBoundary(),
                    documentViewModel.getViewRotation(),
                    documentViewModel.getViewZoom());
            pageViewComponent.releasePageLock(currentPage, this);
            try {
                at = at.createInverse();
            } catch (NoninvertibleTransformException e1) {
                e1.printStackTrace();
            }
            // store the new annotation rectangle in its original user space
            Rectangle2D rect = annotation.getUserSpaceRectangle();
            Rectangle bounds = getBounds();
            Rectangle innerRectangle = new Rectangle(bounds.x + resizeBoxSize / 2,
                    bounds.y + resizeBoxSize / 2, bounds.width - resizeBoxSize, bounds.height - resizeBoxSize);
            GeneralPath shapePath = new GeneralPath(innerRectangle);
            shapePath.transform(at);
            rect.setRect(shapePath.getBounds());
            if (getParent() != null) {
                ((JComponent) getParent()).revalidate();
                getParent().repaint();
            }
            resized = false;
        }

    }

    public void paintComponent(Graphics g) {
        Page currentPage = pageViewComponent.getPageLock(this);
        if (currentPage != null && currentPage.isInitiated()) {
            // update bounds for for component
            if (currentZoom != documentViewModel.getViewZoom() ||
                    currentRotation != documentViewModel.getViewRotation()) {
                validate();
            }
        }
        pageViewComponent.releasePageLock(currentPage, this);

        // sniff out tool bar state to set correct annotation border
        isEditable = (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION &&
                !(annotation.getFlagReadOnly() || annotation.getFlagLocked() ||
                        annotation.getFlagInvisible() || annotation.getFlagHidden()));

        // paint rollover effects.
        if (isMousePressed && documentViewModel.getViewToolMode() !=
                DocumentViewModel.DISPLAY_TOOL_SELECTION) {
            Graphics2D gg2 = (Graphics2D) g;
            if (annotation instanceof LinkAnnotation) {
                LinkAnnotation linkAnnotation = (LinkAnnotation) annotation;
                int highlightMode = linkAnnotation.getHighlightMode();
                Rectangle2D rect = new Rectangle(0, 0, getWidth(), getHeight());
                if (highlightMode == LinkAnnotation.HIGHLIGHT_INVERT) {

                    gg2.setColor(annotationHighlightColor);
                    gg2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                            annotationHighlightAlpha));
                    gg2.fillRect((int) rect.getX(),
                            (int) rect.getY(),
                            (int) rect.getWidth(),
                            (int) rect.getHeight());
                } else if (highlightMode == LinkAnnotation.HIGHLIGHT_OUTLINE) {
                    gg2.setColor(annotationHighlightColor);
                    gg2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                            annotationHighlightAlpha));
                    gg2.drawRect((int) rect.getX(),
                            (int) rect.getY(),
                            (int) rect.getWidth(),
                            (int) rect.getHeight());
                } else if (highlightMode == LinkAnnotation.HIGHLIGHT_PUSH) {
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

    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
    }

    public void mouseMoved(MouseEvent me) {

        int toolMode = documentViewModel.getViewToolMode();

        if (toolMode == DocumentViewModel.DISPLAY_TOOL_SELECTION) {
            ResizableBorder border = (ResizableBorder) getBorder();
            setCursor(Cursor.getPredefinedCursor(border.getCursor(me)));
        } else {
            // set cursor back to the hand cursor. 
            setCursor(documentViewController.getViewCursor(
                    DocumentViewController.CURSOR_HAND_ANNOTATION));
        }

    }

    public void mouseExited(MouseEvent mouseEvent) {
        setCursor(Cursor.getDefaultCursor());
        isRollover = false;
        repaint();
    }

    public void mouseClicked(MouseEvent e) {
        // clear the selection.
        requestFocus();

        if (documentViewModel.getViewToolMode() !=
                DocumentViewModel.DISPLAY_TOOL_SELECTION &&
                isInteractiveAnnotationsEnabled) {
            if (documentViewController.getAnnotationCallback() != null) {
                documentViewController.getAnnotationCallback()
                        .proccessAnnotationAction(annotation);
            }
        } else {
            // todo call up to handler that we have selected this annotation
        }
    }

    public void mouseEntered(MouseEvent e) {
        // set border back to default
        isRollover = documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION;
        repaint();
    }

    public void mousePressed(MouseEvent me) {

        // setup visual effect when the mouse button is pressed or held down
        // inside the active area of the annotation.
        isMousePressed = true;
        repaint();

        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION) {
            ResizableBorder border = (ResizableBorder) getBorder();
            cursor = border.getCursor(me);
            startPos = me.getPoint();
            requestFocus();
        }

    }

    public void mouseDragged(MouseEvent me) {

        if (startPos != null) {

            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();

            int dx = me.getX() - startPos.x;
            int dy = me.getY() - startPos.y;

            switch (cursor) {
                case Cursor.N_RESIZE_CURSOR:
                    if (!(h - dy < 12)) {
                        setBounds(x, y + dy, w, h - dy);
                        resize();
                    }
                    break;

                case Cursor.S_RESIZE_CURSOR:
                    if (!(h + dy < 12)) {
                        setBounds(x, y, w, h + dy);
                        startPos = me.getPoint();
                        resize();
                    }
                    break;

                case Cursor.W_RESIZE_CURSOR:
                    if (!(w - dx < 18)) {
                        setBounds(x + dx, y, w - dx, h);
                        resize();
                    }
                    break;

                case Cursor.E_RESIZE_CURSOR:
                    if (!(w + dx < 18)) {
                        setBounds(x, y, w + dx, h);
                        startPos = me.getPoint();
                        resize();
                    }
                    break;

                case Cursor.NW_RESIZE_CURSOR:
                    if (!(w - dx < 18) && !(h - dy < 18)) {
                        setBounds(x + dx, y + dy, w - dx, h - dy);
                        resize();
                    }
                    break;

                case Cursor.NE_RESIZE_CURSOR:
                    if (!(w + dx < 18) && !(h - dy < 18)) {
                        setBounds(x, y + dy, w + dx, h - dy);
                        startPos = new Point(me.getX(), startPos.y);
                        resize();
                    }
                    break;

                case Cursor.SW_RESIZE_CURSOR:
                    if (!(w - dx < 18) && !(h + dy < 18)) {
                        setBounds(x + dx, y, w - dx, h + dy);
                        startPos = new Point(startPos.x, me.getY());
                        resize();
                    }
                    break;

                case Cursor.SE_RESIZE_CURSOR:
                    if (!(w + dx < 18) && !(h + dy < 18)) {
                        setBounds(x, y, w + dx, h + dy);
                        startPos = me.getPoint();
                        resize();
                    }
                    break;

                case Cursor.MOVE_CURSOR:
                    Rectangle bounds = getBounds();
                    bounds.translate(dx, dy);
                    setBounds(bounds);
                    resize();
            }
            setCursor(Cursor.getPredefinedCursor(cursor));
            validate();
        } else {
            // todo push event back to parent so that the selection box can be updated
        }
    }

    public void mouseReleased(MouseEvent mouseEvent) {
        startPos = null;
        isMousePressed = false;
        repaint();
    }

    /**
     * private ResizableBorder generateAnnotationBorder() {
     * <p/>
     * // none visible or locked, we don't want anything
     * if ((annotation.getFlagReadOnly() ||
     * annotation.getFlagLocked() ||
     * annotation.getFlagInvisible() ||
     * annotation.getFlagHidden())) {
     * return invisibleLockedBorder;
     * }
     * <p/>
     * // link annotation
     * if (annotation instanceof LinkAnnotation) {
     * // link annotation that has no border
     * if (annotation.getBorderStyle() == null) {
     * return outlineResizeBorder;
     * }
     * // link annotation that has a border
     * else {
     * return resizeBorder;
     * }
     * }
     * <p/>
     * // everything else, no border but movable is selected.
     * else {
     * return moveBorder;
     * }
     * }
     */


    public boolean isEditable() {
        return isEditable;
    }

    public boolean isRollover() {
        return isRollover;
    }

    public boolean isLinkAnnot() {
        return isLinkAnnot;
    }

    public boolean isBorderStyle() {
        return isBorderStyle;
    }
}
