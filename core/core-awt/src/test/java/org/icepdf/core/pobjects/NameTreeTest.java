/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.pobjects;

import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the name tree: the sorted, balanced structure a PDF looks names up in.
 * <p>
 * It is how every named destination resolves, which makes it how most links in a document find
 * their page.  The lookup is a binary search over string keys guided by each node's declared
 * {@code /Limits}, so the cases that matter are the boundaries - a name equal to a limit, a name
 * that falls in the gap between two sibling nodes, a name past either end - and a tree deep enough
 * to have to descend.  A lookup that answers "not found" for a name that is present is a link that
 * silently does nothing.
 */
public class NameTreeTest {

    private final Library library = new Library();
    private int nextObjectNumber = 1;

    /**
     * Registers an object with the library so a {@link Reference} to it resolves.
     *
     * @param object object to register
     * @return its reference
     */
    private Reference register(Object object) {
        Reference reference = new Reference(nextObjectNumber++, 0);
        library.addObject(object, reference);
        return reference;
    }

    /**
     * A leaf node holding the given name/value pairs, in order.
     *
     * @param namesAndValues alternating name and value
     * @return the node's entries
     */
    private DictionaryEntries leaf(Object... namesAndValues) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(NameNode.NAMES_KEY, new ArrayList<>(Arrays.asList(namesAndValues)));
        if (namesAndValues.length > 0) {
            entries.put(NameNode.LIMITS_KEY, new ArrayList<>(Arrays.asList(
                    namesAndValues[0], namesAndValues[namesAndValues.length - 2])));
        }
        return entries;
    }

    /**
     * An intermediate node over the given children, with limits spanning them.
     *
     * @param lower    lowest name anywhere below
     * @param upper    highest name anywhere below
     * @param children child node entries
     * @return the node's entries
     */
    private DictionaryEntries branch(String lower, String upper, DictionaryEntries... children) {
        List<Object> kids = new ArrayList<>();
        for (DictionaryEntries child : children) {
            kids.add(register(new NameNode(library, child)));
        }
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(NameNode.KIDS_KEY, kids);
        entries.put(NameNode.LIMITS_KEY, new ArrayList<>(Arrays.asList(lower, upper)));
        return entries;
    }

    private NameTree tree(DictionaryEntries root) {
        NameTree nameTree = new NameTree(library, root);
        nameTree.init();
        return nameTree;
    }

    // ------------------------------------------------------------------
    // a single leaf
    // ------------------------------------------------------------------

    @DisplayName("a flat tree finds every name it holds")
    @Test
    public void flatLookup() {
        NameTree nameTree = tree(leaf("alpha", 1, "bravo", 2, "charlie", 3));
        assertEquals(1, nameTree.searchName("alpha"));
        assertEquals(2, nameTree.searchName("bravo"));
        assertEquals(3, nameTree.searchName("charlie"));
    }

    @DisplayName("a name that is not in the tree is not found")
    @Test
    public void flatMiss() {
        NameTree nameTree = tree(leaf("alpha", 1, "charlie", 3));
        assertNull(nameTree.searchName("bravo"), "a name between two entries is still absent");
        assertNull(nameTree.searchName("aardvark"), "a name before the first entry");
        assertNull(nameTree.searchName("zulu"), "a name after the last entry");
        assertNull(nameTree.searchName("ALPHA"), "the search is case sensitive");
    }

    @DisplayName("the first and last names of a node are found, not skipped by the search")
    @Test
    public void boundaryNames() {
        // A binary search that closes its interval wrongly loses exactly these two.
        NameTree nameTree = tree(leaf("a", 1, "b", 2, "c", 3, "d", 4, "e", 5));
        assertEquals(1, nameTree.searchName("a"));
        assertEquals(5, nameTree.searchName("e"));
    }

    @DisplayName("an even and an odd number of entries both search correctly")
    @Test
    public void entryCountParity() {
        // The midpoint of an even-sized range rounds one way or the other; both have to work.
        NameTree even = tree(leaf("a", 1, "b", 2, "c", 3, "d", 4));
        for (String name : new String[]{"a", "b", "c", "d"}) {
            assertNotNull(even.searchName(name), "missing " + name);
        }
        NameTree odd = tree(leaf("a", 1, "b", 2, "c", 3));
        for (String name : new String[]{"a", "b", "c"}) {
            assertNotNull(odd.searchName(name), "missing " + name);
        }
    }

    @DisplayName("a tree with neither kids nor names finds nothing")
    @Test
    public void emptyTree() {
        NameNode node = new NameNode(library, new DictionaryEntries());
        assertNull(node.searchName("anything"));
        assertFalse(node.hasLimits());
    }

    @DisplayName("isEmpty asks whether a node has an empty /Kids, not whether it holds nothing")
    @Test
    public void isEmptyIsAboutKids() {
        // Worth pinning because the name suggests otherwise: a leaf, and a node with no arrays at
        // all, both answer false.  Nothing in the library calls this; a caller reading it as
        // "holds nothing" would skip neither.
        assertFalse(new NameNode(library, new DictionaryEntries()).isEmpty());
        assertFalse(new NameNode(library, leaf("alpha", 1)).isEmpty());

        DictionaryEntries noKids = new DictionaryEntries();
        noKids.put(NameNode.KIDS_KEY, new ArrayList<>());
        assertTrue(new NameNode(library, noKids).isEmpty());
    }

    // ------------------------------------------------------------------
    // a tree with children
    // ------------------------------------------------------------------

    @DisplayName("a two level tree descends into the child whose limits contain the name")
    @Test
    public void nestedLookup() {
        NameTree nameTree = tree(branch("alpha", "foxtrot",
                leaf("alpha", 1, "bravo", 2),
                leaf("charlie", 3, "delta", 4),
                leaf("echo", 5, "foxtrot", 6)));

        assertEquals(1, nameTree.searchName("alpha"));
        assertEquals(2, nameTree.searchName("bravo"));
        assertEquals(3, nameTree.searchName("charlie"));
        assertEquals(4, nameTree.searchName("delta"));
        assertEquals(5, nameTree.searchName("echo"));
        assertEquals(6, nameTree.searchName("foxtrot"));
    }

    @DisplayName("a name falling between two children is not found in either")
    @Test
    public void nestedGap() {
        // "cobra" sorts after the first child's limits and before the second's; descending into
        // the wrong one would either miss it or, worse, find a neighbour.
        NameTree nameTree = tree(branch("alpha", "delta",
                leaf("alpha", 1, "bravo", 2),
                leaf("charlie", 3, "delta", 4)));
        assertNull(nameTree.searchName("cobra"));
        assertNull(nameTree.searchName("aaa"));
        assertNull(nameTree.searchName("zzz"));
    }

    @DisplayName("a three level tree descends the whole way down")
    @Test
    public void deepLookup() {
        DictionaryEntries left = branch("alpha", "delta",
                leaf("alpha", 1, "bravo", 2),
                leaf("charlie", 3, "delta", 4));
        DictionaryEntries right = branch("echo", "hotel",
                leaf("echo", 5, "foxtrot", 6),
                leaf("golf", 7, "hotel", 8));
        NameTree nameTree = tree(branch("alpha", "hotel", left, right));

        assertEquals(1, nameTree.searchName("alpha"));
        assertEquals(4, nameTree.searchName("delta"));
        assertEquals(5, nameTree.searchName("echo"));
        assertEquals(8, nameTree.searchName("hotel"));
        assertNull(nameTree.searchName("india"));
    }

    @DisplayName("a node reports the limits it was given")
    @Test
    public void limits() {
        NameNode node = new NameNode(library, leaf("alpha", 1, "charlie", 3));
        assertTrue(node.hasLimits());
        assertEquals("alpha", node.getLowerLimit());
        assertEquals("charlie", node.getUpperLimit());
        assertFalse(node.isEmpty());
    }

    @DisplayName("the child nodes of a branch are reachable as nodes, not just references")
    @Test
    public void childNodes() {
        NameNode root = new NameNode(library, branch("alpha", "delta",
                leaf("alpha", 1, "bravo", 2),
                leaf("charlie", 3, "delta", 4)));
        assertEquals(2, root.getKidsReferences().size());
        List<NameNode> children = root.getKidsNodes();
        assertEquals(2, children.size());
        assertEquals("alpha", children.get(0).getLowerLimit());
        assertEquals("delta", children.get(1).getUpperLimit());
        assertEquals("charlie", root.getNode(1).getLowerLimit());
    }

    // ------------------------------------------------------------------
    // names as string objects
    // ------------------------------------------------------------------

    @DisplayName("names written as PDF strings are matched, not only Java strings")
    @Test
    public void stringObjectNames() {
        // A name tree's keys are PDF text strings in the file; they arrive as StringObjects and
        // have to be decrypted to plain text before they can be compared.
        NameTree nameTree = tree(leaf(
                new LiteralStringObject("alpha"), 1,
                new LiteralStringObject("bravo"), 2));
        assertEquals(1, nameTree.searchName("alpha"));
        assertEquals(2, nameTree.searchName("bravo"));
        assertNull(nameTree.searchName("charlie"));
    }

    // ------------------------------------------------------------------
    // reading the whole tree
    // ------------------------------------------------------------------

    @DisplayName("the flat list of names and values covers every leaf, in order")
    @Test
    public void namesAndValues() {
        NameTree nameTree = tree(branch("alpha", "delta",
                leaf("alpha", 1, "bravo", 2),
                leaf("charlie", 3, "delta", 4)));
        List<?> namesAndValues = nameTree.getNamesAndValues();
        assertNotNull(namesAndValues);
        assertEquals(8, namesAndValues.size(), "four pairs, flattened");
        assertEquals("alpha", namesAndValues.get(0).toString());
        assertEquals("delta", namesAndValues.get(6).toString());
    }
}
