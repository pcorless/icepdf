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
package org.icepdf.ri.common;

import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.NameNode;
import org.icepdf.core.pobjects.StringObject;

import javax.swing.tree.DefaultMutableTreeNode;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Name tree node.
 */
@SuppressWarnings("serial")
public class NameTreeNode extends DefaultMutableTreeNode {

    // we can either be a intermediate node
    private NameNode item;
    // or a leaf node but not both.
    private StringObject name;
    private Object value;

    private ResourceBundle messageBundle;
    private MessageFormat formatter;

    private boolean rootNode;
    private boolean intermediateNode;
    private boolean leaf;

    private boolean loadedChildren;

    /**
     * Creates a new instance of an OutlineItemTreeNode
     *
     * @param item          Contains PDF Outline item data
     * @param messageBundle ri root message bundle, localized node text.
     */
    public NameTreeNode(NameNode item, ResourceBundle messageBundle) {
        super();
        this.item = item;
        this.messageBundle = messageBundle;
        if (!item.hasLimits()) {
            rootNode = true;
            setUserObject(messageBundle.getString(
                    "viewer.utilityPane.action.dialog.goto.nameTree.root.label"));
        } else {
            intermediateNode = true;
            // setup a patterned message
            Object[] messageArguments = {
                    item.getLowerLimit(),
                    item.getUpperLimit()
            };
            if (formatter == null) {
                formatter = new MessageFormat(messageBundle.getString(
                        "viewer.utilityPane.action.dialog.goto.nameTree.branch.label"));
            }
            setUserObject(formatter.format(messageArguments));
        }
    }

    public NameTreeNode(StringObject name, Object value) {
        super();
        leaf = true;
        this.name = name;
        this.value = value;
        setUserObject(name);
    }

    public StringObject getName() {
        return name;
    }

    public Object getReference() {
        return value;
    }

    public boolean isRootNode() {
        return rootNode;
    }

    public boolean isLeaf() {
        return leaf;
    }

    public void setRootNode(boolean rootNode) {
        this.rootNode = rootNode;
    }

    public int getChildCount() {
        ensureChildrenLoaded();
        return super.getChildCount();
    }

    @Override
    public Enumeration children() {
        ensureChildrenLoaded();
        return super.children();
    }

    /**
     * Only load children as needed, so don't have to load
     * OutlineItems that the user has not even browsed to
     */
    private void ensureChildrenLoaded() {
        if (!loadedChildren && (intermediateNode || rootNode)) {
            loadedChildren = true;

            // look for any kids.
            if (item.getKidsReferences() != null) {
                int count = item.getKidsReferences().size();
                for (int i = 0; i < count; i++) {
                    NameNode child = item.getNode(i);
                    NameTreeNode childTreeNode =
                            new NameTreeNode(child, messageBundle);
                    add(childTreeNode);
                }
            }
            // other wise we might have some leaf to add
            if (item.getNamesAndValues() != null) {
                List namesAndValues = item.getNamesAndValues();
                StringObject name;
                Object value;
                for (int i = 0, max = namesAndValues.size(); i < max; i += 2) {
                    Object tmp = namesAndValues.get(i);
                    if (tmp instanceof StringObject) {
                        name = (StringObject) tmp;
                    } else {
                        name = new LiteralStringObject((String) namesAndValues.get(i));
                    }
                    value = namesAndValues.get(i + 1);
                    add(new NameTreeNode(name, value));
                }
            }
        }
    }
}



