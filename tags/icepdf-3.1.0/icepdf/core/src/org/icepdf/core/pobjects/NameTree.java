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
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
import java.util.Vector;

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
    private Node root;

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
        root = new Node(library, entries);
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

    /**
     * Utility class to aid in building a tree structure.
     */
    static class Node extends Dictionary {
        private static Object NOT_FOUND = new Object();
        private static Object NOT_FOUND_IS_LESSER = new Object();
        private static Object NOT_FOUND_IS_GREATER = new Object();

        private boolean namesAreDecrypted;
        private Vector namesAndValues;
        private Vector kidsReferences;
        private Vector kidsNodes;
        private Object lowerLimit;
        private Object upperLimit;

        /**
         * @param l
         * @param h
         */
        Node(Library l, Hashtable h) {
            super(l, h);
            Object o = library.getObject(entries, "Kids");
            if (o != null && o instanceof Vector) {
                Vector v = (Vector) o;
                kidsReferences = v;
                int sz = kidsReferences.size();
                if (sz > 0) {
                    kidsNodes = new Vector(sz);
                    kidsNodes.setSize(sz);
                }
            }
            namesAreDecrypted = false;
            o = library.getObject(entries, "Names");
            if (o != null && o instanceof Vector) {
                namesAndValues = (Vector) o;
            }
            o = library.getObject(entries, "Limits");
            if (o != null && o instanceof Vector) {
                Vector limits = (Vector) o;
                if (limits.size() >= 2) {
                    lowerLimit = decryptIfText(limits.get(0));
                    upperLimit = decryptIfText(limits.get(1));
                }
            }
        }

        private void ensureNamesDecrypted() {
            if (namesAreDecrypted)
                return;
            namesAreDecrypted = true;
            // We need to look at each key and encrypt any Text objects
            for (int i = 0; i < namesAndValues.size(); i += 2) {
                Object tmp = namesAndValues.get(i);
                tmp = decryptIfText(tmp);
                namesAndValues.set(i, tmp);
            }
        }

        private Object decryptIfText(Object tmp) {
            if (tmp instanceof StringObject) {
                StringObject nameText = (StringObject) tmp;
                String data = nameText.getDecryptedLiteralString(library.securityManager);
                return new LiteralStringObject(data);
            }
            return tmp;
        }

        /**
         * @param name
         * @return
         */
        Object searchName(String name) {
            Object ret = search(name);
            if (ret == NOT_FOUND || ret == NOT_FOUND_IS_LESSER || ret == NOT_FOUND_IS_GREATER) {
                ret = null;
            }
            return ret;
        }


        private Object search(String name) {
//System.out.println("search()  for: " + name + "  lowerLimit: " + lowerLimit + "  upperLimit: " + upperLimit + " " + name);
            if (kidsReferences != null) {
//System.out.print("search()  kids ... ");
                if (lowerLimit != null) {
                    int cmp = lowerLimit.toString().compareTo(name);
                    if (cmp > 0) {
//System.out.println("skLESSER");
                        return NOT_FOUND_IS_LESSER;
                    } else if (cmp == 0)
                        return getNode(0).search(name);
                }
                if (upperLimit != null) {
                    int cmp = upperLimit.toString().compareTo(name);
                    if (cmp < 0) {
//System.out.println("skGREATER");
                        return NOT_FOUND_IS_GREATER;
                    } else if (cmp == 0)
                        return getNode(kidsReferences.size() - 1).search(name);
                }
//System.out.println("skBETWEEN");

                return binarySearchKids(0, kidsReferences.size() - 1, name);
            } else if (namesAndValues != null) {
//System.out.print("search()  names ... ");
                int numNamesAndValues = namesAndValues.size();

                if (lowerLimit != null) {
                    int cmp = lowerLimit.toString().compareTo(name);
                    if (cmp > 0) {
//System.out.println("snLESSER");
                        return NOT_FOUND_IS_LESSER;
                    } else if (cmp == 0) {
                        ensureNamesDecrypted();
                        if (namesAndValues.get(0).toString().equals(name)) {
                            Object ob = namesAndValues.get(1);
                            if (ob instanceof Reference)
                                ob = library.getObject((Reference) ob);
                            return ob;
                        }
                    }
                }
                if (upperLimit != null) {
                    int cmp = upperLimit.toString().compareTo(name);
                    if (cmp < 0) {
//System.out.println("snGREATER");
                        return NOT_FOUND_IS_GREATER;
                    } else if (cmp == 0) {
                        ensureNamesDecrypted();
                        if (namesAndValues.get(numNamesAndValues - 2).toString().equals(name)) {
                            Object ob = namesAndValues.get(numNamesAndValues - 1);
                            if (ob instanceof Reference)
                                ob = library.getObject((Reference) ob);
                            return ob;
                        }
                    }
                }
//System.out.println("snBETWEEN");

                ensureNamesDecrypted();
                Object ret = binarySearchNames(0, numNamesAndValues - 1, name);
                if (ret == NOT_FOUND || ret == NOT_FOUND_IS_LESSER || ret == NOT_FOUND_IS_GREATER)
                    ret = null;
                return ret;
            }
            return null;
        }

        private Object binarySearchKids(int firstIndex, int lastIndex, String name) {
            if (firstIndex > lastIndex)
                return NOT_FOUND;
            int pivot = firstIndex + ((lastIndex - firstIndex) / 2);
            Object ret = getNode(pivot).search(name);
//System.out.print("binarySearchKids  [ " + firstIndex + ", " + lastIndex + " ]  pivot: " + pivot + "  name: " + name + " ... ");
            if (ret == NOT_FOUND_IS_LESSER) {
//System.out.println("kLESSER");
                return binarySearchKids(firstIndex, pivot - 1, name);
            } else if (ret == NOT_FOUND_IS_GREATER) {
//System.out.println("kGREATER");
                return binarySearchKids(pivot + 1, lastIndex, name);
            } else if (ret == NOT_FOUND) {
//System.out.println("kNOT FOUND");
                // This shouldn't happen, so is either a bug, or a miss coded PDF file
                for (int i = firstIndex; i <= lastIndex; i++) {
                    if (i == pivot)
                        continue;
                    Object r = getNode(i).search(name);
                    if (r != NOT_FOUND && r != NOT_FOUND_IS_LESSER && r != NOT_FOUND_IS_GREATER) {
                        ret = r;
                        break;
                    }
                }
            }
            return ret;
        }

        private Object binarySearchNames(int firstIndex, int lastIndex, String name) {
            if (firstIndex > lastIndex)
                return NOT_FOUND;
            int pivot = firstIndex + ((lastIndex - firstIndex) / 2);
            pivot &= 0xFFFFFFFE; // Clear LSB to ensure even index
//System.out.print("binarySearchNames  [ " + firstIndex + ", " + lastIndex + " ]  pivot: " + pivot + "  size: " + namesAndValues.size() + "  compare  " + name + " to " + namesAndValues.get(pivot).toString() + " ... ");
            int cmp = namesAndValues.get(pivot).toString().compareTo(name);
            if (cmp == 0) {
//System.out.println("nEQUAL");
                Object ob = namesAndValues.get(pivot + 1);
                if (ob instanceof Reference)
                    ob = library.getObject((Reference) ob);
                return ob;
            } else if (cmp > 0) {
//System.out.println("nLESSER");
                return binarySearchNames(firstIndex, pivot - 1, name);
            } else if (cmp < 0) {
//System.out.println("nGREATER");
                return binarySearchNames(pivot + 2, lastIndex, name);
            }
            return NOT_FOUND;
        }

        private Node getNode(int index) {
            Node n = (Node) kidsNodes.get(index);
            if (n == null) {
                Reference r = (Reference) kidsReferences.get(index);
                Hashtable nh = (Hashtable) library.getObject(r);
                n = new Node(library, nh);
                kidsNodes.set(index, n);
            }
            return n;
        }

        public void dispose() {
            if (namesAndValues != null)
                namesAndValues.clear();
            if (kidsReferences != null)
                kidsReferences.clear();
            if (kidsNodes != null)
                kidsNodes.clear();
        }
    }


}
