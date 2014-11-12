/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
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
package org.icepdf.core.pobjects;

import org.icepdf.core.util.Library;

import java.util.HashMap;

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
    public NameTree(Library l, HashMap h) {
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
