/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2011 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.views.common;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Handles the drawing of a selection box commonly used for selection
 * amoung other things.
 *
 * @since 4.0
 */
public class SelectionBoxHandler {

    // dashed selection rectangle stroke
    protected static float dash1[] = {1.0f};
    protected static BasicStroke stroke = new BasicStroke(1.0f,
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER,
            1.0f, dash1, 0.0f);

    // selection rectangle used for glyph intersection aka text selection
    protected Rectangle currentRect = null;
    protected Rectangle rectToDraw = null;
    protected Rectangle previousRectDrawn = new Rectangle();

    protected Color selectionBoxColour = Color.lightGray;


    public void paintSelectionBox(Graphics g) {
        Graphics2D gg = (Graphics2D) g;
        Color oldColor = gg.getColor();
        Stroke oldStroke = gg.getStroke();
        if (rectToDraw != null) {
            //Draw a rectangle on top of the image.
            oldColor = g.getColor();
            g.setColor(selectionBoxColour);
            gg.setStroke(stroke);
            g.drawRect(rectToDraw.x, rectToDraw.y,
                    rectToDraw.width - 1, rectToDraw.height - 1);
            g.setColor(oldColor);
        }

        gg.setColor(oldColor);
        gg.setStroke(oldStroke);
    }

    public void resetRectangle(int x, int y){
        currentRect = new Rectangle(x, y, 0, 0);   
    }

    public Rectangle getCurrentRect() {
        return currentRect;
    }

    public void setCurrentRect(Rectangle currentRect) {
        this.currentRect = currentRect;
    }

    public Rectangle getRectToDraw() {
        return rectToDraw;
    }

    public void setRectToDraw(Rectangle rectToDraw) {
        this.rectToDraw = rectToDraw;
    }

    public void clearRectangle(Component component){
        // clear the rectangle
        currentRect = new Rectangle(0, 0, 0, 0);
        updateDrawableRect(component.getWidth(),
                component.getHeight());
    }

    /**
     * Update the size of the selection rectangle.
     *
     * @param e
     */
    public void updateSelectionSize(MouseEvent e, Component component) {
        int x = e.getX();
        int y = e.getY();
        // dragging across pages will result in null pointer if don't init.
        if (currentRect == null){
            currentRect = new Rectangle(x, y, 0, 0);
        }
        currentRect.setSize(x - currentRect.x,
                y - currentRect.y);
        updateDrawableRect(component.getWidth(), component.getHeight());
        Rectangle totalRepaint = rectToDraw.union(previousRectDrawn);
        component.repaint(totalRepaint.x, totalRepaint.y,
                totalRepaint.width, totalRepaint.height);
    }

    public void setSelectionSize(Rectangle rect, Component component) {

         currentRect = rect;

        updateDrawableRect(component.getWidth(), component.getHeight());
        Rectangle totalRepaint = rectToDraw.union(previousRectDrawn);
        component.repaint(totalRepaint.x, totalRepaint.y,
                totalRepaint.width, totalRepaint.height);
    }

    /**
     * Udpate the drawable rectangle so that it does not extend bast the edge
     * of the page.
     *
     * @param compWidth  width of component being selected
     * @param compHeight height of component being selected.
     */
    public void updateDrawableRect(int compWidth, int compHeight) {
        int x = currentRect.x;
        int y = currentRect.y;
        int width = currentRect.width;
        int height = currentRect.height;

        //Make the width and height positive, if necessary.
        if (width < 0) {
            width = 0 - width;
            x = x - width + 1;
            if (x < 0) {
                width += x;
                x = 0;
            }
        }
        if (height < 0) {
            height = 0 - height;
            y = y - height + 1;
            if (y < 0) {
                height += y;
                y = 0;
            }
        }

        //The rectangle shouldn't extend past the drawing area.
        if ((x + width) > compWidth) {
            width = compWidth - x;
        }
        if ((y + height) > compHeight) {
            height = compHeight - y;
        }

        //Update rectToDraw after saving old value.
        if (rectToDraw != null) {
            previousRectDrawn.setBounds(
                    rectToDraw.x, rectToDraw.y,
                    rectToDraw.width, rectToDraw.height);
            rectToDraw.setBounds(x, y, width, height);
        } else {
            rectToDraw = new Rectangle(x, y, width, height);
        }
    }
}
