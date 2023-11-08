/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.PopupAnnotation;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.SystemProperties;
import org.icepdf.ri.common.tools.TextAnnotationHandler;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.AnnotationComponent;
import org.icepdf.ri.common.views.DocumentViewController;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Date;
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
public abstract class MarkupAnnotationComponent<T extends MarkupAnnotation> extends AbstractAnnotationComponent<T> {

    protected static final Logger logger =
            Logger.getLogger(MarkupAnnotationComponent.class.toString());

    protected static final boolean isInteractivePopupAnnotationsEnabled;

    static {
        isInteractivePopupAnnotationsEnabled =
                Defs.sysPropertyBoolean(
                        "org.icepdf.core.annotations.interactive.popup.enabled", true);
    }

    public MarkupAnnotationComponent(T annotation,
                                     DocumentViewController documentViewController,
                                     AbstractPageViewComponent pageViewComponent) {
        super(annotation, documentViewController, pageViewComponent);

        // command test
        buildContextMenu();
    }

    public void buildContextMenu() {
        //Create the popup menu.
        contextMenu = new MarkupAnnotationPopupMenu(this,
                documentViewController.getParentController(),
                getPageViewComponent(), true);
        // Add listener to components that can bring up popup menus.
        MouseListener popupListener = new PopupListener(contextMenu);
        addMouseListener(popupListener);
    }

    @Override
    public void resetAppearanceShapes() {
        // our only purpose is to update the popup annotation color and the color menu.
        if (annotation != null) {
            PopupAnnotation popup = annotation.getPopupAnnotation();
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
                            popupComponent.resetAppearanceShapes();
                        }
                    }
                }
            }
            ((MarkupAnnotationPopupMenu) contextMenu).refreshColorMenu();
        }
    }

    public PopupAnnotationComponent getPopupAnnotationComponent() {
        if (annotation != null) {
            PopupAnnotation popup = annotation.getPopupAnnotation();
            if (popup == null) {
                PopupAnnotationComponent popupAnnotationComponent = createPopupAnnotationComponent(false);
                annotation.setPopupAnnotation(popupAnnotationComponent.getAnnotation());
                popupAnnotationComponent.getAnnotation().setOpen(false);
                popupAnnotationComponent.setVisible(false);
                return popupAnnotationComponent;
            }

            // find the popup component
            ArrayList<AbstractAnnotationComponent> annotationComponents = pageViewComponent.getAnnotationComponents();
            Reference compReference;
            Reference popupReference = popup.getPObjectReference();
            for (AnnotationComponent annotationComponent : annotationComponents) {
                compReference = annotationComponent.getAnnotation().getPObjectReference();
                // find the component and toggle it's visibility, null check just encase compRef is direct.
                if (compReference != null && compReference.equals(popupReference)) {
                    if (annotationComponent instanceof PopupAnnotationComponent) {
                        return ((PopupAnnotationComponent) annotationComponent);
                    }
                    break;
                }
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
                annotation.setTitleText(SystemProperties.USER_NAME);
            }
            if (annotation.getContents() == null) {
                annotation.setContents("");
            }
        }
        PopupAnnotation popupAnnotation = null;
        if (annotation != null && annotation.getPopupAnnotation() == null) {

            popupAnnotation = TextAnnotationHandler.createPopupAnnotation(
                    documentViewController.getDocument().getPageTree().getLibrary(),
                    tBbox, annotation, getToPageSpaceTransform(), isNew);
            annotation.setPopupAnnotation(popupAnnotation);
        } else if (annotation != null) {
            popupAnnotation = annotation.getPopupAnnotation();
        }

        if (popupAnnotation != null) {
            // create the annotation object.
            PopupAnnotationComponent comp = (PopupAnnotationComponent)
                    AnnotationComponentFactory.buildAnnotationComponent(
                            popupAnnotation, documentViewController, pageViewComponent);
            comp.setParentPageComponent(pageViewComponent);
            // set the bounds and refresh the userSpace rectangle

            comp.refreshDirtyBounds();
            // resets user space rectangle to match bbox converted to page space
            comp.refreshAnnotationRect();
            // not new, which means the popup wasn't part of the document, we don't want to save it at this time
            comp.setSynthetic(!isNew);

            // add them to the container, using absolute positioning.
            documentViewController.addNewAnnotation(comp);
            pageViewComponent.revalidate();
            return comp;
        } else {
            return null;
        }
    }

    @Override
    public boolean contains(int x, int y) {
        boolean contains = super.contains(x, y);
        if (contains && annotation != null && annotation.getMarkupPath() != null) {
            // page space
            AffineTransform pageTransform = getPageSpaceTransform();
            Shape shape = annotation.getMarkupPath().createTransformedShape(pageTransform);

            // offset for annotation space
            Rectangle compBounds = getBounds();
            AffineTransform af = new AffineTransform(1, 0, 0, 1, -compBounds.x, -compBounds.y);
            shape = af.createTransformedShape(shape);
            Rectangle rect = shape.getBounds();

            // bail if the markup shape and comp bounds don't line up at all
            if (!rect.intersects(new Rectangle(0, 0, compBounds.width, compBounds.height))) {
                return true;
            }
            boolean subContained = shape.contains(x, y);
            return subContained;
        }
        return contains;
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

    @Override
    public void mouseDragged(MouseEvent me) {
        super.mouseDragged(me);
        PopupAnnotation popup = annotation.getPopupAnnotation();
        if (popup != null) {
            PopupAnnotationComponent popupComponent = getPopupAnnotationComponent();
            if (popupComponent != null) {
                MarkupGlueComponent glueComponent = popupComponent.getMarkupGlueComponent();
                glueComponent.refreshDirtyBounds();
            }
        }
    }

    public void togglePopupAnnotationVisibility() {
        if (annotation != null) {
            PopupAnnotation popup = annotation.getPopupAnnotation();
            if (popup != null) {
                popup.setOpen(!popup.isOpen());
                PopupAnnotationComponent popupComponent = getPopupAnnotationComponent();
                popupComponent.setVisible(popup.isOpen());
                if (popupComponent.isVisible()) {
                    popupComponent.focusTextArea();
                }
            }
            // no markupAnnotation so we need to create one and display for the addition comments.
            else {
                // user initiated change, so we'll mark the popup state as changed and queue it for saving.
                createPopupAnnotationComponent(true);
            }
        }
    }


    public boolean isActive() {
        return false;
    }

}