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
 * Tests editing a name tree: adding, renaming and removing a named destination.
 * <p>
 * This is what the outline editor drives, and unlike a lookup it has to keep the tree's invariants
 * intact - the entries of a node stay sorted by name, its {@code /Limits} keep bounding what it
 * holds, and every object it touches is handed to the state manager so the change is actually
 * written.  Breaking the sort or the limits does not fail here; it fails later, as a lookup that
 * cannot find a destination that is plainly in the file.
 * <p>
 * A real document stands behind these tests because the edits allocate object numbers.
 */
public class NameTreeEditTest {

    private Library library;

    /**
     * A document's library, with a state manager able to allocate object numbers.
     */
    private Library documentLibrary() throws Exception {
        if (library == null) {
            Document document = new Document();
            document.setInputStream(
                    NameTreeEditTest.class.getResourceAsStream("/redaction/simple_tj.pdf"),
                    "simple_tj.pdf");
            library = document.getCatalog().getLibrary();
        }
        return library;
    }

    /**
     * A destination pointing at page 0 of the document.
     */
    private Destination destination(float top) throws Exception {
        Library library = documentLibrary();
        return new Destination(library, new ArrayList<>(Arrays.asList(
                library.getCatalog().getPageTree().getPageReference(0),
                Destination.TYPE_XYZ, 0f, top, 1f)));
    }

    /**
     * An empty name tree, as a document with no named destinations yet has.
     */
    private NameTree emptyTree() throws Exception {
        NameTree nameTree = new NameTree(documentLibrary(), new DictionaryEntries());
        nameTree.init();
        return nameTree;
    }

    /**
     * The names held anywhere in the tree, in the order they are stored.
     */
    private static List<String> namesOf(NameTree nameTree) {
        List<String> names = new ArrayList<>();
        List<?> namesAndValues = nameTree.getNamesAndValues();
        if (namesAndValues != null) {
            for (int i = 0; i < namesAndValues.size(); i += 2) {
                names.add(namesAndValues.get(i).toString());
            }
        }
        return names;
    }

    // ------------------------------------------------------------------
    // adding
    // ------------------------------------------------------------------

    @DisplayName("the first name added to an empty tree builds the tree around it")
    @Test
    public void addToEmptyTree() throws Exception {
        NameTree nameTree = emptyTree();
        assertTrue(nameTree.addNameNode("chapter.1", destination(700f)));
        assertNotNull(nameTree.searchName("chapter.1"), "the name should be findable straight away");
    }

    @DisplayName("further names are added to the existing node and are findable")
    @Test
    public void addSeveralNames() throws Exception {
        NameTree nameTree = emptyTree();
        nameTree.addNameNode("chapter.1", destination(700f));
        assertTrue(nameTree.addNameNode("chapter.2", destination(600f)));
        assertTrue(nameTree.addNameNode("chapter.3", destination(500f)));

        assertNotNull(nameTree.searchName("chapter.1"));
        assertNotNull(nameTree.searchName("chapter.2"));
        assertNotNull(nameTree.searchName("chapter.3"));
    }

    @DisplayName("names added out of order are stored in order")
    @Test
    public void additionsAreSorted() throws Exception {
        // The lookup is a binary search, so an unsorted node makes its own entries unfindable.
        NameTree nameTree = emptyTree();
        nameTree.addNameNode("bravo", destination(700f));
        nameTree.addNameNode("delta", destination(600f));
        nameTree.addNameNode("alpha", destination(500f));
        nameTree.addNameNode("charlie", destination(400f));

        assertEquals(Arrays.asList("alpha", "bravo", "charlie", "delta"), namesOf(nameTree));
        for (String name : namesOf(nameTree)) {
            assertNotNull(nameTree.searchName(name), "cannot find " + name + " after sorting");
        }
    }

    @DisplayName("a name that is already in the tree is not added twice")
    @Test
    public void duplicateName() throws Exception {
        NameTree nameTree = emptyTree();
        nameTree.addNameNode("chapter.1", destination(700f));
        assertFalse(nameTree.addNameNode("chapter.1", destination(600f)),
                "a duplicate name has to be refused, not silently shadowed");
        assertEquals(1, namesOf(nameTree).size());
    }

    // ------------------------------------------------------------------
    // renaming
    // ------------------------------------------------------------------

    @DisplayName("a name can be changed, and the tree finds it under the new one only")
    @Test
    public void renameName() throws Exception {
        NameTree nameTree = emptyTree();
        nameTree.addNameNode("chapter.1", destination(700f));

        assertTrue(nameTree.updateNameNode("chapter.1", "introduction", destination(700f)));
        assertNotNull(nameTree.searchName("introduction"));
        assertNull(nameTree.searchName("chapter.1"), "the old name should be gone");
    }

    @DisplayName("a rename that moves a name in the sort order leaves the node sorted")
    @Test
    public void renameResorts() throws Exception {
        NameTree nameTree = emptyTree();
        nameTree.addNameNode("alpha", destination(700f));
        nameTree.addNameNode("bravo", destination(600f));
        nameTree.addNameNode("charlie", destination(500f));

        // "alpha" becomes "zulu", which belongs at the other end of the node
        assertTrue(nameTree.updateNameNode("alpha", "zulu", destination(700f)));
        assertEquals(Arrays.asList("bravo", "charlie", "zulu"), namesOf(nameTree));
        assertNotNull(nameTree.searchName("zulu"));
        assertNotNull(nameTree.searchName("bravo"));
    }

    @DisplayName("a destination can be changed without changing its name")
    @Test
    public void updateDestinationOnly() throws Exception {
        NameTree nameTree = emptyTree();
        nameTree.addNameNode("chapter.1", destination(700f));
        assertTrue(nameTree.updateNameNode("chapter.1", "chapter.1", destination(300f)));
        assertNotNull(nameTree.searchName("chapter.1"));
    }

    @DisplayName("renaming to a name already in the tree is refused")
    @Test
    public void renameCollision() throws Exception {
        // Allowing it would leave two entries with one name, one of which is unreachable.
        NameTree nameTree = emptyTree();
        nameTree.addNameNode("alpha", destination(700f));
        nameTree.addNameNode("bravo", destination(600f));

        assertFalse(nameTree.updateNameNode("alpha", "bravo", destination(700f)));
        assertNotNull(nameTree.searchName("alpha"), "the failed rename must not have removed it");
    }

    @DisplayName("renaming a name that is not there does nothing and says so")
    @Test
    public void renameMissingName() throws Exception {
        NameTree nameTree = emptyTree();
        nameTree.addNameNode("alpha", destination(700f));
        assertFalse(nameTree.updateNameNode("nothing", "something", destination(700f)));
        assertNull(nameTree.searchName("something"));
    }

    // ------------------------------------------------------------------
    // deleting
    // ------------------------------------------------------------------

    @DisplayName("a deleted name is no longer found, and its neighbours still are")
    @Test
    public void deleteName() throws Exception {
        NameTree nameTree = emptyTree();
        nameTree.addNameNode("alpha", destination(700f));
        nameTree.addNameNode("bravo", destination(600f));
        nameTree.addNameNode("charlie", destination(500f));

        assertTrue(nameTree.deleteNode("bravo"));
        assertNull(nameTree.searchName("bravo"));
        assertNotNull(nameTree.searchName("alpha"), "deleting one name must not disturb the others");
        assertNotNull(nameTree.searchName("charlie"));
        assertEquals(Arrays.asList("alpha", "charlie"), namesOf(nameTree));
    }

    @DisplayName("the first and last names of a node can be deleted too")
    @Test
    public void deleteEdgeNames() throws Exception {
        NameTree nameTree = emptyTree();
        nameTree.addNameNode("alpha", destination(700f));
        nameTree.addNameNode("bravo", destination(600f));
        nameTree.addNameNode("charlie", destination(500f));

        assertTrue(nameTree.deleteNode("alpha"));
        assertTrue(nameTree.deleteNode("charlie"));
        assertEquals(Arrays.asList("bravo"), namesOf(nameTree));
        assertNotNull(nameTree.searchName("bravo"));
    }

    @DisplayName("deleting a name that is not there does nothing and says so")
    @Test
    public void deleteMissingName() throws Exception {
        NameTree nameTree = emptyTree();
        nameTree.addNameNode("alpha", destination(700f));
        assertFalse(nameTree.deleteNode("nothing"));
        assertEquals(1, namesOf(nameTree).size());
    }

    @DisplayName("emptying a tree one name at a time leaves it searchable and empty")
    @Test
    public void deleteEveryName() throws Exception {
        NameTree nameTree = emptyTree();
        nameTree.addNameNode("alpha", destination(700f));
        nameTree.addNameNode("bravo", destination(600f));

        assertTrue(nameTree.deleteNode("alpha"));
        assertTrue(nameTree.deleteNode("bravo"));
        assertTrue(namesOf(nameTree).isEmpty());
        assertNull(nameTree.searchName("alpha"));
    }

    // ------------------------------------------------------------------
    // finding by page
    // ------------------------------------------------------------------

    @DisplayName("every destination pointing at a page can be found from the page")
    @Test
    public void findDestinationsForPage() throws Exception {
        // This is how the outline panel shows what points at the page being viewed.
        NameTree nameTree = emptyTree();
        nameTree.addNameNode("alpha", destination(700f));
        nameTree.addNameNode("bravo", destination(600f));

        Reference page = documentLibrary().getCatalog().getPageTree().getPageReference(0);
        List<Destination> destinations = nameTree.findDestinations(page);
        assertEquals(2, destinations.size());
    }

    @DisplayName("a page nothing points at yields an empty list, not null")
    @Test
    public void findDestinationsForUnreferencedPage() throws Exception {
        NameTree nameTree = emptyTree();
        nameTree.addNameNode("alpha", destination(700f));
        List<Destination> destinations = nameTree.findDestinations(new Reference(9999, 0));
        assertNotNull(destinations);
        assertTrue(destinations.isEmpty());
    }
}
