package org.icepdf.ri.common.views;

import org.icepdf.ri.common.views.annotations.PageViewAnnotationComponent;

import java.util.ArrayList;

public abstract class BasePageViewLayout {

    protected DocumentViewModel documentViewModel;

    public BasePageViewLayout(DocumentViewModel documentViewModel) {
        this.documentViewModel = documentViewModel;
    }

    protected void updatePopupAnnotationComponents(PageViewDecorator pageViewDecorator){
        PageViewComponent pageViewComponent = pageViewDecorator.getPageViewComponent();
        ArrayList<PageViewAnnotationComponent> annotationComponents =
                documentViewModel.getFloatingAnnotationComponents((AbstractPageViewComponent) pageViewComponent);
        if (annotationComponents != null) {
            // update location of popups, so we don't get any repaint ghosting for flicker.
            annotationComponents.forEach((PageViewAnnotationComponent::refreshDirtyBounds));
        }
    }
}
