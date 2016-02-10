/*
 * Copyright 2006-2016 ICEsoft Technologies Inc.
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
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.annotations.AbstractAnnotationComponent;

import java.util.ArrayList;

/**
 * Utility for locating an AnnotationComponent on a page and setup up focus within int he context of the
 * DocumentView.
 * todo:  some concurrency issue need some further thought.
 */
public class AnnotationSelector {

    /**
     * Utility to find a Annotation's JComponent within a AbstractPageComponent implementation.
     *
     * @param controller       swing controller.
     * @param widgetAnnotation annotation to do search for wrapping component.
     * @return true if component could be found, false otherwise.
     */
    public static boolean SelectAnnotationComponent(SwingController controller, AbstractWidgetAnnotation widgetAnnotation) {
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
            ArrayList<Reference> annotationReferences = document.getPageTree().getPage(pageIndex).getAnnotationReferences();
            if (annotationReferences != null) {
                for (Reference reference : annotationReferences) {
                    if (reference.equals(widgetAnnotation.getPObjectReference())) {
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
            AbstractPageViewComponent pageViewComponent = pageViewComponentList.get(pageIndex);
            pageViewComponent.init();
            // still need to work our some concurrency issue with regard to page init. as a result
            // annotation Components may be null and user will have ot double click again
            // todo need to figure out a better scheme.
//                            ((PageViewComponentImpl)pageViewComponent).getSynchronousAnnotationComponents();
            ArrayList<AbstractAnnotationComponent> annotationComponents = ((PageViewComponentImpl) pageViewComponent).getAnnotationComponents();
            if (annotationComponents != null) {
                for (AbstractAnnotationComponent annotationComponent : annotationComponents) {
                    if (widgetAnnotation.getPObjectReference().equals(
                            annotationComponent.getAnnotation().getPObjectReference())) {
                        annotationComponent.requestFocus();
                        break;
                    }
                }
            }
        }
        return found;
    }
}
