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
