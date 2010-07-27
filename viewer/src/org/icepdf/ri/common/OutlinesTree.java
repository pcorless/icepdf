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
package org.icepdf.ri.common;

import org.icepdf.ri.images.Images;

import javax.swing.*;
import javax.swing.text.Position;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * OutlinesTree is a JTree derivative whose nodes are OutlineItemTreeNode objects,
 * each of which refers to an OutlineItem
 *
 * @author Mark Collette
 * @see org.icepdf.ri.common.OutlineItemTreeNode
 * @see org.icepdf.core.pobjects.OutlineItem
 * @since 2.0
 */
public class OutlinesTree extends JTree {
    public OutlinesTree() {
        getSelectionModel().setSelectionMode(
                TreeSelectionModel.SINGLE_TREE_SELECTION);
        // change the look & feel of the jtree
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setOpenIcon(new ImageIcon(Images.get("page.gif")));
        renderer.setClosedIcon(new ImageIcon(Images.get("page.gif")));
        renderer.setLeafIcon(new ImageIcon(Images.get("page.gif")));
        setCellRenderer(renderer);

        setModel(null);
        setRootVisible(true);
        setScrollsOnExpand(true);
        // old font was Arial with is no go for linux.
        setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 13));
        setRowHeight(18);
    }

    public TreePath getNextMatch(String prefix, int startingRow, Position.Bias bias) {
        // this method body is the same as JTree's implementation but has a check
        // for null text object, which causes a null pointer error in JDK 1.4
        int max = getRowCount();
        if (prefix == null) {
            throw new IllegalArgumentException();
        }
        if (startingRow < 0 || startingRow >= max) {
            throw new IllegalArgumentException();
        }
        prefix = prefix.toUpperCase();

        // start search from the next/previous element from the
        // selected element
        int increment = (bias == Position.Bias.Forward) ? 1 : -1;
        int row = startingRow;
        do {
            TreePath path = getPathForRow(row);
            String text = convertValueToText(
                    path.getLastPathComponent(), isRowSelected(row),
                    isExpanded(row), true, row, false);

            // Added check for null text to avoid nasty output
            if (text != null && text.toUpperCase().startsWith(prefix)) {
                return path;
            }
            row = (row + increment + max) % max;
        } while (row != startingRow);
        return null;
    }
}
