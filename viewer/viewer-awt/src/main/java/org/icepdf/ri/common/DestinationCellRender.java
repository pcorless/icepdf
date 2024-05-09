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
package org.icepdf.ri.common;

import org.icepdf.ri.images.IconPack;
import org.icepdf.ri.images.Images;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

/**
 * DestinationCellRender builds the default look and feel for a destination tree node.
 *
 * @since 6.3
 */
@SuppressWarnings("serial")
public class DestinationCellRender extends DefaultTreeCellRenderer {

    protected static final Icon IMAGE_ICON = Images.getSingleIcon("page", IconPack.Variant.NONE, Images.IconSize.TINY);

    @Override
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

        setOpenIcon(IMAGE_ICON);
        setClosedIcon(IMAGE_ICON);
        setLeafIcon(IMAGE_ICON);

        return this;
    }

}
