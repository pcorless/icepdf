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
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.annotations.*;
import org.icepdf.core.util.ColorUtil;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.Library;
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
 * TextAnnotationHandler tool is responsible creating a new comment type
 * TextAnnotation when a mouse click event is thrown on the page.  The new
 * TextAnnotation is placed at the point of the page where the click took place.
 * The default icon state is set to comment and the respective PopupAnnotation
 * is also created and shown.
 * <br>
 * The addition of the
 * Annotation object to the page is handled by the annotation callback.
 *
 * @since 5.0
 */
public class TextAnnotationHandler extends CommonToolHandler implements ToolHandler {

    private static final Logger logger =
            Logger.getLogger(TextAnnotationHandler.class.toString());

    protected static Color defaultFillColor;
    protected static String defaultIcon;
    protected static int defaultOpacity;

    static {

        // sets annotation text fill colour
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.text.fill.color", "#ffff00");
            int colorValue = ColorUtil.convertColor(color);
            defaultFillColor =
                    new Color(colorValue >= 0 ? colorValue :
                            Integer.parseInt("ffff00", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading text annotation fill colour");
            }
        }

        // sets annotation opacity
        defaultOpacity = Defs.intProperty(
                "org.icepdf.core.views.page.annotation.text.fill.opacity", 255);


        // sets annotation text fill colour
        defaultIcon = Defs.sysProperty("org.icepdf.core.views.page.annotation.text.icon",
                TextAnnotation.COMMENT_ICON.toString());
    }

    protected static final Dimension ICON_SIZE = new Dimension(23, 23);

    public TextAnnotationHandler(DocumentViewController documentViewController,
                                 AbstractPageViewComponent pageViewComponent,
                                 DocumentViewModel documentViewModel) {
        super(documentViewController, pageViewComponent, documentViewModel);
        checkAndApplyPreferences();
    }

    public void paintTool(Graphics g) {

    }

    public void mouseClicked(MouseEvent e) {
        if (pageViewComponent != null) {
            pageViewComponent.requestFocus();
        }
    }

    public void mousePressed(MouseEvent e) {

    }

    public static TextAnnotation createTextAnnotation(Library library, Rectangle bbox,
                                                      AffineTransform pageSpace) {
        TextAnnotation textAnnotation = (TextAnnotation)
                AnnotationFactory.buildAnnotation(
                        library,
                        Annotation.SUBTYPE_TEXT,
                        bbox);
        textAnnotation.setCreationDate(PDate.formatDateTime(new Date()));
        textAnnotation.setTitleText(System.getProperty("user.name"));
        textAnnotation.setContents("");


        textAnnotation.setIconName(new Name(defaultIcon));
        textAnnotation.setColor(defaultFillColor);
        textAnnotation.setOpacity(defaultOpacity);
        textAnnotation.setState(TextAnnotation.STATE_UNMARKED);

        // set the content stream
        textAnnotation.setBBox(new Rectangle(0, 0, bbox.width, bbox.height));
        textAnnotation.resetAppearanceStream(pageSpace);

        return textAnnotation;
    }

    public TextAnnotation createTextAnnotationInstance(Library library, Rectangle bbox,
                                                       AffineTransform pageSpace) {

        TextAnnotation textAnnotation = TextAnnotationHandler.createTextAnnotation(library, bbox, pageSpace);

        // setup some default state
        checkAndApplyPreferences();

        textAnnotation.setIconName(new Name(defaultIcon));
        textAnnotation.setColor(defaultFillColor);
        textAnnotation.setOpacity(defaultOpacity);

        textAnnotation.resetAppearanceStream(pageSpace);

        return textAnnotation;
    }

    public static PopupAnnotation createPopupAnnotation(Library library, Rectangle bbox,
                                                        MarkupAnnotation parent,
                                                        AffineTransform pageSpace) {
        // text annotation are special as the annotation has fixed size.
        PopupAnnotation popupAnnotation = (PopupAnnotation)
                AnnotationFactory.buildAnnotation(
                        library,
                        Annotation.SUBTYPE_POPUP,
                        bbox);
        // save the annotation
        StateManager stateManager = library.getStateManager();
        stateManager.addChange(new PObject(popupAnnotation,
                popupAnnotation.getPObjectReference()));
        library.addObject(popupAnnotation, popupAnnotation.getPObjectReference());

        // setup up some default values
        popupAnnotation.setOpen(true);
        popupAnnotation.setParent(parent);
        parent.setPopupAnnotation(popupAnnotation);
        popupAnnotation.resetAppearanceStream(0, 0, pageSpace);
        return popupAnnotation;
    }

    public void mouseReleased(MouseEvent e) {

        AffineTransform pageTransform = getPageTransformInverse();
        AffineTransform pageInverseTransform = getPageTransform();
        Dimension scaledSize = new Dimension(
                (int) Math.abs(ICON_SIZE.width * pageInverseTransform.getScaleX()),
                (int) Math.abs(ICON_SIZE.height * pageInverseTransform.getScaleY()));

        // convert bbox and start and end line points.
        Rectangle bBox = new Rectangle(e.getX(), e.getY(), scaledSize.width, scaledSize.height);
        Rectangle tBbox = convertToPageSpace(bBox).getBounds();

        // text annotation are special as the annotation has fixed size.
        TextAnnotation markupAnnotation =
                createTextAnnotationInstance(documentViewModel.getDocument().getPageTree().getLibrary(),
                        tBbox, pageTransform);

        // create the annotation object.
        MarkupAnnotationComponent comp = (MarkupAnnotationComponent)
                AnnotationComponentFactory.buildAnnotationComponent(
                        markupAnnotation,
                        documentViewController,
                        pageViewComponent, documentViewModel);
        // set the bounds and refresh the userSpace rectangle
        comp.setBounds(bBox);
        // resets user space rectangle to match bbox converted to page space
        comp.refreshAnnotationRect();

        // add them to the container, using absolute positioning.
        documentViewController.addNewAnnotation(comp);

        // setup the popup so that it will show near the annotation.
        PopupAnnotationComponent popupAnnotationComponent = comp.getPopupAnnotationComponent();
        popupAnnotationComponent.setBoudsRelativeToParent(
                e.getX() + scaledSize.width / 2,
                e.getY() + scaledSize.height / 2,
                pageInverseTransform);
        popupAnnotationComponent.setVisible(true);

        // set the annotation tool to he select tool
        if (preferences.getBoolean(PropertiesManager.PROPERTY_ANNOTATION_TEXT_SELECTION_ENABLED, false)) {
            documentViewController.getParentController().setDocumentToolMode(
                    DocumentViewModel.DISPLAY_TOOL_SELECTION);
        }
    }

    protected void checkAndApplyPreferences() {
        if (preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_TEXT_BUTTON_COLOR, -1) != -1) {
            int rgb = preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_TEXT_BUTTON_COLOR, 0);
            defaultFillColor = new Color(rgb);
        }

        if (defaultFillColor == null) {
            if (preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_TEXT_COLOR, -1) != -1) {
                defaultFillColor = new Color(preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_TEXT_COLOR, -1));
            }
        }
        defaultIcon = preferences.get(
                PropertiesManager.PROPERTY_ANNOTATION_TEXT_ICON, TextAnnotation.COMMENT_ICON.toString());
        defaultOpacity = preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_TEXT_OPACITY, 255);
    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    public void mouseDragged(MouseEvent e) {

    }

    public void mouseMoved(MouseEvent e) {

    }

    public void installTool() {

    }

    public void uninstallTool() {

    }
}
