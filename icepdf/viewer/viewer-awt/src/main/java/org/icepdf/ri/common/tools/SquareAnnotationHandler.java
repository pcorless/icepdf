/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common.tools;

import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.BorderStyle;
import org.icepdf.core.pobjects.annotations.SquareAnnotation;
import org.icepdf.core.util.ColorUtil;
import org.icepdf.core.util.Defs;
import org.icepdf.ri.common.ViewModel;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;
import org.icepdf.ri.common.views.annotations.AnnotationComponentFactory;
import org.icepdf.ri.common.views.annotations.MarkupAnnotationComponent;
import org.icepdf.ri.common.views.annotations.PopupAnnotationComponent;
import org.icepdf.ri.util.PropertiesManager;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SquareAnnotationHandler tool is responsible for painting representation of
 * a square on the screen during a click and drag mouse event.  The box
 * created by this mouse event will be used to draw square within its bounds.
 * <br>
 * Once the mouseReleased event is fired this handler will create new
 * SquareAnnotation and respective AnnotationComponent.  The addition of the
 * Annotation object to the page is handled by the annotation callback.
 *
 * @since 5.0
 */
public class SquareAnnotationHandler extends SelectionBoxHandler implements ToolHandler {

    private static final Logger logger =
            Logger.getLogger(SquareAnnotationHandler.class.toString());

    protected final static float DEFAULT_STROKE_WIDTH = 3.0f;

    private static BasicStroke stroke;
    private static float strokeWidth;
    private static Color lineColor;
    private static Color internalColor;
    private static boolean useInternalColor;
    private static int defaultOpacity;

    static {

        // sets annotation squareCircle stroke colour
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.square.stroke.color", "#ff0000");
            int colorValue = ColorUtil.convertColor(color);
            lineColor =
                    new Color(colorValue >= 0 ? colorValue :
                            Integer.parseInt("ff0000", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading squareAnnotation stroke colour");
            }
        }

        // sets annotation link squareCircle colour
        useInternalColor = Defs.booleanProperty(
                "org.icepdf.core.views.page.annotation.square.fill.enabled", false);

        // sets annotation link squareCircle colour
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.square.fill.color", "#ffffff");
            int colorValue = ColorUtil.convertColor(color);
            internalColor =
                    new Color(colorValue >= 0 ? colorValue :
                            Integer.parseInt("ffffff", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading square Annotation fill colour");
            }
        }

        // sets annotation opacity
        defaultOpacity = Defs.intProperty(
                "org.icepdf.core.views.page.annotation.square.fill.opacity", 255);

        strokeWidth = (float) Defs.doubleProperty("org.icepdf.core.views.page.annotation.square.stroke.width",
                DEFAULT_STROKE_WIDTH);

        // need to make the stroke cap, thickness configurable. Or potentially
        // static from the AnnotationHandle so it would look like the last
        // settings where remembered.
        stroke = new BasicStroke(strokeWidth,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER,
                1.0f);
    }

    // start and end point
    protected Rectangle rectangle;

    protected BorderStyle borderStyle = new BorderStyle();

    /**
     * New Text selection handler.  Make sure to correctly and and remove
     * this mouse and text listeners.
     *
     * @param pageViewComponent page component that this handler is bound to.
     * @param documentViewController view controller.
     */
    public SquareAnnotationHandler(DocumentViewController documentViewController,
                                   AbstractPageViewComponent pageViewComponent) {
        super(documentViewController, pageViewComponent);
        borderStyle.setStrokeWidth(DEFAULT_STROKE_WIDTH);

        // make sure we are drawing the facade with the correct look and feel.
        checkAndApplyPreferences();
    }

    public void paintTool(Graphics g) {
        if (rectangle != null) {
            Graphics2D gg = (Graphics2D) g;
            Color oldColor = gg.getColor();
            Stroke oldStroke = gg.getStroke();
            gg.setStroke(stroke);
            gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, defaultOpacity / 255.0f));
            if (useInternalColor) {
                gg.setColor(internalColor);
                gg.fill(rectangle);
            }
            gg.setColor(lineColor);
            gg.draw(rectangle);
            g.setColor(oldColor);
            gg.setStroke(oldStroke);
        }
    }

    public void mousePressed(MouseEvent e) {
        int x = e.getX();
        int y = e.getY();
        if (rectangle == null) {
            rectangle = new Rectangle();
        }
        currentRect = new Rectangle(x, y, 0, 0);
        updateDrawableRect(pageViewComponent.getWidth(),
                pageViewComponent.getHeight());
        rectangle.setRect(currentRect);
        pageViewComponent.repaint();
    }

    public void mouseReleased(MouseEvent e) {
        updateSelectionSize(e.getX(), e.getY(), pageViewComponent);

        // convert the rectangle to page space
        rectangle = convertToPageSpace(rectangle);

        // check to make sure the bbox isn't zero height or width
        rectToDraw.setRect(rectToDraw.getX() - DEFAULT_STROKE_WIDTH,
                rectToDraw.getY() - DEFAULT_STROKE_WIDTH,
                rectToDraw.getWidth() + DEFAULT_STROKE_WIDTH * 2,
                rectToDraw.getHeight() + DEFAULT_STROKE_WIDTH * 2);

        // convert tBbox
        Rectangle tBbox = convertToPageSpace(rectToDraw);

        // create annotations types that that are rectangle based;
        // which is actually just link annotations
        SquareAnnotation annotation = (SquareAnnotation)
                AnnotationFactory.buildAnnotation(
                        documentViewController.getDocument().getPageTree().getLibrary(),
                        Annotation.SUBTYPE_SQUARE,
                        tBbox);

        checkAndApplyPreferences();

        // set the private contents flag.
        ViewModel viewModel = documentViewController.getParentController().getViewModel();
        annotation.setFlag(Annotation.FLAG_PRIVATE_CONTENTS, !viewModel.getAnnotationPrivacy());
        annotation.setCreationDate(PDate.formatDateTime(new Date()));
        annotation.setTitleText(System.getProperty("user.name"));

        annotation.setColor(lineColor);
        annotation.setOpacity(defaultOpacity);
        if (annotation.isFillColor() || useInternalColor) {
            annotation.setFillColor(internalColor);
            if (!annotation.isFillColor()) {
                annotation.setFillColor(true);
            }
        }
        borderStyle.setStrokeWidth(strokeWidth);
        annotation.setRectangle(rectangle);
        annotation.setBorderStyle(borderStyle);

        AffineTransform pageTransform = getToPageSpaceTransform();

        // pass outline shapes and bounds to create the highlight shapes
        annotation.setBBox(new Rectangle(0, 0, tBbox.width, tBbox.height));
        annotation.resetAppearanceStream(pageTransform);

        // create the annotation object.
        MarkupAnnotationComponent comp = (MarkupAnnotationComponent)
                AnnotationComponentFactory.buildAnnotationComponent(
                        annotation, documentViewController, pageViewComponent);
        // set the bounds and refresh the userSpace rectangle
        Rectangle bbox = new Rectangle(rectToDraw.x, rectToDraw.y,
                rectToDraw.width, rectToDraw.height);
        comp.setBounds(bbox);
        // resets user space rectangle to match bbox converted to page space
        comp.refreshAnnotationRect();

        // add them to the container, using absolute positioning.
        documentViewController.addNewAnnotation(comp);

        // associate popup to location
        PopupAnnotationComponent popupAnnotationComponent = comp.getPopupAnnotationComponent();
        popupAnnotationComponent.setBoudsRelativeToParent(
                bbox.x + (bbox.width / 2), bbox.y + (bbox.height / 2), pageTransform);
        popupAnnotationComponent.setVisible(false);
        popupAnnotationComponent.getAnnotation().setOpen(false);

        // set the annotation tool to he select tool
        if (preferences.getBoolean(PropertiesManager.PROPERTY_ANNOTATION_SQUARE_SELECTION_ENABLED, false)) {
            documentViewController.getParentController().setDocumentToolMode(
                    DocumentViewModel.DISPLAY_TOOL_SELECTION);
        }

        rectangle = null;
        // clear the rectangle
        clearRectangle(pageViewComponent);
    }

    @Override
    protected void checkAndApplyPreferences() {
        defaultOpacity = preferences.getInt(
                PropertiesManager.PROPERTY_ANNOTATION_SQUARE_OPACITY, defaultOpacity);
        lineColor = new Color(preferences.getInt(
                PropertiesManager.PROPERTY_ANNOTATION_SQUARE_COLOR, lineColor.getRGB()));
        internalColor = new Color(preferences.getInt(
                PropertiesManager.PROPERTY_ANNOTATION_SQUARE_FILL_COLOR, internalColor.getRGB()));
    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    public void mouseClicked(MouseEvent e) {
        if (pageViewComponent != null) {
            pageViewComponent.requestFocus();
        }
    }

    public void mouseMoved(MouseEvent e) {

    }

    public void installTool() {

    }

    public void uninstallTool() {

    }

    public void mouseDragged(MouseEvent e) {
        updateSelectionSize(e.getX(), e.getY(), pageViewComponent);
        rectangle.setRect(rectToDraw);
        pageViewComponent.repaint();
    }

    @Override
    public void setSelectionRectangle(Point cursorLocation, Rectangle selection) {

    }
}
