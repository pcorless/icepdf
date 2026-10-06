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
package org.icepdf.ri.common.tools;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.text.Bias;
import org.icepdf.core.pobjects.graphics.text.Caret;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentTextSelection;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

/**
 * Handles Paint and mouse/keyboard logic around text selection and search
 * highlighting.  there is on text handler instance of each pageComponent
 * used to display the document.
 * <br>
 * The highlight colour by default is #FFF600 but can be set using color or
 * hex values names using the system property "org.icepdf.core.views.page.text.highlightColor"
 * <br>
 * The highlight colour by default is #FFF600 but can be set using color or
 * hex values names using the system property "org.icepdf.core.views.page.text.selectionColor"
 * <br>
 *
 * @since 4.0
 */
public class TextSelectionPageHandler extends TextSelection
        implements ToolHandler {

    protected boolean isMouseDrag;
    protected boolean isClearSelection;

    /**
     * New Text selection handler.  Make sure to correctly and remove
     * this mouse and text listeners.
     *
     * @param pageViewComponent page component that this handler is bound to.
     * @param documentViewController view model.
     */
    public TextSelectionPageHandler(DocumentViewController documentViewController,
                                    AbstractPageViewComponent pageViewComponent) {
        super(documentViewController, pageViewComponent);
    }

    public void setDocumentViewController(DocumentViewController documentViewController) {
        this.documentViewController = documentViewController;
    }

    /**
     * When the mouse is double-clicked we select the word the mouse if over.  When
     * the mouse is triple clicked we select the line of text that the mouse
     * is over.
     */
    public void mouseClicked(MouseEvent e) {
        wordLineSelection(e.getClickCount(), e.getPoint(), pageViewComponent);
    }

    /**
     * Invoked when a mouse button has been pressed on a component.
     */
    public void mousePressed(MouseEvent e) {
        isClearSelection = false;
        this.pageViewComponent.requestFocusInWindow();
        lastMousePressedLocation = e.getPoint();

        selectionStart(e.getPoint(), pageViewComponent, true);
    }

    /**
     * Invoked when a mouse button has been released on a component.
     */
    public void mouseReleased(MouseEvent e) {
        selectionEnd(e.getPoint(), pageViewComponent);
        isMouseDrag = false;
    }

    /**
     * Invoked when a mouse button is pressed on a component and then
     * dragged.  <code>MOUSE_DRAGGED</code> events will continue to be
     * delivered to the component where the drag originated until the
     * mouse button is released (regardless of whether the mouse position
     * is within the bounds of the component).
     * <br>
     * Due to platform-dependent Drag&amp;Drop implementations,
     * <code>MOUSE_DRAGGED</code> events may not be delivered during a native
     * Drag&amp;Drop operation.
     */
    public void mouseDragged(MouseEvent e) {
        if (isClearSelection) {
            return;
        }
        isMouseDrag = true;
        Point point = e.getPoint();
        updateSelectionSize(point.x, point.y, pageViewComponent);
        boolean isMovingDown = true;
        boolean isMovingRight = true;
        if (lastMousePressedLocation != null) {
            isMovingDown = lastMousePressedLocation.y <= e.getPoint().y;
            isMovingRight = lastMousePressedLocation.x <= e.getPoint().x;
        }
        selection(e.getPoint(), pageViewComponent, isMovingDown, isMovingRight);
    }

    /**
     * Invoked when the mouse enters a component.
     */
    public void mouseEntered(MouseEvent e) {

    }

    /**
     * Invoked when the mouse exits a component.
     */
    public void mouseExited(MouseEvent e) {

    }

    public void setSelectionRectangle(Point cursorLocation, Rectangle selection) {

        // rectangle select tool
        setSelectionSize(selection, pageViewComponent);

    }

    /**
     * Invoked when the mouse cursor has been moved onto a component
     * but no buttons have been pushed.
     */
    public void mouseMoved(MouseEvent e) {
        // change state of mouse from pointer to text selection icon
        selectionTextSelectIcon(e.getPoint(), pageViewComponent);
    }

    public void cancelSelection() {
        isMouseDrag = false;
        isClearSelection = true;
    }

    public void installTool() {

    }

    public void uninstallTool() {

    }

    public void paintTool(Graphics g) {
        // paint the keyboard caret bar when the document focus caret is on this page.
        DocumentViewModel model = documentViewController.getDocumentViewModel();
        DocumentTextSelection selection = model.getTextSelection();
        if (selection.isEmpty() || selection.getFocusPage() != pageViewComponent.getPageIndex()
                || !CaretBlink.isVisible()) {
            return;
        }
        PageText pageText = TextSelectionSupport.loadedPageText(pageViewComponent);
        if (pageText == null) return;
        Page page = pageViewComponent.getPage();
        TextSequence sequence = pageText.getTextSequence();
        AffineTransform transform = page.getPageTransform(
                model.getPageBoundary(), model.getViewRotation(), model.getViewZoom());
        Rectangle2D.Double caret = sequence.caretRect(new Caret(selection.getFocusOffset(), Bias.FORWARD));
        Rectangle2D bounds = transform.createTransformedShape(caret).getBounds2D();
        Graphics2D g2 = (Graphics2D) g;
        Color oldColor = g2.getColor();
        Stroke oldStroke = g2.getStroke();
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new Line2D.Double(bounds.getX(), bounds.getMinY(), bounds.getX(), bounds.getMaxY()));
        g2.setColor(oldColor);
        g2.setStroke(oldStroke);
    }


}
