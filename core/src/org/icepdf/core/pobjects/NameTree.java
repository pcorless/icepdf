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
package org.icepdf.core.pobjects;

import org.icepdf.core.util.Library;

import java.util.Hashtable;

/**
 * <p>The <code>NameTree</code> class is similar to the <code>Dictionary</code> class in that
 * it associates keys and values, but it does this in a different way.  The keys
 * in a NameTree are strings and are ordered and the values of the associated
 * keys may be an object of any type.</p>
 * <p/>
 * <p>The <code>NameTree</code> class is primarily used to store named destinations
 * accessible via the document's Catalog.  This class is very simple with only
 * one method which is responsible searching for the given key.</p>
 *
 * @since 1.0
 */
public class NameTree extends Dictionary {

    // root node of the tree of names.
    private NameNode root;

    /**
     * Creates a new instance of a NameTree.
     *
     * @param l document library.
     * @param h NameTree dictionary entries.
     */
    public NameTree(Library l, Hashtable h) {
        super(l, h);
    }

    /**
     * Initiate the NameTree.
     */
    public void init() {
        if (inited) {
            return;
        }
        root = new NameNode(library, entries);
        inited = true;
    }

    /**
     * Dispose the NameTree.
     */
    public void dispose() {
        root.dispose();
    }

    /**
     * Searches for the given key in the name tree.  If the key is found, its
     * associated object is returned.  It is important to know the context in
     * which a search is made as the name tree can hold objects of any type.
     *
     * @param key key to look up in name tree.
     * @return the associated object value if found; null, otherwise.
     */
    public Object searchName(String key) {
        return root.searchName(key);
    }

    public NameNode getRoot() {
        return root;
    }
}
