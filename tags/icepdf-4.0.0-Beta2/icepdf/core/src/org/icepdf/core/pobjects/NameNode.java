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
 * NameNode used in building a name tree.
 *
 * @since 4.0
 */
public class NameNode extends Dictionary {
    private static Object NOT_FOUND = new Object();
    private static Object NOT_FOUND_IS_LESSER = new Object();
    private static Object NOT_FOUND_IS_GREATER = new Object();

    private boolean namesAreDecrypted;
    private Vector namesAndValues;
    private Vector kidsReferences;
    private Vector kidsNodes;
    private String lowerLimit;
    private String upperLimit;

    /**
     * @param l
     * @param h
     */
    public NameNode(Library l, Hashtable h) {
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

     

    public boolean isEmpty(){
        return kidsNodes.size() == 0;
    }

    public boolean hasLimits(){
        return library.getObject(entries, "Limits") != null;
    }

    public Vector getNamesAndValues() {
        return namesAndValues;
    }

    public Vector getKidsReferences() {
        return kidsReferences;
    }

    public Vector getKidsNodes() {
        return kidsNodes;
    }

    public String getLowerLimit() {
        return lowerLimit;
    }

    public String getUpperLimit() {
        return upperLimit;
    }

    private void ensureNamesDecrypted() {
        if (namesAreDecrypted)
            return;
        namesAreDecrypted = true;
        // We need to look at each key and encrypt any Text objects which
        // is every second object
        for (int i = 0; i < namesAndValues.size(); i += 2) {
            namesAndValues.set(i,
                    decryptIfText(namesAndValues.get(i)));
        }
    }

    /**
     * Decyptes the node String object and returns a String value of the node
     * which is used to find names in the name tree. We only do this once
     * for the notes names vector.
     * @param tmp
     * @return
     */
    private String decryptIfText(Object tmp) {
        if (tmp instanceof StringObject) {
            StringObject nameText = (StringObject) tmp;
            return nameText.getDecryptedLiteralString(library.securityManager);
        }
        else if (tmp instanceof String){
            return (String)tmp;
        }
        return null;
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

    public NameNode getNode(int index) {
        NameNode n = (NameNode) kidsNodes.get(index);
        if (n == null) {
            Reference r = (Reference) kidsReferences.get(index);
            Hashtable nh = (Hashtable) library.getObject(r);
            n = new NameNode(library, nh);
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
