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

import javax.swing.*;
import javax.swing.tree.TreeSelectionModel;

/**
 * Name tree component to represent a PDF documents name tree data structure.
 *
 * @since 4.0
 */
@SuppressWarnings("serial")
public class NameJTree extends JTree {
    public NameJTree() {
        getSelectionModel().setSelectionMode(
                TreeSelectionModel.SINGLE_TREE_SELECTION);
        // change the look & feel of the jtree
        setCellRenderer(new DestinationCellRender());

        setModel(null);
        setRootVisible(true);
        setScrollsOnExpand(true);
    }
}
