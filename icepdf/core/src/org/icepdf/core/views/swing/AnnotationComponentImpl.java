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

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.core.pobjects.annotations.AnnotationState;
import org.icepdf.core.util.ColorUtil;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.PropertyConstants;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.DocumentViewModel;
import org.icepdf.core.views.AnnotationComponent;
import org.icepdf.core.views.PageViewComponent;

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
public class AnnotationComponentImpl extends JComponent implements FocusListener,
        MouseInputListener, AnnotationComponent {

    private static final Logger logger =
            Logger.getLogger(AnnotationComponentImpl.class.toString());

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
    private boolean wasResized;

    // border state flags.
    private boolean isEditable;
    private boolean isRollover;
    private boolean isLinkAnnot;
    private boolean isBorderStyle;
    private boolean isSelected;

    // selection, move and resize handling.
    private int cursor;
    private Point startPos;
    private AnnotationState previousAnnotationState;

    public AnnotationComponentImpl(Annotation annotation,
                               DocumentViewController documentViewController,
                               AbstractPageViewComponent pageViewComponent,
                               DocumentViewModel documentViewModel) {
        this.pageViewComponent = pageViewComponent;
        this.documentViewModel = documentViewModel;
        this.documentViewController = documentViewController;
        this.annotation = annotation;

        addMouseListener(this);
        addMouseMotionListener(this);

        // disabled focus until we are ready to implement our own handler. 
//        setFocusable(true);
//        addFocusListener(this);


        // setup a resizable border.
        setLayout(new BorderLayout());
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

    public Document getDocument() {
        return documentViewModel.getDocument();
    }

    public int getPageIndex() {
        return pageViewComponent.getPageIndex();
    }

    public PageViewComponent getParentPageView() {
        return pageViewComponent;
    }

    public AbstractPageViewComponent getPageViewComponent() {
        return pageViewComponent;
    }

    public void removeMouseListeners() {
        removeMouseListener(this);
        removeMouseMotionListener(this);
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public void focusGained(FocusEvent e) {
//        repaint();
    }

    public void focusLost(FocusEvent e) {
//        repaint();
        // if we've lost focus then drop the selected state
//        isSelected = false;
    }

    private void resize() {
        if (getParent() != null) {
            ((JComponent) getParent()).revalidate();
        }
        resized = true;
    }

    /**
     * Refreshses the components bounds for the current page transformation.
     * Bounds have are allready in user space.
     */
    public void refreshDirtyBounds(){
        Page currentPage = pageViewComponent.getPageLock(this);
        AffineTransform at = currentPage.getPageTransform(
                documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(),
                documentViewModel.getViewZoom());
        pageViewComponent.releasePageLock(currentPage, this);
        setBounds(commonBoundsNormalization(new GeneralPath(
                annotation.getUserSpaceRectangle()), at));
    }

    /**
     * Refreshes/transforms the page space bounds back to user space.  This
     * must be done in order refresh the annotation user space rectangle after
     * UI manipulation, otherwise the annotation will be incorrectly located
     * on the next repaint.
     */
    public void refreshAnnotationRect(){
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
        rect.setRect(commonBoundsNormalization(new GeneralPath(bounds), at));
    }

    /**
     * Normalizes and the given path with the specified transform.  The method
     * also rounds the Rectangle2D bounds values when creating a new rectangle
     * instead of trunkating the values.
     *
     * @param shapePath path to apply transform to
     * @param at tranfor to apply to shapePath
     * @return bound value of the shape path.
     */
    private Rectangle commonBoundsNormalization(GeneralPath shapePath,
                                                AffineTransform at){
        shapePath.transform(at);
        Rectangle2D pageSpaceBound = shapePath.getBounds2D();
        return new Rectangle(
                (int)Math.round(pageSpaceBound.getX()),
                (int)Math.round(pageSpaceBound.getY()),
                (int)Math.round(pageSpaceBound.getWidth()),
                (int)Math.round(pageSpaceBound.getHeight()));
    }

    public void validate() {
        if (currentZoom != documentViewModel.getViewZoom() ||
                currentRotation != documentViewModel.getViewRotation()) {
            refreshDirtyBounds();
            currentRotation = documentViewModel.getViewRotation();
            currentZoom = documentViewModel.getViewZoom();
        }

        if (resized) {
            refreshAnnotationRect();
            if (getParent() != null) {
                ((JComponent) getParent()).revalidate();
                getParent().repaint();
            }
            resized = false;
            wasResized = true;
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
        isEditable = ( (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION ||
                documentViewModel.getViewToolMode() ==
                        DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION) &&
                !(annotation.getFlagReadOnly() || annotation.getFlagLocked() ||
                        annotation.getFlagInvisible() || annotation.getFlagHidden()));

        // paint rollover effects.
        if (isMousePressed && !(documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION ||
                documentViewModel.getViewToolMode() ==
                    DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION)) {
            Graphics2D gg2 = (Graphics2D) g;
            if (annotation instanceof LinkAnnotation) {
                LinkAnnotation linkAnnotation = (LinkAnnotation) annotation;
                String highlightMode = linkAnnotation.getHighlightMode();
                Rectangle2D rect = new Rectangle(0, 0, getWidth(), getHeight());
                if (LinkAnnotation.HIGHLIGHT_INVERT.equals(highlightMode)) {
                    gg2.setColor(annotationHighlightColor);
                    gg2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                            annotationHighlightAlpha));
                    gg2.fillRect((int) rect.getX(),
                            (int) rect.getY(),
                            (int) rect.getWidth(),
                            (int) rect.getHeight());
                } else if (LinkAnnotation.HIGHLIGHT_OUTLINE.equals(highlightMode)) {
                    gg2.setColor(annotationHighlightColor);
                    gg2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                            annotationHighlightAlpha));
                    gg2.drawRect((int) rect.getX(),
                            (int) rect.getY(),
                            (int) rect.getWidth(),
                            (int) rect.getHeight());
                } else if (LinkAnnotation.HIGHLIGHT_PUSH.equals(highlightMode)) {
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
        }else if (toolMode == DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION) {
            // keep it the same
        } else{
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
//        requestFocus();

        if (!(documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION ||
                documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION)&&
                isInteractiveAnnotationsEnabled) {

            if (documentViewController.getAnnotationCallback() != null) {
                documentViewController.getAnnotationCallback()
                        .proccessAnnotationAction(annotation);
            }
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

        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION &&
                isInteractiveAnnotationsEnabled) {
            ResizableBorder border = (ResizableBorder) getBorder();
            cursor = border.getCursor(me);
            startPos = me.getPoint();
            previousAnnotationState = new AnnotationState(this);
            // mark annotation as selected. 
            documentViewController.assignSelectedAnnotation(this);
        }
        repaint();
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
        }
    }

    public void mouseReleased(MouseEvent mouseEvent) {
        startPos = null;
        isMousePressed = false;

        // check to see if a move/resize occurred and if so we add the
        // state change to the memento in document view.
        if (wasResized){
            wasResized = false;


            // fire new bounds change event, let the listener handle
            // how to deal with the bound change.
            documentViewController.firePropertyChange(
                    PropertyConstants.ANNOTATION_BOUNDS,
                    previousAnnotationState, new AnnotationState(this));
        }
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

    /**
     * Is the annotation editable
     * @return true if editable, false otherwise.
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

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}
