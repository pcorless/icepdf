/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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

package org.icepdf.ri.common.tools;

import org.icepdf.core.pobjects.Page;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;
import org.icepdf.ri.util.PropertiesManager;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Common logic to all annotation handlers.
 *
 * @since 5.0
 */
public abstract class CommonToolHandler {

    private static final Logger logger =
            Logger.getLogger(CommonToolHandler.class.toString());

    // parent page component
    protected AbstractPageViewComponent pageViewComponent;
    protected DocumentViewController documentViewController;

    protected Preferences preferences;

    /**
     * Create a new common tool handler.  The tool handle can operate on a view or at the page level.  If the
     * handler only operates at the view level then pageViewComponent can be set to null;
     *
     * @param documentViewController parent view controller
     * @param pageViewComponent      page view component tool acts on, can be null for view tool handlers.
     */
    public CommonToolHandler(DocumentViewController documentViewController,
                             AbstractPageViewComponent pageViewComponent) {
        this.pageViewComponent = pageViewComponent;
        this.documentViewController = documentViewController;

        PropertiesManager propertiesManager = PropertiesManager.getInstance();
        preferences = propertiesManager.getPreferences();
    }

    protected abstract void checkAndApplyPreferences();

    protected AffineTransform getToPageSpaceTransform() {
        return getToPageSpaceTransform(pageViewComponent);
    }

    protected AffineTransform getToPageSpaceTransform(AbstractPageViewComponent pageViewComponent) {
        Page currentPage = pageViewComponent.getPage();
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        return currentPage.getToPageSpaceTransform(
                documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(),
                documentViewModel.getViewZoom());
    }

    protected AffineTransform getPageTransform() {
        return getPageTransform(pageViewComponent);
    }

    protected AffineTransform getPageTransform(AbstractPageViewComponent pageViewComponent) {
        Page currentPage = pageViewComponent.getPage();
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        AffineTransform at = currentPage.getPageTransform(
                documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(),
                documentViewModel.getViewZoom());
        return at;
    }

    /**
     * Convert the shapes that make up the annotation to page space so that
     * they will scale correctly at different zooms.
     *
     * @param rect rectangle of rectangle to convert to page space.
     * @return transformed bBox.
     */
    protected Rectangle convertToPageSpace(Rectangle rect) {
        Page currentPage = pageViewComponent.getPage();
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        return currentPage.convertToPageSpace(rect, documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(), documentViewModel.getViewZoom());
    }

    /**
     * Converts the location point from g2d to page space.
     *
     * @param location location to convert.
     * @return converted point with Point2D precision.
     */
    protected Point2D.Float convertToPageSpace(Point location) {
        Page currentPage = pageViewComponent.getPage();
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        return currentPage.convertToPageSpace(location, documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(), documentViewModel.getViewZoom());
    }

    /**
     * Convert the shapes that make up the annotation to page space so that
     * they will scale correctly at different zooms.
     *
     * @param shape shape to convert to page space.
     * @return transformed bBox.
     */
    protected Shape convertToPageSpace(Shape shape) {
        Page currentPage = pageViewComponent.getPage();
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        return currentPage.convertToPageSpace(shape, documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(), documentViewModel.getViewZoom());
    }

    /**
     * Converts the given point from g2d space to page space.
     *
     * @param points points to convert.
     * @return list of converted points with Point2D precision.
     */
    protected Point2D[] convertToPageSpace(Point... points) {
        if (points != null) {
            Page currentPage = pageViewComponent.getPage();
            DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
            AffineTransform pageSpaceTransform = currentPage.getToPageSpaceTransform(documentViewModel.getPageBoundary(),
                    documentViewModel.getViewRotation(), documentViewModel.getViewZoom());
            Point2D[] point2DS = new Point2D[points.length];
            for (int i = 0, max = points.length; i < max; i++) {
                point2DS[i] = Page.convertTo(points[i], pageSpaceTransform);
            }
            return point2DS;
        }
        return null;
    }
}
