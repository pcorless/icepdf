package org.icepdf.ri.common.views.annotations;

import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;

public class RedactionAnnotationComponent extends MarkupAnnotationComponent<RedactionAnnotation> {
    public RedactionAnnotationComponent(RedactionAnnotation annotation,
                                        DocumentViewController documentViewController,
                                        AbstractPageViewComponent pageViewComponent) {
        super(annotation, documentViewController, pageViewComponent);
        isMovable = false;
        isResizable = false;
        isShowInvisibleBorder = false;
    }

    @Override
    public void resetAppearanceShapes() {
        super.resetAppearanceShapes();
        annotation.resetAppearanceStream(getToPageSpaceTransform());
    }
}
