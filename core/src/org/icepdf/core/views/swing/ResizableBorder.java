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
 * The Original Code is ICEpdf 4.1 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
package org.icepdf.core.views.swing;

import org.icepdf.core.util.ColorUtil;
import org.icepdf.core.util.Defs;
import org.icepdf.core.views.AnnotationComponent;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The resizable border is mainly designed to bed used with mutable annotation
 * in the UI but supect it could be used for ather content manipulation. Like
 * other Swing Borders the same instance can be used on multiple components.
 *
 * @since 4.0
 */
public class ResizableBorder extends AbstractBorder {

    private static final Logger logger =
            Logger.getLogger(ResizableBorder.class.toString());

    private static Color selectColor;
    private static Color outlineColor;

    static {

        // sets annotation selected highlight colour
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.select.color", "#0000FF");
            int colorValue = ColorUtil.convertColor(color);
            selectColor =
                    new Color(colorValue > 0 ? colorValue :
                            Integer.parseInt("0000FF", 16));

            color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.outline.color", "#000000");
            colorValue = ColorUtil.convertColor(color);
            outlineColor =
                    new Color(colorValue > 0 ? colorValue :
                            Integer.parseInt("000000", 16));

        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading page annotation outline colour");
            }
        }
    }

    private static final int locations[] = {
            SwingConstants.NORTH, SwingConstants.SOUTH, SwingConstants.WEST,
            SwingConstants.EAST, SwingConstants.NORTH_WEST,
            SwingConstants.NORTH_EAST, SwingConstants.SOUTH_WEST,
            SwingConstants.SOUTH_EAST };

    private static final int cursors[] = {
            Cursor.N_RESIZE_CURSOR, Cursor.S_RESIZE_CURSOR, Cursor.W_RESIZE_CURSOR,
            Cursor.E_RESIZE_CURSOR, Cursor.NW_RESIZE_CURSOR, Cursor.NE_RESIZE_CURSOR,
            Cursor.SW_RESIZE_CURSOR, Cursor.SE_RESIZE_CURSOR };

    protected int resizeWidgetDim;

    public ResizableBorder(int resizeBoxSize) {
        this.resizeWidgetDim = resizeBoxSize;
    }

    public Insets getBorderInsets(Component component) {
        return new Insets(10,10, 10, 10);
    }

    public boolean isBorderOpaque() {
        return false;
    }

    public void paintBorder(Component component, Graphics g, int x, int y,
                            int w, int h) {
        boolean isSelected = false;
        boolean isEditable = false;
        boolean isRollover = false;
        boolean isLinkAnnot = false;
        boolean isBorderStyle = false;

        // get render flags from component.
        if (component instanceof AnnotationComponent){
            AnnotationComponent annot = (AnnotationComponent) component;
            isEditable = annot.isEditable();
            isRollover = annot.isRollover();
            isLinkAnnot = annot.isLinkAnnot();
            isBorderStyle = annot.isBorderStyle();
            isSelected = annot.isSelected();
        }

        // if we aren't in the edit mode, then we have nothing to paint.
        if (!isEditable){
            return;
        }

        // get paint colour
        if (isSelected || component.hasFocus() || isRollover){
            g.setColor(selectColor);
        }
        else{
            g.setColor(outlineColor);
        }

        // paint border
        if (isSelected || isRollover || (isLinkAnnot && !isBorderStyle)){
            g.drawRect(x, y, w-1, h-1);
        }

        // paint resize widgets.
        if ((isSelected || isRollover) && isLinkAnnot){
            for (int location : locations) {
                Rectangle rect = getRectangle(x, y, w, h, location);
//                g.setColor(Color.WHITE);
                g.fillRect(rect.x, rect.y, rect.width - 1, rect.height - 1);
//                g.setColor(Color.BLACK);
                g.drawRect(rect.x, rect.y, rect.width - 1, rect.height - 1);
            }
        }
    }


    private Rectangle getRectangle(int x, int y, int w, int h, int location) {
        switch (location) {
            case SwingConstants.NORTH:
                return new Rectangle(x + w / 2 - resizeWidgetDim / 2, y, resizeWidgetDim, resizeWidgetDim);
            case SwingConstants.SOUTH:
                return new Rectangle(x + w / 2 - resizeWidgetDim / 2, y + h - resizeWidgetDim, resizeWidgetDim,
                        resizeWidgetDim);
            case SwingConstants.WEST:
                return new Rectangle(x, y + h / 2 - resizeWidgetDim / 2, resizeWidgetDim, resizeWidgetDim);
            case SwingConstants.EAST:
                return new Rectangle(x + w - resizeWidgetDim, y + h / 2 - resizeWidgetDim / 2, resizeWidgetDim,
                        resizeWidgetDim);
            case SwingConstants.NORTH_WEST:
                return new Rectangle(x, y, resizeWidgetDim, resizeWidgetDim);
            case SwingConstants.NORTH_EAST:
                return new Rectangle(x + w - resizeWidgetDim, y, resizeWidgetDim, resizeWidgetDim);
            case SwingConstants.SOUTH_WEST:
                return new Rectangle(x, y + h - resizeWidgetDim, resizeWidgetDim, resizeWidgetDim);
            case SwingConstants.SOUTH_EAST:
                return new Rectangle(x + w - resizeWidgetDim, y + h - resizeWidgetDim, resizeWidgetDim, resizeWidgetDim);
        }
        return null;
    }


    public int getCursor(MouseEvent me) {
        Component c = me.getComponent();
        boolean isEditable = false;
        boolean isLinkAnnot = false;

        // get render flags from component.
        if (c instanceof AnnotationComponentImpl){
            AnnotationComponentImpl annot = (AnnotationComponentImpl) c;
            isEditable = annot.isEditable();
            isLinkAnnot = annot.isLinkAnnot();
        }

        int w = c.getWidth();
        int h = c.getHeight();

        // show resize cursors for link annotations
        if ((isEditable && isLinkAnnot)){
            for (int i = 0; i < locations.length; i++) {
                Rectangle rect = getRectangle(0, 0, w, h, locations[i]);
                if (rect.contains(me.getPoint()))
                    return cursors[i];
            }
        }
        // other wise just show the move. 
        return Cursor.MOVE_CURSOR;
    }
}
