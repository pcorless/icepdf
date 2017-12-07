/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.ri.common.utility.annotation.properties.LinkAnnotationPanel;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;

/**
 * The LinkAnnotationComponent encapsulates a LinkAnnotation objects.  It
 * also provides basic editing functionality such as resizing, moving and change
 * the border color and style.  The rollover effect can also be set to one
 * of the named states defined in the LinkAnnotation object. .
 * <br>
 * The Viewer RI implementation contains a LinkAnnotationPanel class which
 * can edit the various properties of this component.
 *
 * @see LinkAnnotationPanel
 * @since 5.0
 */
@SuppressWarnings("serial")
public class LinkAnnotationComponent extends AbstractAnnotationComponent<LinkAnnotation> {

    protected Color highlightColor = new Color(Integer.parseInt("83A3D3", 16));
    protected Color linkColor = new Color(Integer.parseInt("990033", 16));

    public LinkAnnotationComponent(LinkAnnotation annotation, DocumentViewController documentViewController,
                                   AbstractPageViewComponent pageViewComponent) {
        super(annotation, documentViewController, pageViewComponent);
        isShowInvisibleBorder = true;

        AnnotationPopup<LinkAnnotationComponent> annotationPopup = new AnnotationPopup<>(this,
                documentViewController.getParentController(), getPageViewComponent());
        annotationPopup.buildGui();

        contextMenu = annotationPopup;
        // Add listener to components that can bring up popup menus.
        MouseListener popupListener = new LinkPopupListener(contextMenu);
        addMouseListener(popupListener);
    }

    private boolean isAnnotationEditable() {
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        return ((documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION ||
                documentViewModel.getViewToolMode() ==
                        DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION) &&
                !(annotation.getFlagReadOnly() || annotation.getFlagLocked() ||
                        annotation.getFlagInvisible() || annotation.getFlagHidden()));
    }

    public void paintComponent(Graphics g) {
        // sniff out tool bar state to set correct annotation border
        isEditable = isAnnotationEditable();

        // check for the annotation editing mode and draw the link effect so it's easier to see.
        if (documentViewController.getParentController().getViewModel().isAnnotationEditingMode()) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Composite composite = g2d.getComposite();
            Color color = g2d.getColor();
            AffineTransform affineTransform = g2d.getTransform();
            // draw the main box
            g2d.setColor(highlightColor);
            g2d.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            g2d.fillRect(0, 0, getWidth(), getHeight());

            // draw the link arrow
            int width = 15;
            int height = 10;
            if (height > getHeight() - 4) {
                height = getHeight() - 4;
            }
            int indent = 4;
            GeneralPath generalPath = new GeneralPath();
            generalPath.moveTo(0, 0);
            generalPath.lineTo(width - indent, 0);
            generalPath.lineTo(width, height / 2);
            generalPath.lineTo(width - indent, height);
            generalPath.lineTo(0, height);
            generalPath.lineTo(indent, height / 2);
            generalPath.closePath();

            DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
            Page currentPage = pageViewComponent.getPage();
            AffineTransform at = currentPage.getPageTransform(
                    documentViewModel.getPageBoundary(),
                    documentViewModel.getViewRotation(),
                    documentViewModel.getViewZoom());
            at.setTransform(at.getScaleX(), 0, 0, -at.getScaleY(), 0, 0);

            Shape shape = at.createTransformedShape(generalPath);
            Rectangle bounds = shape.getBounds();
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.40f));
            g2d.translate(getWidth() - bounds.width - 2, getHeight() - bounds.height - 2);
            g2d.setColor(linkColor);
            g2d.fill(shape);
            g2d.setColor(Color.DARK_GRAY);
            g2d.draw(shape);

            // reset the context.
            g2d.setComposite(composite);
            g2d.setColor(color);
            g2d.setTransform(affineTransform);
        }

        // paint rollover effects.
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        if (isMousePressed && !(documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION ||
                documentViewModel.getViewToolMode() ==
                        DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION)) {
            Graphics2D gg2 = (Graphics2D) g;

            Name highlightMode = annotation.getHighlightMode();
            Rectangle2D rect = new Rectangle(0, 0, getWidth(), getHeight());
            if (LinkAnnotation.HIGHLIGHT_INVERT.equals(highlightMode)) {
                gg2.setColor(annotationHighlightColor);
                gg2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        annotationHighlightAlpha));
                gg2.fillRect((int) rect.getX(),
                        (int) rect.getY(),
                        (int) rect.getWidth(),
                        (int) rect.getHeight());
            } else if (LinkAnnotation.HIGHLIGHT_OUTLINE.equals(highlightMode)) {
                gg2.setColor(annotationHighlightColor);
                gg2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        annotationHighlightAlpha));
                gg2.drawRect((int) rect.getX(),
                        (int) rect.getY(),
                        (int) rect.getWidth(),
                        (int) rect.getHeight());
            } else if (LinkAnnotation.HIGHLIGHT_PUSH.equals(highlightMode)) {
                gg2.setColor(annotationHighlightColor);
                gg2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        annotationHighlightAlpha));
                gg2.drawRect((int) rect.getX(),
                        (int) rect.getY(),
                        (int) rect.getWidth(),
                        (int) rect.getHeight());
            }
        }
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void resetAppearanceShapes() {

    }

    public class LinkPopupListener extends PopupListener {

        public LinkPopupListener(JPopupMenu contextMenu) {
            super(contextMenu);
        }

        protected void maybeShowPopup(MouseEvent e) {
            if (e.isPopupTrigger() && isAnnotationEditable()) {
                contextMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        }
    }
}
