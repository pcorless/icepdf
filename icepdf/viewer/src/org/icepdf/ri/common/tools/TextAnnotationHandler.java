/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.ri.common.tools;

import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.*;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.AnnotationCallback;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;
import org.icepdf.ri.common.views.annotations.AbstractAnnotationComponent;
import org.icepdf.ri.common.views.annotations.AnnotationComponentFactory;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.util.Date;
import java.util.logging.Logger;

/**
 * TextAnnotationHandler tool is responsible creating a new comment type
 * TextAnnotation when a mouse click event is thrown on the page.  The new
 * TextAnnotation is placed at the point of the page where the click took place.
 * The default icon state is set to comment and the respective PopupAnnotation
 * is also created and shown.
 * <p/>
 * The addition of the
 * Annotation object to the page is handled by the annotation callback.
 *
 * @since 5.0
 */
public class TextAnnotationHandler implements ToolHandler {

    private static final Logger logger =
            Logger.getLogger(TextAnnotationHandler.class.toString());

    protected DocumentViewController documentViewController;
    protected AbstractPageViewComponent pageViewComponent;
    protected DocumentViewModel documentViewModel;

    public TextAnnotationHandler(DocumentViewController documentViewController,
                                 AbstractPageViewComponent pageViewComponent,
                                 DocumentViewModel documentViewModel) {
        this.documentViewController = documentViewController;
        this.pageViewComponent = pageViewComponent;
        this.documentViewModel = documentViewModel;
    }

    public void paintTool(Graphics g) {

    }

    public void mouseClicked(MouseEvent e) {

    }

    public void mousePressed(MouseEvent e) {

    }

    public static TextAnnotation createTextAnnotation(Library library, Rectangle bbox) {
        TextAnnotation markupAnnotation = (TextAnnotation)
                AnnotationFactory.buildAnnotation(
                        library,
                        Annotation.SUBTYPE_TEXT,
                        bbox);
        markupAnnotation.setCreationDate(PDate.createDate(new Date()));
        markupAnnotation.setTitleText(System.getProperty("user.name"));
        markupAnnotation.setContents("");

        // set the content stream
        markupAnnotation.setAppearanceStream(bbox);

        return markupAnnotation;
    }

    public static PopupAnnotation createPopupAnnotation(Library library, Rectangle bbox,
                                                        MarkupAnnotation parent) {
        // text annotation are special as the annotation has fixed size.
        PopupAnnotation popupAnnotation = (PopupAnnotation)
                AnnotationFactory.buildAnnotation(
                        library,
                        Annotation.SUBTYPE_POPUP,
                        bbox);

        // setup up some default values
        popupAnnotation.setOpen(true);
        popupAnnotation.setParent(parent);
        parent.setPopupAnnotation(popupAnnotation);
        return popupAnnotation;
    }

    public void mouseReleased(MouseEvent e) {

        // convert bbox and start and end line points.
        Rectangle bBox = new Rectangle(e.getX(), e.getY(), 20, 20);

        Rectangle tBbox = convertToPageSpace(bBox).getBounds();

        // text annotation are special as the annotation has fixed size.
        TextAnnotation markupAnnotation =
                createTextAnnotation(documentViewModel.getDocument().getPageTree().getLibrary(),
                        tBbox);

        // create the annotation object.
        AbstractAnnotationComponent comp =
                AnnotationComponentFactory.buildAnnotationComponent(
                        markupAnnotation,
                        documentViewController,
                        pageViewComponent, documentViewModel);
        // set the bounds and refresh the userSpace rectangle
        comp.setBounds(bBox);
        // resets user space rectangle to match bbox converted to page space
        comp.refreshAnnotationRect();

        // add them to the container, using absolute positioning.
        if (documentViewController.getAnnotationCallback() != null) {
            AnnotationCallback annotationCallback =
                    documentViewController.getAnnotationCallback();
            annotationCallback.newAnnotation(pageViewComponent, comp);
        }

        /**
         * now create the respective popup annotation
         */

        // convert bbox and start and end line points.
        Rectangle bBox2 = new Rectangle(e.getX(), e.getY(), 215, 150);
        Rectangle tBbox2 = convertToPageSpace(bBox).getBounds();

        // text annotation are special as the annotation has fixed size.
        PopupAnnotation popupAnnotation = createPopupAnnotation(
                documentViewModel.getDocument().getPageTree().getLibrary(),
                tBbox2, markupAnnotation);

        // create the annotation object.
        AbstractAnnotationComponent comp2 = AnnotationComponentFactory.buildAnnotationComponent(
                popupAnnotation,
                documentViewController,
                pageViewComponent, documentViewModel);
        // set the bounds and refresh the userSpace rectangle
        comp2.setBounds(bBox2);
        // resets user space rectangle to match bbox converted to page space
        comp2.refreshAnnotationRect();

        // add them to the container, using absolute positioning.
        if (documentViewController.getAnnotationCallback() != null) {
            AnnotationCallback annotationCallback =
                    documentViewController.getAnnotationCallback();
            annotationCallback.newAnnotation(pageViewComponent, comp2);
        }

        // set the annotation tool to he select tool
        documentViewController.getParentController().setDocumentToolMode(
                DocumentViewModel.DISPLAY_TOOL_SELECTION);
    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    public void mouseDragged(MouseEvent e) {

    }

    public void mouseMoved(MouseEvent e) {

    }

    /**
     * Convert the shapes that make up the annotation to page space so that
     * they will scale correctly at different zooms.
     *
     * @return transformed bbox.
     */
    protected Shape convertToPageSpace(Shape shape) {
        Page currentPage = pageViewComponent.getPage();
        AffineTransform at = currentPage.getPageTransform(
                documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(),
                documentViewModel.getViewZoom());
        try {
            at = at.createInverse();
        } catch (NoninvertibleTransformException e1) {
            e1.printStackTrace();
        }

        shape = at.createTransformedShape(shape);

        return shape;

    }
}
