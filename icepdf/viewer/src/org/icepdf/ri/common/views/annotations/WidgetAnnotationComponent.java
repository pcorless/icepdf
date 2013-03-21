package org.icepdf.ri.common.views.annotations;

import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;

import java.awt.*;

/**
 *
 */
public class WidgetAnnotationComponent extends AbstractAnnotationComponent {


    public WidgetAnnotationComponent(Annotation annotation, DocumentViewController documentViewController,
                                     AbstractPageViewComponent pageViewComponent, DocumentViewModel documentViewModel) {
        super(annotation, documentViewController, pageViewComponent, documentViewModel);
        isShowInvisibleBorder = true;
    }

    @Override
    public void resetAppearanceShapes() {

    }

    @Override
    public void paintComponent(Graphics g) {

    }
}