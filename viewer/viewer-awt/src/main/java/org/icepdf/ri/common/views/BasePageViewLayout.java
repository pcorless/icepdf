/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.ri.common.views;

import org.icepdf.ri.common.views.annotations.PageViewAnnotationComponent;

import java.util.ArrayList;

public abstract class BasePageViewLayout {

    protected static final int PAGE_SPACING_HORIZONTAL = 2;
    protected static final int PAGE_SPACING_VERTICAL = 2;

    protected DocumentViewModel documentViewModel;

    public BasePageViewLayout(DocumentViewModel documentViewModel) {
        this.documentViewModel = documentViewModel;
    }

    protected void updatePopupAnnotationComponents(PageViewDecorator pageViewDecorator){
        PageViewComponent pageViewComponent = pageViewDecorator.getPageViewComponent();
        ArrayList<PageViewAnnotationComponent> annotationComponents =
                documentViewModel.getDocumentViewAnnotationComponents((AbstractPageViewComponent) pageViewComponent);
        if (annotationComponents != null) {
            // update location of popups, so we don't get any repaint ghosting for flicker.
            annotationComponents.forEach((PageViewAnnotationComponent::refreshDirtyBounds));
        }
    }
}
