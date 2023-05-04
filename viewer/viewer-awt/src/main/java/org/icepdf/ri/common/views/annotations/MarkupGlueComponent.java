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

import org.icepdf.core.pobjects.Page;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;

/**
 * MarkupGlueComponent allows for a visual associating between a markup annotation and it's popup annotation
 * when open.
 *
 * @since 6.3
 */
public class MarkupGlueComponent extends JComponent implements PageViewAnnotationComponent {

    protected MarkupAnnotationComponent markupAnnotationComponent;
    protected PopupAnnotationComponent popupAnnotationComponent;

    protected Rectangle adjustedMarkupAnnotationBounds;

    private DocumentViewController documentViewController;
    protected AbstractPageViewComponent parentPageViewComponent;

    public MarkupGlueComponent(DocumentViewController documentViewController,
                               MarkupAnnotationComponent markupAnnotationComponent,
                               PopupAnnotationComponent popupAnnotationComponent) {
        this.documentViewController = documentViewController;
        this.markupAnnotationComponent = markupAnnotationComponent;
        this.popupAnnotationComponent = popupAnnotationComponent;
    }

    public MarkupAnnotationComponent getMarkupAnnotationComponent() {
        return markupAnnotationComponent;
    }

    public PopupAnnotationComponent getPopupAnnotationComponent() {
        return popupAnnotationComponent;
    }

    public void refreshDirtyBounds() {
        adjustedMarkupAnnotationBounds = recalculateAnnotationBounds();
        setBounds(adjustedMarkupAnnotationBounds);
    }

    private Rectangle recalculateAnnotationBounds() {
        Page currentPage = parentPageViewComponent.getPage();
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        AffineTransform at = currentPage.getPageTransform(
                documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(),
                documentViewModel.getViewZoom());
        Rectangle2D markupUserSpaceRectangle = markupAnnotationComponent.getAnnotation().getUserSpaceRectangle();
        Rectangle annotationPageSpaceBounds =
                AbstractAnnotationComponent.commonBoundsNormalization(new GeneralPath(markupUserSpaceRectangle), at);
        Rectangle pageBounds = parentPageViewComponent.getParent().getBounds();
        annotationPageSpaceBounds.x += pageBounds.x;
        annotationPageSpaceBounds.y += pageBounds.y;
        return annotationPageSpaceBounds;
    }

    @Override
    public void paintComponent(Graphics g) {
        // not ideal to setBounds during the paint but it covers a lot of painting corner cases very well.
        Rectangle popupBounds = popupAnnotationComponent.getBounds();
        Rectangle markupBounds = adjustedMarkupAnnotationBounds;
        Rectangle bound = markupBounds.union(popupBounds);
        setBounds(bound);

        Rectangle bounds = getBounds();
        Graphics2D g2d = (Graphics2D) g;

        if (popupAnnotationComponent.isVisible()) {
            g2d.setColor(markupAnnotationComponent.getAnnotation().getColor());
            g2d.setStroke(new BasicStroke(1));
            GeneralPath path = new GeneralPath();
            path.moveTo(0, 0);

            // in order to draw the curvy shape we need to determine which of the 8 surrounding regions
            // the popup is relative to the markup annotation.
            int popupX = popupBounds.x;
            int popupY = popupBounds.y;
            int popupXC = (int) popupBounds.getCenterX();
            int popupYC = (int) popupBounds.getCenterY();
            int popupW = popupBounds.width;
            int popupH = popupBounds.height;

            int markupXC = (int) markupBounds.getCenterX();
            int markupYC = (int) markupBounds.getCenterY();

            float angle = (float) Math.toDegrees(Math.atan2(markupYC - popupYC, markupXC - popupXC));
            if (angle < 0) {
                angle += 360;
            }

            // N
            if (angle >= 67.5 && angle < 112.5) {
                path.moveTo(markupXC, markupYC);
                path.quadTo(popupXC, popupY + popupH, popupX, popupY + popupH);
                path.lineTo(popupX + popupW, popupY + popupH);
                path.quadTo(popupXC, popupY + popupH, markupXC, markupYC);
            }
            // NE
            else if (angle >= 112.5 && angle < 157.5) {
                path.moveTo(markupXC, markupYC);
                path.quadTo(popupX, popupY + popupH, popupX, popupYC);
                path.lineTo(popupXC, popupY + popupH);
                path.quadTo(popupX, popupY + popupH, markupXC, markupYC);
            }
            // E
            else if (angle >= 157.5 && angle < 202.5) {
                path.moveTo(markupXC, markupYC);
                path.quadTo(popupX, popupYC, popupX, popupY);
                path.lineTo(popupX, popupY + popupH);
                path.quadTo(popupX, popupYC, markupXC, markupYC);
            }
            // SE
            else if (angle >= 202.5 && angle < 247.5) {
                path.moveTo(markupXC, markupYC);
                path.quadTo(popupX, popupY, popupXC, popupY);
                path.lineTo(popupX, popupYC);
                path.quadTo(popupX, popupY, markupXC, markupYC);
            }
            // S
            else if (angle >= 247.5 && angle < 292.5) {
                path.moveTo(markupXC, markupYC);
                path.quadTo(popupXC, popupY, popupX, popupY);
                path.lineTo(popupX + popupW, popupY);
                path.quadTo(popupXC, popupY, markupXC, markupYC);
            } else if (angle >= 292.5 && angle < 315) {
                path.moveTo(markupXC, markupYC);
                path.quadTo(popupX + popupW, popupY, popupXC, popupY);
                path.lineTo(popupX + popupW, popupYC);
                path.quadTo(popupX + popupW, popupY, markupXC, markupYC);
            }
            // W
            else if (angle >= 315 || angle < 22.5) {
                path.moveTo(markupXC, markupYC);
                path.quadTo(popupX + popupW, popupYC, popupX + popupW, popupY);
                path.lineTo(popupX + popupW, popupY + popupH);
                path.quadTo(popupX + popupW, popupYC, markupXC, markupYC);
            }
            // NW
            else if (angle >= 22.5 && angle < 67.5) {
                path.moveTo(markupXC, markupYC);
                path.quadTo(popupX + popupW, popupY + popupH, popupX + popupW, popupYC);
                path.lineTo(popupXC, popupY + popupH);
                path.quadTo(popupX + popupW, popupY + popupH, markupXC, markupYC);
            }
            // translate to this components space.
            path.transform(new AffineTransform(1, 0, 0, 1, -bounds.x, -bounds.y));
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            g2d.fill(path);
            g2d.setStroke(new BasicStroke(3));
            g2d.setColor(Color.RED);
            g2d.draw(bound);
        }
    }

    public void setParentPageComponent(AbstractPageViewComponent pageViewComponent) {
        parentPageViewComponent = pageViewComponent;
    }

}
