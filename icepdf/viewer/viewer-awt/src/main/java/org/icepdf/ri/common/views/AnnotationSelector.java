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
package org.icepdf.ri.common.views;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.annotations.AbstractAnnotationComponent;

import java.util.ArrayList;

/**
 * Utility for locating an AnnotationComponent on a page and setup up focus within int he context of the
 * DocumentView.
 */
public class AnnotationSelector {

    /**
     * Utility to find a Annotation's JComponent within a AbstractPageComponent implementation.
     *
     * @param controller       swing controller.
     * @param widgetAnnotation annotation to do search for wrapping component.
     * @return true if component could be found, false otherwise.
     */
    public static AnnotationComponent SelectAnnotationComponent(SwingController controller, Annotation widgetAnnotation) {
        // turn out the parent is seldom used correctly and generally just points to page zero.
        // so we need to do a deep search for the annotation.
        Document document = controller.getDocument();
        java.util.List<AbstractPageViewComponent> pageViewComponentList =
                controller.getDocumentViewController().getDocumentViewModel().getPageComponents();
        int pages = controller.getPageTree().getNumberOfPages();
        boolean found = false;
        int pageIndex;
        for (pageIndex = 0; pageIndex < pages; pageIndex++) {
            // check is page's annotation array for a matching reference.
            Page page = document.getPageTree().getPage(pageIndex);
            ArrayList<Reference> annotationReferences = page.getAnnotationReferences();
            if (annotationReferences != null) {
                for (Reference reference : annotationReferences) {
                    if (reference.equals(widgetAnnotation.getPObjectReference())) {
                        widgetAnnotation.setPage(page);
                        // found, so navigate to page which will start the full page load off awt thread.
                        if (controller.getCurrentPageNumber() != pageIndex) {
                            controller.showPage(pageIndex);
                        }
                        found = true;
                        break;
                    }
                }
            }
            if (found) break;
        }
        // the trick now is to init only the pageComponent that contains the clicked on annotation.
        if (found) {
            PageViewComponentImpl pageViewComponent = (PageViewComponentImpl) pageViewComponentList.get(pageIndex);
            ArrayList<AbstractAnnotationComponent> annotationComponents = pageViewComponent.getAnnotationComponents();
            if (annotationComponents != null) {
                for (AbstractAnnotationComponent annotationComponent : annotationComponents) {
                    if (widgetAnnotation.getPObjectReference().equals(
                            annotationComponent.getAnnotation().getPObjectReference())) {
                        annotationComponent.requestFocus();
                        return annotationComponent;
                    }
                }
            }
        }
        return null;
    }

    public static int AssignAnnotationPage(SwingController controller, Annotation widgetAnnotation) {
        Document document = controller.getDocument();
        java.util.List<AbstractPageViewComponent> pageViewComponentList =
                controller.getDocumentViewController().getDocumentViewModel().getPageComponents();
        int pages = controller.getPageTree().getNumberOfPages();
        int pageIndex;
        for (pageIndex = 0; pageIndex < pages; pageIndex++) {
            // check is page's annotation array for a matching reference.
            Page page = document.getPageTree().getPage(pageIndex);
            ArrayList<Reference> annotationReferences = page.getAnnotationReferences();
            if (annotationReferences != null) {
                for (Reference reference : annotationReferences) {
                    if (reference.equals(widgetAnnotation.getPObjectReference())) {
                        widgetAnnotation.setPage(page);
                        return pageIndex;
                    }
                }
            }
        }
        return -1;
    }
}
