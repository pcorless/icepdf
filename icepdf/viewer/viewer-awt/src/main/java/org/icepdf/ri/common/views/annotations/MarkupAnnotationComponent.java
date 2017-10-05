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

import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.PopupAnnotation;
import org.icepdf.core.util.Defs;
import org.icepdf.ri.common.tools.TextAnnotationHandler;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.AnnotationComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MarkupAnnotationComponent class encapsulates the component functionality
 * needed to display an MarkupAnnotations PopupAnnnotaion component. When
 * a MarkupAnnotationComponent is double clicked is child PopupAnnotation component
 * will be displayed.
 *
 * @see CircleAnnotationComponent
 * @see FreeTextAnnotationComponent
 * @see InkAnnotationComponent
 * @see LineAnnotationComponent
 * @see LinkAnnotationComponent
 * @see PolygonAnnotationComponent
 * @see PolyLineAnnotationComponent
 * @see SquareAnnotationComponent
 * @see TextAnnotationComponent
 * @see TextMarkupAnnotationComponent
 * @since 5.0
 */
@SuppressWarnings("serial")
public abstract class MarkupAnnotationComponent extends AbstractAnnotationComponent<MarkupAnnotation> {

    protected static final Logger logger =
            Logger.getLogger(MarkupAnnotationComponent.class.toString());

    protected static boolean isInteractivePopupAnnotationsEnabled;

    static {
        isInteractivePopupAnnotationsEnabled =
                Defs.sysPropertyBoolean(
                        "org.icepdf.core.annotations.interactive.popup.enabled", true);
    }

    public MarkupAnnotationComponent(MarkupAnnotation annotation,
                                     DocumentViewController documentViewController,
                                     AbstractPageViewComponent pageViewComponent,
                                     DocumentViewModel documentViewModel) {
        super(annotation, documentViewController, pageViewComponent, documentViewModel);

        // command test
        buildContextMenu();
    }

    public void buildContextMenu() {
        //Create the popup menu.
        contextMenu = new MarkupAnnotationPopupMenu(this, documentViewController,
                getPageViewComponent(), documentViewModel, true);
        // Add listener to components that can bring up popup menus.
        MouseListener popupListener = new PopupListener(contextMenu);
        addMouseListener(popupListener);
    }

    @Override
    public void resetAppearanceShapes() {
        // our only purpose is to update the popup annotation color.
        if (annotation != null) {
            PopupAnnotation popup = annotation.getPopupAnnotation();
            if (popup != null) {
                // toggle the visibility of the popup
                popup.setOpen(!popup.isOpen());
                // find the popup component
                ArrayList<AbstractAnnotationComponent> annotationComponents =
                        pageViewComponent.getAnnotationComponents();
                Reference compReference;
                Reference popupReference = popup.getPObjectReference();
                for (AnnotationComponent annotationComponent : annotationComponents) {
                    compReference = annotationComponent.getAnnotation().getPObjectReference();
                    // find the component and toggle it's visibility, null check just encase compRef is direct.
                    if (compReference != null && compReference.equals(popupReference)) {
                        if (annotationComponent instanceof PopupAnnotationComponent) {
                            PopupAnnotationComponent popupComponent = ((PopupAnnotationComponent) annotationComponent);
                            popupComponent.resetAppearanceShapes();
                        }
                    }
                }
            }
        }
    }

    public PopupAnnotationComponent getPopupAnnotationComponent() {
        if (annotation != null) {
            PopupAnnotation popup = annotation.getPopupAnnotation();
            if (popup == null) {
                PopupAnnotationComponent popupAnnotationComponent = createPopupAnnotationComponent(false);
                annotation.setPopupAnnotation((PopupAnnotation) popupAnnotationComponent.getAnnotation());
                return popupAnnotationComponent;
            }

            if (popup != null) {
                // find the popup component
                ArrayList<AbstractAnnotationComponent> annotationComponents = pageViewComponent.getAnnotationComponents();
                Reference compReference;
                Reference popupReference = popup.getPObjectReference();
                for (AnnotationComponent annotationComponent : annotationComponents) {
                    compReference = annotationComponent.getAnnotation().getPObjectReference();
                    // find the component and toggle it's visibility, null check just encase compRef is direct.
                    if (compReference != null && compReference.equals(popupReference)) {
                        if (annotationComponent instanceof PopupAnnotationComponent) {
                            PopupAnnotationComponent popupComponent = ((PopupAnnotationComponent) annotationComponent);
                            return popupComponent;
                        }
                        break;
                    }
                }
//                // we didn't fin a component so we'll create one.
//                PopupAnnotationComponent popupAnnotationComponent = createPopupAnnotationComponent(true);
//                annotation.setPopupAnnotation(popup);
//                annotationComponents.add(popupAnnotationComponent);
//                return popupAnnotationComponent;
            }
        }
        return null;
    }

    public PopupAnnotationComponent createPopupAnnotationComponent(boolean isNew) {
        // convert bbox and start and end line points.
        Rectangle bounds = this.getBounds();
        Rectangle bBox = new Rectangle(bounds.x, bounds.y, PopupAnnotationComponent.DEFAULT_WIDTH,
                PopupAnnotationComponent.DEFAULT_HEIGHT);

        Rectangle tBbox = convertToPageSpace(bBox).getBounds();

        // we may have an new markup or one that just didn't have a popup.
        if (annotation != null && isNew) {
            if (annotation.getCreationDate() == null) {
                annotation.setCreationDate(PDate.formatDateTime(new Date()));
            }
            if (annotation.getTitleText() == null) {
                annotation.setTitleText(System.getProperty("user.name"));
            }
            if (annotation.getContents() == null) {
                annotation.setContents("");
            }
        }
        PopupAnnotation popupAnnotation;
        if (annotation.getPopupAnnotation() == null) {
            popupAnnotation = TextAnnotationHandler.createPopupAnnotation(
                    documentViewModel.getDocument().getPageTree().getLibrary(),
                    tBbox, annotation, getPageTransform());
            annotation.setPopupAnnotation(popupAnnotation);
        } else {
            popupAnnotation = annotation.getPopupAnnotation();
        }


        // create the annotation object.
        PopupAnnotationComponent comp = (PopupAnnotationComponent)
                AnnotationComponentFactory.buildAnnotationComponent(
                        popupAnnotation,
                        documentViewController,
                        pageViewComponent, documentViewModel);
        // set the bounds and refresh the userSpace rectangle
        comp.setBounds(bBox);
        // resets user space rectangle to match bbox converted to page space
        comp.refreshAnnotationRect();

        // add them to the container, using absolute positioning.
        documentViewController.addNewAnnotation(comp);
        pageViewComponent.revalidate();
        return comp;
    }


    @Override
    public void mouseClicked(MouseEvent e) {
        super.mouseClicked(e);
        // on double click toggle the visibility of the popup component.
        if (isInteractivePopupAnnotationsEnabled && e.getClickCount() == 2) {
            // we have an annotation so toggle it's visibility
            togglePopupAnnotationVisibility();
        }
    }

    public void togglePopupAnnotationVisibility() {
        if (annotation != null) {
            PopupAnnotation popup = annotation.getPopupAnnotation();
            if (popup != null) {
                popup.setOpen(!popup.isOpen());
                PopupAnnotationComponent popupComponent = getPopupAnnotationComponent();
                popupComponent.setVisible(popup.isOpen());
                // make sure the popup is drawn on the page and
                // not outside the page clip.
                Rectangle popupBounds = popupComponent.getBounds();
                Rectangle pageBounds = pageViewComponent.getBounds();
                if (!pageBounds.contains(popupBounds.getX(), popupBounds.getY(),
                        popupBounds.getWidth(), popupBounds.getHeight())) {
                    int x = popupBounds.x;
                    int y = popupBounds.y;
                    if (x + popupBounds.width > pageBounds.width) {
                        x = x - (popupBounds.width - (pageBounds.width - popupBounds.x));
                    }
                    if (y + popupBounds.height > pageBounds.height) {
                        y = y - (popupBounds.height - (pageBounds.height - popupBounds.y));
                    }
                    popupBounds.setLocation(x, y);
                    popupComponent.setBounds(popupBounds);
                }
            }
            // no markupAnnotation so we need to create one and display for
            // the addition comments.
            else {
                // convert bbox and start and end line points.
                createPopupAnnotationComponent(true);
            }
        }
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
        } catch (NoninvertibleTransformException e) {
            logger.log(Level.FINE, "Error converting to page space.", e);
        }

        shape = at.createTransformedShape(shape);

        return shape;

    }

    public boolean isActive() {
        return false;
    }

}
