/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common.views.annotations;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.acroform.AdditionalActionsDictionary;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.actions.Action;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.Appearance;
import org.icepdf.core.util.ColorUtil;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.views.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AbstractAnnotationComponent contains base functionality for annotation
 * components which are used to display annotation for a given page view. This
 * class controls icon state, focus and basic component states: editable,
 * movable, resizable, selected and show invisible border.
 *
 * @since 5.0
 */
public abstract class AbstractAnnotationComponent<T extends Annotation> extends JComponent implements FocusListener,
        MouseInputListener, AnnotationComponent, ResizeableComponent {

    protected static final Logger logger =
            Logger.getLogger(AbstractAnnotationComponent.class.toString());
    protected static boolean isInteractiveAnnotationsEnabled;
    protected static Color annotationHighlightColor;
    protected static float annotationHighlightAlpha;

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
                    new Color(colorValue >= 0 ? colorValue :
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
    protected static ResizableBorder resizableBorder =
            new ResizableBorder(resizeBoxSize);

    protected PageViewComponentImpl pageViewComponent;
    protected DocumentViewController documentViewController;

    protected float currentZoom;
    protected float currentRotation;

    protected T annotation;
    protected boolean isMousePressed;
    protected boolean resized;
    protected boolean wasResized;

    // border state flags.
    protected boolean isEditable;
    protected boolean isRollover;
    protected boolean isMovable;
    protected boolean isResizable;
    protected boolean isShowInvisibleBorder;
    protected boolean isSelected;

    // drag offset
    protected int dx = 0;
    protected int dy = 0;

    // selection, move and resize handling.
    protected int cursor;
    protected Point startPos;
    protected AnnotationState previousAnnotationState;
    // total distance moved on mouse down/up.
    protected Point startOfMousePress;
    protected Point endOfMousePress;

    // context Menu
    protected JPopupMenu contextMenu;

    protected ResourceBundle messageBundle;

    public AbstractAnnotationComponent(T annotation,
                                       DocumentViewController documentViewController,
                                       AbstractPageViewComponent pageViewComponent) {
        this.pageViewComponent = (PageViewComponentImpl) pageViewComponent;
        this.documentViewController = documentViewController;
        this.annotation = annotation;
        messageBundle = documentViewController.getParentController().getMessageBundle();

        // border and behavior default properties.
        isEditable = !annotation.getFlagReadOnly();
        isRollover = false;
        isMovable = !(annotation.getFlagReadOnly() || annotation.getFlagLocked());
        isResizable = !(annotation.getFlagReadOnly() || annotation.getFlagLocked());

        // lock UI controls.
        if (isInteractiveAnnotationsEnabled) {
            addMouseListener(this);
            addMouseMotionListener(this);

            // disabled focus until we are ready to implement our own handler.
            setFocusable(true);
            addFocusListener(this);

            // setup a resizable border.
            setLayout(new BorderLayout());
            setBorder(resizableBorder);

            // set component location and original size.
            DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
            Page currentPage = pageViewComponent.getPage();
            AffineTransform at = currentPage.getPageTransform(
                    documentViewModel.getPageBoundary(),
                    documentViewModel.getViewRotation(),
                    documentViewModel.getViewZoom());
            Rectangle location = Page.convertTo(annotation.getUserSpaceRectangle(), at).getBounds();
            setBounds(location);

            // update zoom and rotation state
            currentRotation = documentViewModel.getViewRotation();
            currentZoom = documentViewModel.getViewZoom();
            resizableBorder.setZoom(currentZoom);
        }

    }

    public abstract boolean isActive();

    public Document getDocument() {
        return documentViewController.getDocument();
    }

    public int getPageIndex() {
        return pageViewComponent.getPageIndex();
    }

    public AbstractPageViewComponent getPageViewComponent() {
        return pageViewComponent;
    }

    public void removeMouseListeners() {
        removeMouseListener(this);
        removeMouseMotionListener(this);
    }

    public T getAnnotation() {
        return annotation;
    }

    public void focusGained(FocusEvent e) {
        isSelected = true;

        // on mouse enter pass event to annotation callback if we are in normal viewing
        // mode. A and AA dictionaries are taken into consideration.
        additionalActionsHandler(AdditionalActionsDictionary.ANNOTATION_FO_KEY, null);

        repaint();
    }

    public void focusLost(FocusEvent e) {

        // if we've lost focus then drop the selected state
        isSelected = false;

        // on mouse enter pass event to annotation callback if we are in normal viewing
        // mode. A and AA dictionaries are taken into consideration.
        additionalActionsHandler(AdditionalActionsDictionary.ANNOTATION_Bl_KEY, null);

        repaint();
    }

    protected void resize() {
        if (getParent() != null) {
            getParent().validate();
        }
        resized = true;
    }

    /**
     * Refreshes the components bounds for the current page transformation.
     * Bounds have are already in user space.
     */
    public void refreshDirtyBounds() {
        Page currentPage = pageViewComponent.getPage();
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        AffineTransform at = currentPage.getPageTransform(
                documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(),
                documentViewModel.getViewZoom());
        setBounds(commonBoundsNormalization(new GeneralPath(
                annotation.getUserSpaceRectangle()), at));
    }

    /**
     * Refreshes/transforms the page space bounds back to user space.  This
     * must be done in order refresh the annotation user space rectangle after
     * UI manipulation, otherwise the annotation will be incorrectly located
     * on the next repaint.
     */
    public void refreshAnnotationRect() {
        Page currentPage = pageViewComponent.getPage();
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        AffineTransform at = currentPage.getToPageSpaceTransform(
                documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(),
                documentViewModel.getViewZoom());
        // store the new annotation rectangle in its original user space
        Rectangle bounds = getBounds();
        annotation.syncBBoxToUserSpaceRectangle(commonBoundsNormalization(new GeneralPath(bounds), at));
    }

    /**
     * Normalizes and the given path with the specified transform.  The method
     * also rounds the Rectangle2D bounds values when creating a new rectangle
     * instead of truncating the values.
     *
     * @param shapePath path to apply transform to
     * @param at        transform to apply to shapePath
     * @return bound value of the shape path.
     */
    protected Rectangle commonBoundsNormalization(GeneralPath shapePath,
                                                  AffineTransform at) {
        shapePath.transform(at);
        Rectangle2D pageSpaceBound = shapePath.getBounds2D();
        return new Rectangle(
                (int) Math.round(pageSpaceBound.getX()),
                (int) Math.round(pageSpaceBound.getY()),
                (int) Math.round(pageSpaceBound.getWidth()),
                (int) Math.round(pageSpaceBound.getHeight()));
    }

    public void validate() {
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        if (currentZoom != documentViewModel.getViewZoom() ||
                currentRotation != documentViewModel.getViewRotation()) {
            refreshDirtyBounds();
            currentRotation = documentViewModel.getViewRotation();
            currentZoom = documentViewModel.getViewZoom();
            resizableBorder.setZoom(currentZoom);
        }

        if (resized) {
            refreshAnnotationRect();
            if (getParent() != null) {
//                getParent().validate();
                getParent().repaint();
            }
            resized = false;
            wasResized = true;
        }

    }

    public void paintComponent(Graphics g) {
    }

    abstract public void resetAppearanceShapes();

    public void mouseMoved(MouseEvent me) {

        int toolMode = documentViewController.getDocumentViewModel().getViewToolMode();

        if (toolMode == DocumentViewModel.DISPLAY_TOOL_SELECTION &&
                !(annotation.getFlagLocked() || annotation.getFlagReadOnly())) {
            Border border = getBorder();
            if (border instanceof ResizableBorder) {
                setCursor(Cursor.getPredefinedCursor(((ResizableBorder) border).getCursor(me)));
            }
        } else {
            // set cursor back to the hand cursor.
            setCursor(documentViewController.getViewCursor(
                    DocumentViewController.CURSOR_HAND_ANNOTATION));
        }
    }

    public void dispose() {
        removeMouseListener(this);
        removeMouseMotionListener(this);
        // disabled focus until we are ready to implement our own handler.
        removeFocusListener(this);
    }

    public void mouseExited(MouseEvent mouseEvent) {

        // set selected appearance state
        annotation.setCurrentAppearance(Annotation.APPEARANCE_STREAM_NORMAL_KEY);

        // on exit pass event to annotation callback if we are in normal viewing
        // mode. A and AA dictionaries are taken into consideration.
        additionalActionsHandler(AdditionalActionsDictionary.ANNOTATION_X_KEY, mouseEvent);

        setCursor(Cursor.getDefaultCursor());
        isRollover = false;
        repaint();
    }

    public void mouseClicked(MouseEvent e) {
        // clear the selection.
        requestFocus();

    }

    public void mouseEntered(MouseEvent e) {
        // reset the appearance steam
        Appearance hover = annotation.getAppearances().get(Annotation.APPEARANCE_STREAM_ROLLOVER_KEY);
        if (hover != null && hover.hasAlternativeAppearance()) {
            // set selected appearance state
            hover.setSelectedName(hover.getOnName());
            annotation.setCurrentAppearance(Annotation.APPEARANCE_STREAM_ROLLOVER_KEY);
        }

        // set border highlight when mouse over.
        isRollover = (documentViewController.getDocumentViewModel().getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION ||
                (this instanceof PopupAnnotationComponent));

        // on mouse enter pass event to annotation callback if we are in normal viewing
        // mode. A and AA dictionaries are taken into consideration.
        //additionalActionsHandler(AdditionalActionsDictionary.ANNOTATION_E_KEY, e);
        repaint();
    }

    public void mousePressed(MouseEvent e) {

        requestFocus();
        // setup visual effect when the mouse button is pressed or held down
        // inside the active area of the annotation.
        isMousePressed = true;
        Point point = new Point();
        if (e != null) {
            point = e.getPoint();
        }
        startOfMousePress = point;
        endOfMousePress = new Point(point); // need clone not a copy...

        // check if there is a mouse down state
        Appearance down = annotation.getAppearances().get(Annotation.APPEARANCE_STREAM_DOWN_KEY);
        if (down != null && down.hasAlternativeAppearance()) {
            if (down.getSelectedName().equals(down.getOnName())) {
                down.setSelectedName(down.getOffName());
            } else {
                down.setSelectedName(down.getOnName());
            }
            annotation.setCurrentAppearance(Annotation.APPEARANCE_STREAM_DOWN_KEY);
        }

        if (documentViewController.getDocumentViewModel().getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION &&
                isInteractiveAnnotationsEnabled &&
                !annotation.getFlagReadOnly()) {
            initiateMouseMoved(e);
        }


        repaint();
    }

    protected boolean additionalActionsHandler(Name additionalActionKey, MouseEvent e) {
        if (!(AbstractPageViewComponent.isAnnotationTool(
                documentViewController.getDocumentViewModel().getViewToolMode())) &&
                isInteractiveAnnotationsEnabled) {
            if (documentViewController.getAnnotationCallback() != null) {
                int x = -1, y = -1;
                if (e != null) {
                    x = e.getX();
                    y = e.getY();
                }
                // get the A and AA entries.
                if (annotation instanceof AbstractWidgetAnnotation) {
                    AbstractWidgetAnnotation widgetAnnotation = (AbstractWidgetAnnotation) annotation;
                    FieldDictionary fieldDictionary = widgetAnnotation.getFieldDictionary();
                    if (fieldDictionary != null) {
                        AdditionalActionsDictionary additionalActionsDictionary =
                                fieldDictionary.getAdditionalActionsDictionary();
                        if (additionalActionsDictionary != null &&
                                additionalActionsDictionary.isAnnotationValue(additionalActionKey)) {
                            documentViewController.getAnnotationCallback()
                                    .processAnnotationAction(annotation,
                                            additionalActionsDictionary.getAction(additionalActionKey),
                                            x, y);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    protected void initiateMouseMoved(MouseEvent e) {
        Border border = getBorder();
        if (border != null && border instanceof ResizableBorder) {
            cursor = ((ResizableBorder) border).getCursor(e);
        }
        startPos = e.getPoint();
        previousAnnotationState = new AnnotationState(this);
        // mark annotation as selected.
        documentViewController.assignSelectedAnnotation(this);
    }

    public void mouseDragged(MouseEvent me) {

        if (startPos != null && isMovable &&
                !(annotation.getFlagLocked() || annotation.getFlagReadOnly())) {

            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();

            dx = me.getX() - startPos.x;
            dy = me.getY() - startPos.y;

            if (endOfMousePress != null) {
                endOfMousePress.setLocation(endOfMousePress.x + dx, endOfMousePress.y + dy);
            }

            switch (cursor) {
                case Cursor.N_RESIZE_CURSOR:
                    if (isResizable && !(h - dy < 12)) {
                        setBounds(x, y + dy, w, h - dy);
                        resize();
                        setCursor(Cursor.getPredefinedCursor(cursor));
                    }
                    break;

                case Cursor.S_RESIZE_CURSOR:
                    if (isResizable && !(h + dy < 12)) {
                        setBounds(x, y, w, h + dy);
                        startPos = me.getPoint();
                        resize();
                        setCursor(Cursor.getPredefinedCursor(cursor));
                    }
                    break;

                case Cursor.W_RESIZE_CURSOR:
                    if (isResizable && !(w - dx < 18)) {
                        setBounds(x + dx, y, w - dx, h);
                        resize();
                        setCursor(Cursor.getPredefinedCursor(cursor));
                    }
                    break;

                case Cursor.E_RESIZE_CURSOR:
                    if (isResizable && !(w + dx < 18)) {
                        setBounds(x, y, w + dx, h);
                        startPos = me.getPoint();
                        resize();
                        setCursor(Cursor.getPredefinedCursor(cursor));
                    }
                    break;

                case Cursor.NW_RESIZE_CURSOR:
                    if (isResizable && !(w - dx < 18) && !(h - dy < 18)) {
                        setBounds(x + dx, y + dy, w - dx, h - dy);
                        resize();
                        setCursor(Cursor.getPredefinedCursor(cursor));
                    }
                    break;

                case Cursor.NE_RESIZE_CURSOR:
                    if (isResizable && !(w + dx < 18) && !(h - dy < 18)) {
                        setBounds(x, y + dy, w + dx, h - dy);
                        startPos = new Point(me.getX(), startPos.y);
                        resize();
                        setCursor(Cursor.getPredefinedCursor(cursor));
                    }
                    break;

                case Cursor.SW_RESIZE_CURSOR:
                    if (isResizable && !(w - dx < 18) && !(h + dy < 18)) {
                        setBounds(x + dx, y, w - dx, h + dy);
                        startPos = new Point(startPos.x, me.getY());
                        resize();
                        setCursor(Cursor.getPredefinedCursor(cursor));
                    }
                    break;

                case Cursor.SE_RESIZE_CURSOR:
                    if (isResizable && !(w + dx < 18) && !(h + dy < 18)) {
                        setBounds(x, y, w + dx, h + dy);
                        startPos = me.getPoint();
                        resize();
                        setCursor(Cursor.getPredefinedCursor(cursor));
                    }
                    break;
                default:
                    if (isMovable) {
                        Rectangle bounds = getBounds();
                        bounds.translate(dx, dy);
                        setBounds(bounds);
                        resize();
                        setCursor(Cursor.getPredefinedCursor(cursor));
                    }
                    break;

            }
            validate();
        }
    }

    public void mouseReleased(MouseEvent mouseEvent) {
        startPos = null;
        isMousePressed = false;

        // reset the appearance steam
        Appearance down = annotation.getAppearances().get(Annotation.APPEARANCE_STREAM_DOWN_KEY);
        if (down != null && down.hasAlternativeAppearance()) {
            if (down.getSelectedName().equals(down.getOnName())) {
                down.setSelectedName(down.getOffName());
            } else {
                down.setSelectedName(down.getOnName());
            }
        }
        // set selected appearance state
        annotation.setCurrentAppearance(Annotation.APPEARANCE_STREAM_NORMAL_KEY);

        // check to see if a move/resize occurred and if so we add the
        // state change to the memento in document view.
        if (wasResized) {
            wasResized = false;

            // update the bounds
            refreshAnnotationRect();

            double dx = 0;
            double dy = 0;
            if (startOfMousePress != null &&
                    endOfMousePress != null) {
                dx = endOfMousePress.getX() - startOfMousePress.getX();
                dy = endOfMousePress.getY() - startOfMousePress.getY();
            }

            annotation.resetAppearanceStream(dx, -dy, getToPageSpaceTransform());

            // fire new bounds change event, let the listener handle
            // how to deal with the bound change.
            documentViewController.firePropertyChange(
                    PropertyConstants.ANNOTATION_BOUNDS,
                    previousAnnotationState, new AnnotationState(this));

            // notify the annotation callback of the annotation resize.
            documentViewController.updateAnnotation(this);
        }
        if (mouseEvent != null && mouseEvent.getButton() == MouseEvent.BUTTON1) {

            // on mouse released event to annotation callback if we are in normal viewing
            // mode. A and AA dictionaries are taken into consideration.
            additionalActionsHandler(AdditionalActionsDictionary.ANNOTATION_U_KEY, mouseEvent);

            // on lef mouse pressed event to annotation callback if we are in normal viewing
            // mode. A and AA dictionaries are taken into consideration.
            boolean actionFired = additionalActionsHandler(AdditionalActionsDictionary.ANNOTATION_D_KEY, mouseEvent);

            // fire the main action associated with the
            if (!actionFired && !(AbstractPageViewComponent.isAnnotationTool(
                    documentViewController.getDocumentViewModel().getViewToolMode())) &&
                    isInteractiveAnnotationsEnabled) {
                if (documentViewController.getAnnotationCallback() != null) {
                    // get the A and AA entries.
                    Action action = annotation.getAction();
                    documentViewController.getAnnotationCallback()
                            .processAnnotationAction(annotation, action, mouseEvent.getX(), mouseEvent.getY());
                }
            }
        }

        repaint();

    }

    /**
     * Convert the shapes that make up the annotation to page space so that
     * they will scale correctly at different zooms.
     *
     * @param rect rectangle to convert to page space.
     * @return transformed bbox.
     */
    protected Rectangle convertToPageSpace(Rectangle rect) {
        Page currentPage = pageViewComponent.getPage();
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        return currentPage.convertToPageSpace(rect, documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(),
                documentViewModel.getViewZoom());
    }

    /**
     * Convert the shapes that make up the annotation to page space so that
     * they will scale correctly at different zooms.
     *
     * @param shape shape to convert to page space.
     * @return transformed bbox.
     */
    protected Shape convertToPageSpace(Shape shape) {
        Page currentPage = pageViewComponent.getPage();
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        return currentPage.convertToPageSpace(shape,
                documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(),
                documentViewModel.getViewZoom());

    }

    protected AffineTransform getToPageSpaceTransform() {
        Page currentPage = pageViewComponent.getPage();
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        return currentPage.getToPageSpaceTransform(
                documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(),
                documentViewModel.getViewZoom());
    }

    protected AffineTransform getPageSpaceTransform() {
        Page currentPage = pageViewComponent.getPage();
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        return currentPage.getPageTransform(
                documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(),
                documentViewModel.getViewZoom());
    }

    /**
     * Is the annotation editable
     *
     * @return true if editable, false otherwise.
     */
    public boolean isEditable() {
        return isEditable;
    }

    public boolean isRollover() {
        return isRollover;
    }

    public boolean isBorderStyle() {
        return annotation.isBorder();
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public boolean isMovable() {
        return isMovable;
    }

    public boolean isResizable() {
        return isResizable;
    }

    public boolean isShowInvisibleBorder() {
        return isShowInvisibleBorder;
    }
}