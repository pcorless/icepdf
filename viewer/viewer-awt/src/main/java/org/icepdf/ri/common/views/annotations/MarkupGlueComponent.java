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
import org.icepdf.core.pobjects.annotations.MarkupGluePainter;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;


import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;

/**
 * MarkupGlueComponent allows for a visual associating between a markup annotation, and it's popup annotation
 * when open.
 *
 * @since 6.3
 */
public class MarkupGlueComponent extends JComponent implements PageViewAnnotationComponent, ComponentListener {

    protected final MarkupAnnotationComponent markupAnnotationComponent;
    protected final PopupAnnotationComponent popupAnnotationComponent;

    protected Rectangle adjustedMarkupAnnotationBounds;

    private DocumentViewController documentViewController;
    protected AbstractPageViewComponent parentPageViewComponent;

    public MarkupGlueComponent(DocumentViewController documentViewController,
                               MarkupAnnotationComponent markupAnnotationComponent,
                               PopupAnnotationComponent popupAnnotationComponent) {
        this.documentViewController = documentViewController;
        this.markupAnnotationComponent = markupAnnotationComponent;
        this.popupAnnotationComponent = popupAnnotationComponent;
        this.popupAnnotationComponent.addComponentListener(this);
    }

    public void dispose(){
        if (popupAnnotationComponent != null) {
            popupAnnotationComponent.removeComponentListener(this);
        }
    }

    public PopupAnnotationComponent getPopupAnnotationComponent() {
        return popupAnnotationComponent;
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

    public void refreshDirtyBounds() {
        adjustedMarkupAnnotationBounds = recalculateAnnotationBounds();
        Rectangle popupBounds = popupAnnotationComponent.getBounds();
        Rectangle markupBounds = adjustedMarkupAnnotationBounds;
        Rectangle bound = markupBounds.union(popupBounds);
        setBounds(bound);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);


        if (popupAnnotationComponent.isVisible()) {
            Rectangle popupBounds = popupAnnotationComponent.getBounds();
            Rectangle markupBounds = markupAnnotationComponent.getBounds();
            Rectangle glueBounds = markupAnnotationComponent.getBounds().union(popupAnnotationComponent.getBounds());
            setBounds(glueBounds);
            setPreferredSize(glueBounds.getSize());
            setSize(glueBounds.getSize());

            MarkupGluePainter.paintGlue(
                    g, markupBounds, popupBounds, glueBounds,
                    markupAnnotationComponent.getAnnotation().getColor());


    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (popupAnnotationComponent.getAnnotation().isOpen()) {
            Rectangle popupBounds = popupAnnotationComponent.getBounds();
            Rectangle markupBounds = markupAnnotationComponent.getBounds();
            Rectangle glueBounds = markupAnnotationComponent.getBounds().union(popupAnnotationComponent.getBounds());
            setBounds(glueBounds);
            setPreferredSize(glueBounds.getSize());
            setSize(glueBounds.getSize());

            MarkupGluePainter.paintGlue(
                    g, markupBounds, popupBounds, glueBounds,
                    markupAnnotationComponent.getAnnotation().getColor());
        }
    }

    @Override
    public void componentResized(ComponentEvent componentEvent) {

    }

    @Override
    public void componentMoved(ComponentEvent componentEvent) {
        this.refreshDirtyBounds();
    }

    @Override
    public void componentShown(ComponentEvent componentEvent) {

    }

    @Override
    public void componentHidden(ComponentEvent componentEvent) {

    }

    public void setParentPageComponent(AbstractPageViewComponent pageViewComponent) {
        parentPageViewComponent = pageViewComponent;
    }

}
