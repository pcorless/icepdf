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
import org.icepdf.core.pobjects.annotations.FreeTextAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;
import org.icepdf.ri.common.views.annotations.AbstractAnnotationComponent;
import org.icepdf.ri.common.views.annotations.AnnotationComponentFactory;
import org.icepdf.ri.common.views.annotations.FreeTextAnnotationComponent;
import org.icepdf.ri.util.PropertiesManager;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.Date;
import java.util.logging.Logger;

import static org.icepdf.core.pobjects.annotations.FreeTextAnnotation.INSETS;

/**
 * FreeTextAnnotationHandler tool is responsible for painting representation of
 * a FreeTextAnnotationHandler on the screen during a click and drag mouse event.
 * The box created by this mouse event will be used be used as the bounding
 * box of the annotation that will be created.
 * <br>
 * Once the mouseReleased event is fired this handler will create new
 * FreeTextAnnotationHandler and respective AnnotationComponent.  The addition
 * of the Annotation object to the page is handled by the annotation callback.
 *
 * @since 5.0
 */
public class FreeTextAnnotationHandler extends SelectionBoxHandler
        implements ToolHandler {

    private static final Logger logger =
            Logger.getLogger(LineAnnotationHandler.class.toString());

    public static final int DEFAULT_WIDTH = 30;
    public static final int DEFAULT_HEIGHT = 30;

    private FreeTextAnnotation annotation;

    /**
     * New Text selection handler.  Make sure to correctly and and remove
     * this mouse and text listeners.
     *
     * @param pageViewComponent      page component that this handler is bound to.
     * @param documentViewController view controller.
     */
    public FreeTextAnnotationHandler(DocumentViewController documentViewController,
                                     AbstractPageViewComponent pageViewComponent) {
        super(documentViewController, pageViewComponent);
    }

    @Override
    public void setSelectionRectangle(Point cursorLocation, Rectangle selection) {

    }

    public void paintTool(Graphics g) {
        paintSelectionBox(g, rectToDraw);
    }

    public void mouseClicked(MouseEvent e) {
        if (pageViewComponent != null) {
            pageViewComponent.requestFocus();
        }
    }

    public void mousePressed(MouseEvent e) {

    }

    public void mouseReleased(MouseEvent e) {
        updateSelectionSize(e.getX(), e.getY(), pageViewComponent);

        // check the bounds on rectToDraw to try and avoid creating
        // an annotation that is very small.  This occurs when some clicks rather then drags.
        if (rectToDraw.getWidth() < 5 || rectToDraw.getHeight() < 5) {
            rectToDraw.setSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
            rectToDraw.setLocation(rectToDraw.x, rectToDraw.y - DEFAULT_HEIGHT + INSETS);
        }

        // create a fixed sized box based on the default font size.
        Rectangle tBbox = convertToPageSpace(rectToDraw).getBounds();

        // create annotations types that that are rectangle based;
        // which is actually just link annotations
        annotation = (FreeTextAnnotation)
                AnnotationFactory.buildAnnotation(
                        documentViewController.getDocument().getPageTree().getLibrary(),
                        Annotation.SUBTYPE_FREE_TEXT,
                        tBbox);
        annotation.setCreationDate(PDate.formatDateTime(new Date()));
        annotation.setTitleText(System.getProperty("user.name"));
        annotation.setContents("");

        // apply store settings
        checkAndApplyPreferences();

        AffineTransform pageTransform = getToPageSpaceTransform();
        annotation.resetAppearanceStream(pageTransform);

        // create the annotation object.
        AbstractAnnotationComponent comp =
                AnnotationComponentFactory.buildAnnotationComponent(
                        annotation, documentViewController, pageViewComponent);
        // set the bounds and refresh the userSpace rectangle
        comp.setBounds(rectToDraw);
        // resets user space rectangle to match bbox converted to page space
        comp.refreshAnnotationRect();

        // add them to the container, using absolute positioning.
        documentViewController.addNewAnnotation(comp);

        // set the annotation tool to he select tool
        if (preferences.getBoolean(PropertiesManager.PROPERTY_ANNOTATION_FREE_TEXT_SELECTION_ENABLED, false)) {
            documentViewController.getParentController().setDocumentToolMode(DocumentViewModel.DISPLAY_TOOL_SELECTION);
        }

        // request focus so that editing can take place.
        ((FreeTextAnnotationComponent) comp).requestTextAreaFocus();

    }

    protected void checkAndApplyPreferences() {
        // apply free text colour
        if (preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_FREE_TEXT_COLOR, -1) != -1) {
            int rgb = preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_FREE_TEXT_COLOR, 0);
            annotation.setFontColor(new Color(rgb));
        }
        // apply fill colour
        if (preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_FREE_TEXT_FILL_COLOR, -1) != -1) {
            int rgb = preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_FREE_TEXT_FILL_COLOR, 0);
            annotation.setFillColor(new Color(rgb));
        }
        // apply border colour
        if (preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_FREE_TEXT_BORDER_COLOR, -1) != -1) {
            int rgb = preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_FREE_TEXT_BORDER_COLOR, 0);
            annotation.setColor(new Color(rgb));
        }
        // font
        String fontName = preferences.get(PropertiesManager.PROPERTY_ANNOTATION_FREE_TEXT_FONT, "Helvetica");
        annotation.setFontName(fontName);
        // apply font size
        int fontSize = preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_FREE_TEXT_SIZE, 24);
        annotation.setFontSize(fontSize);
        // opacity
        int opacity = preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_FREE_TEXT_OPACITY, 255);
        annotation.setOpacity(opacity);

    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    public void mouseDragged(MouseEvent e) {
        updateSelectionSize(e.getX(), e.getY(), pageViewComponent);
    }

    public void mouseMoved(MouseEvent e) {

    }

    public void installTool() {

    }

    public void uninstallTool() {

    }

}
