package org.icepdf.ri.common.fonts;

import org.icepdf.ri.images.Images;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

/**
 * DefaultTreeCellRenderer for the a Font tree node.
 * Usage can be found in {@link org.icepdf.ri.common.fonts.FontDialog}.
 */
@SuppressWarnings("serial")
public class FontCellRender extends DefaultTreeCellRenderer {

    public Component getTreeCellRendererComponent(
            JTree tree,
            Object value,
            boolean sel,
            boolean expanded,
            boolean leaf,
            int row,
            boolean hasFocus) {
        super.getTreeCellRendererComponent(
                tree, value, sel,
                expanded, leaf, row,
                hasFocus);
        setOpenIcon(new ImageIcon(Images.get("page.gif")));
        setClosedIcon(new ImageIcon(Images.get("page.gif")));
        setLeafIcon(new ImageIcon(Images.get("page.gif")));
        return this;
    }

}
