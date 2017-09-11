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
package org.icepdf.ri.common.tools;

import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.CircleAnnotation;
import org.icepdf.core.util.ColorUtil;
import org.icepdf.core.util.Defs;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.AnnotationCallback;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;
import org.icepdf.ri.common.views.annotations.AbstractAnnotationComponent;
import org.icepdf.ri.common.views.annotations.AnnotationComponentFactory;
import org.icepdf.ri.util.PropertiesManager;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CircleAnnotationHandler tool is responsible for painting representation of
 * a circle on the screen during a click and drag mouse event.  The box
 * created by this mouse event will be used to draw circle within its bounds.
 * <br>
 * Once the mouseReleased event is fired this handler will create new
 * CircleAnnotation and respective AnnotationComponent.  The addition of the
 * Annotation object to the page is handled by the annotation callback.
 *
 * @since 5.0
 */
public class CircleAnnotationHandler extends SquareAnnotationHandler {

    private CircleAnnotation annotation;

    private static final Logger logger =
            Logger.getLogger(CircleAnnotationHandler.class.toString());

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
                    "org.icepdf.core.views.page.annotation.circle.stroke.color", "#ff0000");
            int colorValue = ColorUtil.convertColor(color);
            lineColor =
                    new Color(colorValue >= 0 ? colorValue :
                            Integer.parseInt("ff0000", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading circle Annotation stroke colour");
            }
        }

        // sets annotation link squareCircle colour
        useInternalColor = Defs.booleanProperty(
                "org.icepdf.core.views.page.annotation.circle.fill.enabled", false);

        // sets annotation link squareCircle colour
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.circle.fill.color", "#ffffff");
            int colorValue = ColorUtil.convertColor(color);
            internalColor =
                    new Color(colorValue >= 0 ? colorValue :
                            Integer.parseInt("ffffff", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading circle Annotation fill colour");
            }
        }

        // sets annotation opacity
        defaultOpacity = Defs.intProperty(
                "org.icepdf.core.views.page.annotation.squareCircle.fill.opacity", 255);

        strokeWidth = (float) Defs.doubleProperty("org.icepdf.core.views.page.annotation.circle.stroke.width",
                DEFAULT_STROKE_WIDTH);

        // need to make the stroke cap, thickness configurable. Or potentially
        // static from the AnnotationHandle so it would look like the last
        // settings where remembered.
        stroke = new BasicStroke(strokeWidth,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER,
                1.0f);
    }

    public CircleAnnotationHandler(DocumentViewController documentViewController,
                                   AbstractPageViewComponent pageViewComponent,
                                   DocumentViewModel documentViewModel) {
        super(documentViewController, pageViewComponent, documentViewModel);

        checkAndApplyPreferences();
    }

    /**
     * Paint a rough circle representing what the annotation will look like
     * when created.
     *
     * @param g graphics context
     */
    public void paintTool(Graphics g) {
        if (rectangle != null) {

            Ellipse2D.Double circle = new Ellipse2D.Double(
                    rectangle.getMinX(),
                    rectangle.getMinY(),
                    rectangle.getWidth(),
                    rectangle.getHeight());

            Graphics2D gg = (Graphics2D) g;
            Color oldColor = gg.getColor();
            Stroke oldStroke = gg.getStroke();
            gg.setStroke(stroke);
            gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, defaultOpacity / 255.0f));
            if (useInternalColor) {
                gg.setColor(internalColor);
                gg.fill(circle);
            }
            gg.setColor(lineColor);
            gg.draw(circle);
            g.setColor(oldColor);
            gg.setStroke(oldStroke);
        }
    }

    /**
     * Create the annotation objects need to draw and manipulated the annotation
     * using the GUI properties panels.
     *
     * @param e mouse event.
     */
    public void mouseReleased(MouseEvent e) {
        updateSelectionSize(e.getX(),e.getY(), pageViewComponent);

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
        annotation = (CircleAnnotation)
                AnnotationFactory.buildAnnotation(
                        documentViewModel.getDocument().getPageTree().getLibrary(),
                        Annotation.SUBTYPE_CIRCLE,
                        tBbox);

        checkAndApplyPreferences();

        annotation.setColor(lineColor);
        annotation.setOpacity(defaultOpacity);
        if (annotation.isFillColor() || useInternalColor) {
            annotation.setFillColor(internalColor);
            if (!annotation.isFillColor()) {
                annotation.setFillColor(true);
            }
        }

        annotation.setRectangle(rectangle);
        annotation.setBorderStyle(borderStyle);

        // pass outline shapes and bounds to create the highlight shapes
        annotation.setBBox(new Rectangle(0, 0, tBbox.width, tBbox.height));
        annotation.resetAppearanceStream(getPageTransform());

        // create the annotation object.
        AbstractAnnotationComponent comp =
                AnnotationComponentFactory.buildAnnotationComponent(
                        annotation,
                        documentViewController,
                        pageViewComponent, documentViewModel);
        // set the bounds and refresh the userSpace rectangle
        Rectangle bbox = new Rectangle(rectToDraw.x, rectToDraw.y,
                rectToDraw.width, rectToDraw.height);
        comp.setBounds(bbox);
        // resets user space rectangle to match bbox converted to page space
        comp.refreshAnnotationRect();

        // add them to the container, using absolute positioning.
        if (documentViewController.getAnnotationCallback() != null) {
            AnnotationCallback annotationCallback =
                    documentViewController.getAnnotationCallback();
            annotationCallback.newAnnotation(pageViewComponent, comp);
        }

        // set the annotation tool to he select tool
        if (preferences.getBoolean(PropertiesManager.PROPERTY_ANNOTATION_CIRCLE_SELECTION_ENABLED, false)) {
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
                PropertiesManager.PROPERTY_ANNOTATION_CIRCLE_OPACITY, defaultOpacity);
        lineColor = new Color(preferences.getInt(
                PropertiesManager.PROPERTY_ANNOTATION_CIRCLE_COLOR, lineColor.getRGB()));
        internalColor = new Color(preferences.getInt(
                PropertiesManager.PROPERTY_ANNOTATION_CIRCLE_FILL_COLOR, internalColor.getRGB()));
    }
}
