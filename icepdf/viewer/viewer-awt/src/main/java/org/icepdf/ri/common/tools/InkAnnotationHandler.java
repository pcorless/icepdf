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

import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.BorderStyle;
import org.icepdf.core.pobjects.annotations.InkAnnotation;
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
import java.awt.geom.GeneralPath;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * InkAnnotationHandler tool is responsible for painting representation of
 * a ink on the screen as the mouse is dragged around the page.  The points
 * that make up the mouse path are then used to create the InkAnnotation and
 * respective annotation component.
 * <br>
 * The addition of the Annotation object to the page is handled by the
 * annotation callback.
 *
 * @since 5.0
 */
public class InkAnnotationHandler extends CommonToolHandler implements ToolHandler {

    private static final Logger logger =
            Logger.getLogger(LineAnnotationHandler.class.toString());

    // need to make the stroke cap, thickness configurable. Or potentially
    // static from the lineAnnotationHandle so it would look like the last
    // settings where remembered.
    protected static BasicStroke stroke = new BasicStroke(1.0f,
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER,
            1.0f);

    protected static Color inkColor;
    protected static int opacity;

    static {

        // sets annotation ink line colour
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.ink.line.color", "#00ff00");
            int colorValue = ColorUtil.convertColor(color);
            inkColor =
                    new Color(colorValue >= 0 ? colorValue :
                            Integer.parseInt("00ff00", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading Ink Annotation line colour");
            }
        }
        // sets annotation opacity
        opacity = Defs.intProperty(
                "org.icepdf.core.views.page.annotation.ink.line.opacity", 255);

    }

    // start and end point
    protected GeneralPath inkPath;

    protected BorderStyle borderStyle = new BorderStyle();

    /**
     * New Text selection handler.  Make sure to correctly and and remove
     * this mouse and text listeners.
     *
     * @param pageViewComponent page component that this handler is bound to.
     * @param documentViewModel view model.
     */
    public InkAnnotationHandler(DocumentViewController documentViewController,
                                AbstractPageViewComponent pageViewComponent, DocumentViewModel documentViewModel) {
        super(documentViewController, pageViewComponent, documentViewModel);
        checkAndApplyPreferences();
    }

    public void paintTool(Graphics g) {
        if (inkPath != null) {
            Graphics2D gg = (Graphics2D) g;
            Color oldColor = gg.getColor();
            Stroke oldStroke = gg.getStroke();

            gg.setColor(inkColor);
            gg.setStroke(stroke);
            gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity / 255.0f));
            gg.draw(inkPath);
            gg.setColor(oldColor);
            gg.setStroke(oldStroke);
        }
    }

    public void mouseClicked(MouseEvent e) {
        if (pageViewComponent != null) {
            pageViewComponent.requestFocus();
        }
    }

    public void mousePressed(MouseEvent e) {
        // annotation selection box.
        if (inkPath == null) {
            inkPath = new GeneralPath();
        }
        inkPath.moveTo(e.getX(), e.getY());
        pageViewComponent.repaint();
    }

    protected void checkAndApplyPreferences() {
        inkColor = new Color(preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_INK_COLOR, inkColor.getRGB()));
        opacity = preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_INK_OPACITY, opacity);
    }

    public void mouseReleased(MouseEvent e) {

        inkPath.moveTo(e.getX(), e.getY());

        // convert bbox and start and end line points.
        Rectangle bBox = inkPath.getBounds();
        // check to make sure the bbox isn't zero height or width
        bBox.setRect(bBox.getX() - 5, bBox.getY() - 5,
                bBox.getWidth() + 10, bBox.getHeight() + 10);
        Rectangle tBbox = convertToPageSpace(bBox).getBounds();
        // get the ink path in page space then we need to translate it relative to the bbox.
        Shape tInkPath = convertToPageSpace(inkPath);

        // create annotations types that that are rectangle based;
        // which is actually just link annotations
        InkAnnotation annotation = (InkAnnotation)
                AnnotationFactory.buildAnnotation(
                        documentViewModel.getDocument().getPageTree().getLibrary(),
                        Annotation.SUBTYPE_INK,
                        tBbox);

        checkAndApplyPreferences();
        annotation.setCreationDate(PDate.formatDateTime(new Date()));
        annotation.setTitleText(System.getProperty("user.name"));
        annotation.setColor(inkColor);
        annotation.setBorderStyle(borderStyle);
        annotation.setInkPath(tInkPath);
        annotation.setOpacity(opacity);

        AffineTransform pageTransform = getPageTransformInverse();

        // pass outline shapes and bounds to create the highlight shapes
        annotation.setBBox(tBbox);
        annotation.resetAppearanceStream(pageTransform);

        // create the annotation object.
        MarkupAnnotationComponent comp = (MarkupAnnotationComponent)
                AnnotationComponentFactory.buildAnnotationComponent(
                        annotation,
                        documentViewController,
                        pageViewComponent, documentViewModel);
        // set the bounds and refresh the userSpace rectangle
        comp.setBounds(bBox);

        // add them to the container, using absolute positioning.
        if (documentViewController.getAnnotationCallback() != null) {
            AnnotationCallback annotationCallback =
                    documentViewController.getAnnotationCallback();
            annotationCallback.newAnnotation(pageViewComponent, comp);
        }

        // associate popup to location
        PopupAnnotationComponent popupAnnotationComponent = comp.getPopupAnnotationComponent();
        popupAnnotationComponent.setBoudsRelativeToParent(
                bBox.x + (bBox.width / 2), bBox.y + (bBox.height / 2), pageTransform);
        popupAnnotationComponent.setVisible(false);

        // add them to the container, using absolute positioning.
        if (documentViewController.getAnnotationCallback() != null) {
            AnnotationCallback annotationCallback =
                    documentViewController.getAnnotationCallback();
            annotationCallback.newAnnotation(pageViewComponent, popupAnnotationComponent);
        }

        // set the annotation tool to he select tool
        if (preferences.getBoolean(PropertiesManager.PROPERTY_ANNOTATION_INK_SELECTION_ENABLED, false)) {
            documentViewController.getParentController().setDocumentToolMode(
                    DocumentViewModel.DISPLAY_TOOL_SELECTION);
        }

        // clear the path
        inkPath = null;
    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    public void installTool() {

    }

    public void uninstallTool() {

    }

    public void mouseDragged(MouseEvent e) {
        inkPath.lineTo(e.getX(), e.getY());
        pageViewComponent.repaint();
    }

    public void mouseMoved(MouseEvent e) {

    }

}
