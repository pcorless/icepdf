/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * <p>The <code>NameTree</code> class is similar to the <code>Dictionary</code> class in that
 * it associates keys and values, but it does this in a different way.  The keys
 * in a NameTree are strings and are ordered and the values of the associated
 * keys may be an object of any type.</p>
 * <br>
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
     * Depth fist traversal of the the tree returning a list of the name and
     * reference values of the leafs in the tree.
     *
     * @return list of all name and corresponding references.
     */
    public List getNamesAndValues() {
        if (root != null) {
            ArrayList<Object> namesAndValues = new ArrayList<>();
            // single root, just return the list.
            if (root.getNamesAndValues() != null) {
                namesAndValues.addAll(root.getNamesAndValues());
                return namesAndValues;
            }
            // depth first traversal to get the names leaves off the kits.
            else if (root.getKidsNodes() != null) {
                for (NameNode node : root.getKidsNodes()) {
                    namesAndValues.addAll(getNamesAndValues(node));
                }
                return namesAndValues;
            }
        }
        return null;
    }

    /**
     * Helper method to do the recursive dive to get all the names and values
     * from the tree.
     *
     * @param nameNode Name node to check for names and nodes.
     * @return found names and values for the given node.
     */
    private List getNamesAndValues(NameNode nameNode) {
        // leaf node.
        if (nameNode.getNamesAndValues() != null) {
            return nameNode.getNamesAndValues();
        }
        // intermediary node.
        else {
            ArrayList<Object> namesAndValues = new ArrayList<>();
            for (NameNode node : nameNode.getKidsNodes()) {
                namesAndValues.addAll(getNamesAndValues(node));
            }
            return namesAndValues;
        }
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
     * The addition of a node to a name tree is relatively complex but because we are assuming this will only be used
     * in limit capacity as name tree's best to be created a PDF encoder.
     *
     * @param newName     label for name node.
     * @param destination associated destination
     * @return true if insertion was successful, false otherwise.
     */
    public boolean addNameNode(String newName, Destination destination) {
        StateManager stateManager = library.getStateManager();
        if (root.entries.size() == 0) {
            // add the destination as it's own object.
            destination.entries = destination.getRawDestination();
            destination.setPObjectReference(stateManager.getNewReferencNumber());
            stateManager.addChange(new PObject(destination, destination.getPObjectReference()));

            // create a name node to attach to the kids.
            HashMap nameNodeEntries = new HashMap();
            ArrayList limits = new ArrayList();
            limits.add(newName);
            limits.add(newName);
            ArrayList names = new ArrayList();
            names.add(newName);
            names.add(destination.getPObjectReference());
            nameNodeEntries.put(NameNode.LIMITS_KEY, limits);
            nameNodeEntries.put(NameNode.NAMES_KEY, names);
            NameNode nameNode = new NameNode(library, nameNodeEntries);
            nameNode.setPObjectReference(stateManager.getNewReferencNumber());
            stateManager.addChange(new PObject(nameNode, nameNode.getPObjectReference()));

            // first insertion so we don't need to look for any other nodes. just setup kids reference and child
            ArrayList kids = new ArrayList();
            kids.add(nameNode.getPObjectReference());
            root.setKids(kids);
            return true;
        } else {
            Object found = root.searchName(newName);
            // indicate that name node already exists.
            if (found != null) return false;
            // other wise we need to figure out which child to insert into.
            Object tmp = root.searchForInsertionNode(newName);
            if (tmp != null && tmp instanceof NameNode) {
                // add the new node and update limits.
                destination.entries = destination.getRawDestination();
                destination.setPObjectReference(stateManager.getNewReferencNumber());
                stateManager.addChange(new PObject(destination, destination.getPObjectReference()));
                // assign the names destination.
                NameNode nameNode = (NameNode) tmp;
                nameNode.addNameAndValue(newName, destination.getPObjectReference());
                // store the new object.
                stateManager.addChange(new PObject(nameNode, nameNode.getPObjectReference()));
                return true;
            } else if (tmp != null) {
                // add a new kid entry at the start or end of, with wide a-z limit
                if (tmp.equals(NameNode.NOT_FOUND_IS_GREATER)) {
                    // add the new node and update limits.
                    destination.entries = destination.getRawDestination();
                    destination.setPObjectReference(stateManager.getNewReferencNumber());
                    stateManager.addChange(new PObject(destination, destination.getPObjectReference()));
                    // going to cheat a bit here,  we are going to add the word to the last element of the last
                    // kid and update the upper limit.
                    NameNode lastNode = root.getKidsNodes().get(root.getKidsNodes().size() - 1);
                    lastNode.addNameAndValue(newName, destination.getPObjectReference());
                    // store the new object.
                    stateManager.addChange(new PObject(lastNode, lastNode.getPObjectReference()));
                    return true;
                }
            }
        }
        return false;
    }


    public boolean updateNameNode(String oldName, String newName, Destination destination) {
        if (root != null) {
            Object found = root.searchName(oldName);
            Object foundNew = null;
            // make sure we can update the destination if the name didn't change.
            if (!oldName.equals(newName)) {
                foundNew = root.searchName(newName);
            }
            // update if the new name isn't in play and we found the old node.
            if (foundNew == null && found != null && found instanceof PObject) {
                Reference reference = ((PObject) found).getReference();
                Object tmp = library.getObject(reference);
                NameNode nameNode = null;
                if (tmp instanceof HashMap) {
                    nameNode = new NameNode(library, (HashMap) tmp);
                } else if (tmp instanceof NameNode) {
                    nameNode = (NameNode) tmp;
                }
                nameNode.setPObjectReference(reference);
                List nameValues = nameNode.getNamesAndValues();
                // find our name and remove it and the value.
                for (int i = 0; i < nameValues.size(); i += 2) {
                    Object name = nameValues.get(i);
                    if (name instanceof String) {
                        if (oldName.equals(name)) {
                            // update the name
                            nameValues.set(i, newName);
                            // update destination
                            Object value = nameValues.get(i + 1);
                            // we have an indirect reference so we need to update it as well with new destination data.
                            // we assume that this is always an implicit destination and not a named destination
                            if (value instanceof Reference) {
                                HashMap destMap = destination.getRawDestination();
                                library.getStateManager().addChange(new PObject(destMap, (Reference) value));
                            } else if (value instanceof List) {
                                List destList = destination.getRawListDestination();
                                nameValues.set(i + 1, destList);
                            }

                            // makes sure we have some names to write back.
                            nameNode.setNamesAndValues(nameValues);
                            // reorder and update limits if needed
                            checkOrderAndLimits(nameNode);
                            library.getStateManager().addChange(new PObject(nameNode, reference));
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * An attempt is made to remove the node with the given name.  If found the entries parent is updated appropriately
     * and the modified object is added to the state manager so the change can be persisted.
     *
     * @param name name node name to remove from nameTree hierarchy.
     * @return true if the name was found and removed, otherwise false.
     */
    public boolean deleteNode(String name) {
        if (root != null) {
            Object found = root.searchName(name);
            // we found the item and its parent's reference
            if (found != null && found instanceof PObject) {
                Reference reference = ((PObject) found).getReference();
                Object tmp = library.getObject(reference);
                // we'll remove the name from the parent names tree and orphan the destination if indirect.
                NameNode nameNode = null;
                if (tmp instanceof HashMap) {
                    nameNode = new NameNode(library, (HashMap) tmp);
                } else if (tmp instanceof NameNode) {
                    nameNode = (NameNode) tmp;
                }
                nameNode.setPObjectReference(reference);
                List nameValues = nameNode.getNamesAndValues();
                // find our name and remove it and the value.
                for (int i = 0; i < nameValues.size(); i += 2) {
                    Object value = nameValues.get(i);
                    if (value instanceof String) {
                        if (name.equals(value)) {
                            nameValues.remove(i);
                            nameValues.remove(i);
                            break;
                        }
                    }
                }
                // makes sure we have some names to write back.
                nameNode.setNamesAndValues(nameValues);
                library.getStateManager().addChange(new PObject(nameNode, reference));
                return true;
            }
        }
        return false;
    }

    private void checkOrderAndLimits(NameNode nameNode) {
        List namesAndValues = nameNode.getNamesAndValues();
        int size = namesAndValues.size();
        // sor the list
        if (size > 2) {
            // a bit brutal but we pack the name value pairs.
            Pair[] sortable = new Pair[size / 2];
            for (int i = 0, j = 0; i < size; i += 2, j++) {
                sortable[j] = new Pair((String) namesAndValues.get(i), namesAndValues.get(i + 1));
            }
            Arrays.sort(sortable);
            // expand the name values for storage.
            namesAndValues.clear();
            for (Pair pair : sortable) {
                namesAndValues.add(pair.getName());
                namesAndValues.add(pair.getValue());
            }
        }
        if (size > 0) {
            // updates the pairs and resets the limits.
            nameNode.setNamesAndValues(namesAndValues);
        }

    }

    /**
     * Searches for names that have the given page number in the name tree destination.
     *
     * @param pageReference page reference to find.
     * @return list of destinations for the given page index, an empty list if no entries are found.
     */
    public ArrayList<Destination> findDestinations(Reference pageReference) {
        return searchForPageIndex(root, pageReference);
    }

    public ArrayList<Destination> searchForPageIndex(NameNode rootNode, Reference pageReference) {
        ArrayList<Destination> destinations = new ArrayList<>();
        List nameValues = rootNode.getNamesAndValues();
        if (rootNode.getKidsReferences() != null) {
            List<NameNode> kids = rootNode.getKidsNodes();
            for (NameNode kid : kids) {
                ArrayList<Destination> found = searchForPageIndex(kid, pageReference);
                if (found != null) {
                    destinations.addAll(found);
                }
            }
        }
        if (nameValues != null && nameValues.size() > 0) {
            for (int i = 0; i < nameValues.size(); i += 2) {
                Object name = library.getObject(nameValues.get(i));
                Object value = nameValues.get(i + 1);
                Object tmp = library.getObject(value);
                // D-> ref -> Destination
                if (tmp instanceof HashMap) {
                    HashMap dictionary = (HashMap) tmp;
                    Object obj = dictionary.get(Destination.D_KEY);
                    if (obj instanceof List) {
                        Destination dest = new Destination(library, obj);
                        if (pageReference.equals(dest.getPageReference())) {
                            destinations.add(dest);
                            if (name != null) dest.setNamedDestination(name.toString());
                        }
                    }
                } else if (tmp instanceof Destination) {
                    Destination dest = (Destination) tmp;
                    if (dest.getPageReference().equals(pageReference)) {
                        destinations.add(dest);
                        if (name != null) dest.setNamedDestination(name.toString());
                    }
                }
            }
        }
        return destinations;
    }

    public NameNode getRoot() {
        return root;
    }

    class Pair implements Comparable<Pair> {
        String name;
        Object value;

        Pair(String name, Object value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public Object getValue() {
            return value;
        }

        @Override
        public int compareTo(Pair o) {
            return name.compareTo(o.name);
        }

    }
}
