package org.icepdf.ri.common.tools;

import org.icepdf.core.pobjects.Page;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;

/**
 * Common logicv to all annotation handlers.
 *
 * @since 5.0
 */
public class CommonToolHandler {

    // parent page component
    protected AbstractPageViewComponent pageViewComponent;
    protected DocumentViewController documentViewController;
    protected DocumentViewModel documentViewModel;

    public CommonToolHandler(DocumentViewController documentViewController,
                             AbstractPageViewComponent pageViewComponent,
                             DocumentViewModel documentViewModel) {
        this.pageViewComponent = pageViewComponent;
        this.documentViewController = documentViewController;
        this.documentViewModel = documentViewModel;
    }

    protected AffineTransform getPageTransform() {
        Page currentPage = pageViewComponent.getPage();
        AffineTransform at = currentPage.getPageTransform(
                documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(),
                documentViewModel.getViewZoom());
        try {
            at = at.createInverse();
        } catch (NoninvertibleTransformException e1) {
            e1.printStackTrace();
        }
        return at;
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
        } catch (NoninvertibleTransformException e1) {
            e1.printStackTrace();
        }

        shape = at.createTransformedShape(shape);

        return shape;

    }
}
