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

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.BorderStyle;
import org.icepdf.core.pobjects.annotations.LineAnnotation;
import org.icepdf.core.util.ColorUtil;
import org.icepdf.core.util.Defs;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.AnnotationCallback;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;
import org.icepdf.ri.common.views.annotations.AnnotationComponentFactory;
import org.icepdf.ri.common.views.annotations.MarkupAnnotationComponent;
import org.icepdf.ri.common.views.annotations.PopupAnnotationComponent;
import org.icepdf.ri.util.PropertiesManager;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LineAnnotationHandler tool is responsible for painting representation of
 * a line on the screen during a click and drag mouse event.  The first point
 * is recorded on mousePressed and the line is drawn from first point the current
 * location of the mouse.
 * <br>
 * Once the mouseReleased event is fired this handler will create new
 * LineAnnotation and respective AnnotationComponent.  The addition of the
 * Annotation object to the page is handled by the annotation callback.
 *
 * @since 5.0
 */
public class LineAnnotationHandler extends SelectionBoxHandler implements ToolHandler {


    private static final Logger logger =
            Logger.getLogger(LineAnnotationHandler.class.toString());

    // need to make the stroke cap, thickness configurable. Or potentially
    // static from the lineAnnotationHandle so it would look like the last
    // settings where remembered.
    protected static BasicStroke stroke = new BasicStroke(1.0f,
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER,
            1.0f);

    protected static Color lineColor;
    protected static Color internalColor;
    protected static int opacity;

    static {

        // sets annotation link stroke colour
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.line.stroke.color", "#ff0000");
            int colorValue = ColorUtil.convertColor(color);
            lineColor =
                    new Color(colorValue >= 0 ? colorValue :
                            Integer.parseInt("ff0000", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading line Annotation stroke colour");
            }
        }

        // sets annotation link fill colour
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.line.fill.color", "#ff0000");
            int colorValue = ColorUtil.convertColor(color);
            internalColor =
                    new Color(colorValue >= 0 ? colorValue :
                            Integer.parseInt("ff0000", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading line Annotation fill colour");
            }
        }
        // sets annotation opacity
        opacity = Defs.intProperty(
                "org.icepdf.core.views.page.annotation.line.fill.opacity", 255);
    }

    protected static Name startLineEnding = LineAnnotation.LINE_END_NONE;
    protected static Name endLineEnding = LineAnnotation.LINE_END_NONE;

    // start and end point
    protected Point startOfLine;
    protected Point endOfLine;

    protected BorderStyle borderStyle = new BorderStyle();

    /**
     * New Text selection handler.  Make sure to correctly and and remove
     * this mouse and text listeners.
     *
     * @param pageViewComponent page component that this handler is bound to.
     * @param documentViewController view controller.
     */
    public LineAnnotationHandler(DocumentViewController documentViewController,
                                 AbstractPageViewComponent pageViewComponent) {
        super(documentViewController, pageViewComponent);
        startLineEnding = LineAnnotation.LINE_END_NONE;
        endLineEnding = LineAnnotation.LINE_END_NONE;

        checkAndApplyPreferences();
    }

    public void paintTool(Graphics g) {
        if (startOfLine != null && endOfLine != null) {
            Graphics2D gg = (Graphics2D) g;
            Color oldColor = gg.getColor();
            Stroke oldStroke = gg.getStroke();
            g.setColor(lineColor);
            gg.setStroke(stroke);
            gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity / 255.0f));
            g.drawLine((int) startOfLine.getX(), (int) startOfLine.getY(),
                    (int) endOfLine.getX(), (int) endOfLine.getY());
            g.setColor(oldColor);
            gg.setStroke(oldStroke);
        }
    }

    public void mousePressed(MouseEvent e) {
        Point startPoint = e.getPoint();
        startOfLine = new Point(startPoint.x, startPoint.y);
        // annotation selection box.
        int x = e.getX();
        int y = e.getY();
        currentRect = new Rectangle(x, y, 0, 0);
        updateDrawableRect(pageViewComponent.getWidth(),
                pageViewComponent.getHeight());
        pageViewComponent.repaint();
    }

    public void mouseReleased(MouseEvent e) {
        Point startPoint = e.getPoint();
        endOfLine = new Point(startPoint.x, startPoint.y);
        updateSelectionSize(e.getX(),e.getY(), pageViewComponent);

        // add a little padding or the end point icon types
        rectToDraw.setRect(rectToDraw.getX() - 8, rectToDraw.getY() - 8,
                rectToDraw.getWidth() + 16, rectToDraw.getHeight() + 16);

        // convert bbox and start and end line points.
        Rectangle tBbox = convertToPageSpace(rectToDraw).getBounds();
        // convert start of line and end of line to page space
        Point2D[] points = convertToPageSpace(startOfLine, endOfLine);

        // create annotations types that  are rectangle based;
        LineAnnotation annotation = (LineAnnotation)
                AnnotationFactory.buildAnnotation(
                        documentViewController.getDocument().getPageTree().getLibrary(),
                        Annotation.SUBTYPE_LINE,
                        tBbox);
        annotation.setStartArrow(startLineEnding);
        annotation.setEndArrow(endLineEnding);
        annotation.setStartOfLine(points[0]);
        annotation.setEndOfLine(points[1]);
        annotation.setBorderStyle(borderStyle);

        // apply preferences
        checkAndApplyPreferences();

        annotation.setColor(lineColor);
        annotation.setInteriorColor(internalColor);
        annotation.setOpacity(opacity);

        AffineTransform pageTransform = getToPageSpaceTransform();

        // setup the markup properties.
        annotation.setContents(annotation.getSubType().toString());
        annotation.setCreationDate(PDate.formatDateTime(new Date()));
        annotation.setTitleText(System.getProperty("user.name"));

        // pass outline shapes and bounds to create the highlight shapes
        annotation.setBBox(tBbox);
        annotation.resetAppearanceStream(pageTransform);

        // create the annotation object.
        MarkupAnnotationComponent comp = (MarkupAnnotationComponent)
                AnnotationComponentFactory.buildAnnotationComponent(
                        annotation, documentViewController, pageViewComponent);
        // set the bounds and refresh the userSpace rectangle
        Rectangle bbox = new Rectangle(rectToDraw.x, rectToDraw.y,
                rectToDraw.width, rectToDraw.height);
        comp.setBounds(bbox);

        // add them to the container, using absolute positioning.
        if (documentViewController.getAnnotationCallback() != null) {
            AnnotationCallback annotationCallback =
                    documentViewController.getAnnotationCallback();
            documentViewController.addNewAnnotation(comp);
        }

        // associate popup to location
        PopupAnnotationComponent popupAnnotationComponent = comp.getPopupAnnotationComponent();
        popupAnnotationComponent.setBoudsRelativeToParent(
                bbox.x + (bbox.width / 2), bbox.y + (bbox.height / 2), pageTransform);
        popupAnnotationComponent.setVisible(false);

        // set the annotation tool to he select tool
        if (preferences.getBoolean(PropertiesManager.PROPERTY_ANNOTATION_LINE_SELECTION_ENABLED, false)) {
            documentViewController.getParentController().setDocumentToolMode(
                    DocumentViewModel.DISPLAY_TOOL_SELECTION);
        }

        // clear the rectangle
        clearRectangle(pageViewComponent);
        startOfLine = endOfLine = null;
    }

    protected void checkAndApplyPreferences() {
        lineColor = new Color(preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_LINE_COLOR, lineColor.getRGB()));
        internalColor = new Color(preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_LINE_FILL_COLOR,
                internalColor.getRGB()));
        opacity = preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_LINE_OPACITY, opacity);
    }

    public void mouseDragged(MouseEvent e) {
        updateSelectionSize(e.getX(),e.getY(), pageViewComponent);
        Point startPoint = e.getPoint();
        endOfLine = new Point(startPoint.x, startPoint.y);
        pageViewComponent.repaint();
    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseClicked(MouseEvent e) {
        if (pageViewComponent != null) {
            pageViewComponent.requestFocus();
        }
    }

    public void mouseExited(MouseEvent e) {

    }

    public void mouseMoved(MouseEvent e) {

    }

    public void installTool() {

    }

    public void uninstallTool() {

    }

    @Override
    public void setSelectionRectangle(Point cursorLocation, Rectangle selection) {

    }
}
