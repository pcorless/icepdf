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

import org.icepdf.core.Memento;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.BorderStyle;
import org.icepdf.core.pobjects.annotations.PopupAnnotation;
import org.icepdf.ri.common.views.AnnotationComponent;
import org.icepdf.ri.common.views.PageViewComponentImpl;

import java.awt.*;
import java.awt.geom.Rectangle2D;

import static java.util.Objects.requireNonNull;

/**
 * Stores state parameters for annotation objects to be used in conjunction
 * with a care taker as part of the memento pattern.
 *
 * @since 4.0
 */
public class AnnotationState implements Memento {

    // annotation bounding rectangle in user space.
    private final Rectangle2D.Float userSpaceRectangle;
    private final Operation operation;
    private final AnnotationComponent annotationComponent;
    private final PopupState popupState;

    public enum Operation {
        ADD, DELETE, MOVE
    }

    /**
     * Stores the annotation state associated with the AnnotationComponents
     * annotation object.  When a new instance of this object is created
     * the annotation's properties are saved.
     *
     * @param annotationComponent annotation component whose state will be stored.
     * @param operation           The operation applied to the annotation
     */
    public AnnotationState(final AnnotationComponent annotationComponent, final Operation operation) {
        // reference to component so we can apply the state parameters if
        // restore() is called.
        this.annotationComponent = requireNonNull(annotationComponent);
        this.operation = requireNonNull(operation);
        this.userSpaceRectangle = annotationComponent.getAnnotation().getUserSpaceRectangle();
        this.popupState = annotationComponent instanceof MarkupAnnotationComponent ? new PopupState(((MarkupAnnotationComponent) annotationComponent).getPopupAnnotationComponent()) : null;

    }

    public AnnotationComponent getAnnotationComponent() {
        return annotationComponent;
    }

    public Operation getOperation() {
        return operation;
    }

    /**
     * Restores the AnnotationComponents state to the state stored during the
     * construction of this object.
     */
    public void restore() {
        if (annotationComponent != null &&
                annotationComponent.getAnnotation() != null) {
            // get reference to annotation
            final Annotation annotation = annotationComponent.getAnnotation();

            if (annotation.getBorderStyle() == null) {
                annotation.setBorderStyle(new BorderStyle());
            }

            // apply old user rectangle
            annotation.setUserSpaceRectangle(userSpaceRectangle);

            // update the document with current state.
            synchronizeState();
        }
    }

    public void synchronizeState() {
        // update the document with this change.
        final int pageIndex = annotationComponent.getPageIndex();
        final Document document = annotationComponent.getDocument();
        final Annotation annotation = annotationComponent.getAnnotation();
        final PageTree pageTree = document.getPageTree();
        final Page page = pageTree.getPage(pageIndex);
        if (operation == Operation.ADD) {
            // Special case for an undelete as we need to make the component
            // visible again.

            // mark it as not deleted
            annotation.setDeleted(false);
            // re-add it to the page
            page.addAnnotation(annotation, true);
            // re-add to the page view if needed
            final PageViewComponentImpl pageViewComponent = (PageViewComponentImpl) annotationComponent.getPageViewComponent();
            if (!pageViewComponent.getAnnotationComponents().contains(annotationComponent)) {
                pageViewComponent.addAnnotation(annotationComponent);
            }
            if (annotationComponent instanceof MarkupAnnotationComponent) {
                final PopupAnnotationComponent popupAnnotationComponent =
                        ((MarkupAnnotationComponent<?>) annotationComponent).getPopupAnnotationComponent();
                if (popupAnnotationComponent == null) {
                    ((MarkupAnnotationComponent<?>) annotationComponent).createPopupAnnotationComponent(true);
                } else {
                    if (!pageViewComponent.getAnnotationComponents().contains(popupAnnotationComponent)) {
                        pageViewComponent.addAnnotation(popupAnnotationComponent);
                    }
                    if (popupState != null) {
                        final PopupAnnotation popupAnnotation = popupAnnotationComponent.getAnnotation();
                        popupAnnotation.setOpen(popupState.isVisible());
                        popupAnnotation.setTextAreaFontsize(popupState.getTextAreaFontSize());
                        popupAnnotation.setHeaderLabelsFontSize(popupState.getHeaderTextSize());
                        popupAnnotationComponent.setVisible(popupState.isVisible());
                        popupAnnotationComponent.setTextAreaFontSize(popupState.getTextAreaFontSize());
                        popupAnnotationComponent.setHeaderLabelsFontSize(popupState.getHeaderTextSize());
                    }
                }
            }
            // finally update the pageComponent so we can see it again.
            ((Component) annotationComponent).setVisible(true);
            // refresh bounds for any resizes
        } else if (operation == Operation.DELETE) {
            // Special case for an un-add
            page.deleteAnnotation(annotation);
            ((Component) annotationComponent).setVisible(false);
            if (annotationComponent instanceof MarkupAnnotationComponent) {
                ((MarkupAnnotationComponent<?>) annotationComponent).getPopupAnnotationComponent().setVisible(false);
            }
        } else if (operation == Operation.MOVE) {
            // Simply update the annotation
            page.updateAnnotation(annotation);
            if (annotationComponent instanceof MarkupAnnotationComponent) {
                ((MarkupAnnotationComponent<?>) annotationComponent).getPopupAnnotationComponent()
                        .getMarkupGlueComponent().refreshDirtyBounds();
            }
        }
        annotationComponent.refreshDirtyBounds();
    }


}
