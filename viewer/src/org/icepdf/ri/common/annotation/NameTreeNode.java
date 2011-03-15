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
package org.icepdf.ri.common.annotation;

import org.icepdf.core.pobjects.NameNode;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.StringObject;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Vector;
import java.util.ResourceBundle;
import java.text.MessageFormat;

/**
 * Name tree node.
 */
public class NameTreeNode extends DefaultMutableTreeNode {

    // we can either be a intermediate node
    private NameNode item;
    // or a leaf node but not both.
    private StringObject name;
    private Reference reference;

    private ResourceBundle messageBundle;
    private MessageFormat formatter;

    private boolean rootNode;
    private boolean intermidiatNode;
    private boolean leaf;

    private boolean loadedChildren;

    /**
     * Creates a new instance of an OutlineItemTreeNode
     *
     * @param item Contains PDF Outline item data
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
            intermidiatNode = true;
            // setup a patterned message
            Object[] messageArguments = {
                    item.getLowerLimit(),
                    item.getUpperLimit()
            };
            if (formatter == null){
                formatter = new MessageFormat(messageBundle.getString(
                        "viewer.utilityPane.action.dialog.goto.nameTree.branch.label"));
            }
            setUserObject(formatter.format(messageArguments));
        }
    }

    public NameTreeNode(StringObject name, Reference ref) {
        super();
        leaf = true;
        this.name = name;
        this.reference = ref;
        setUserObject(name);
    }


    public void recursivelyClearOutlineItems() {
        item = null;
        if (loadedChildren) {
            int count = getChildCount();
            for (int i = 0; i < count; i++) {
                NameTreeNode node = (NameTreeNode) getChildAt(i);
                node.recursivelyClearOutlineItems();
            }
        }
    }

    public StringObject getName() {
        return name;
    }

    public Reference getReference() {
        return reference;
    }

    public boolean isRootNode() {
        return rootNode;
    }

    public boolean isIntermidiatNode() {
        return intermidiatNode;
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

    /**
     * Only load children as needed, so don't have to load
     * OutlineItems that the user has not even browsed to
     */
    private void ensureChildrenLoaded() {
        if (!loadedChildren && (intermidiatNode || rootNode)) {
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
            if (item.getNamesAndValues() != null){
                Vector namesAndValues = item.getNamesAndValues();
                StringObject name;
                Reference ref;
                for(int i = 0,max =namesAndValues.size(); i < max; i += 2 ){
                    name = (StringObject)namesAndValues.get(i);
                    ref = (Reference)namesAndValues.get(i+1);
                    add(new NameTreeNode(name, ref));
                }
            }
        }
    }
}



