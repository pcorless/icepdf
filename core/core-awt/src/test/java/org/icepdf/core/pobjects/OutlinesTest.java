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

import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Outlines} is a thin wrapper whose one job is handing back the root {@link OutlineItem}.
 * These pin what decides that an outline exists, because the class used to look as though it
 * decided that itself and did not.
 */
public class OutlinesTest {

    private static DictionaryEntries entries(Object... keyValues) {
        DictionaryEntries entries = new DictionaryEntries();
        for (int i = 0; i < keyValues.length; i += 2) {
            entries.put(new Name((String) keyValues[i]), keyValues[i + 1]);
        }
        return entries;
    }

    @DisplayName("outline without /Count is still an outline")
    @Test
    public void missingCountStillYieldsRoot() {
        // /Count is optional in the wild: three documents in a 648 document corpus sample carry
        // /First without it.  This used to be gated on a boxed /Count being non-null, which only
        // ever worked because Library.getInt answers a primitive 0 for a missing key.
        Outlines outlines = new Outlines(new Library(), entries("First", new Reference(4, 0)));
        assertNotNull(outlines.getRootOutlineItem());
    }

    @DisplayName("outline with /Count yields a root item")
    @Test
    public void countPresentYieldsRoot() {
        Outlines outlines = new Outlines(new Library(),
                entries("Count", 3, "First", new Reference(4, 0), "Last", new Reference(9, 0)));
        assertNotNull(outlines.getRootOutlineItem());
    }

    @DisplayName("an outline with nothing in it reports empty rather than null")
    @Test
    public void emptyOutlineIsEmptyNotNull() {
        // the viewer relies on this: it asks for the root item and then uses isEmpty() to decide
        // whether to show the root node, so an empty outline must not come back as null
        Outlines outlines = new Outlines(new Library(), new DictionaryEntries());
        OutlineItem root = outlines.getRootOutlineItem();
        assertNotNull(root);
        assertTrue(root.isEmpty());
    }

    @DisplayName("null entries are normalised by Dictionary, so a root item still comes back")
    @Test
    public void nullEntriesAreNormalised() {
        // Dictionary substitutes an empty DictionaryEntries for a null one, which is the second
        // reason the old null guard could never fire.  Whether a document has an outline is
        // Catalog.getOutlines()'s decision, not this class's.
        Outlines outlines = new Outlines(new Library(), null);
        assertNotNull(outlines.getEntries());
        assertNotNull(outlines.getRootOutlineItem());
    }

    @DisplayName("creating an outline item without a state manager fails with a useful message")
    @Test
    public void createNewOutlineItemWithoutStateManager() {
        // a bare library has no state manager, and only the state manager can issue the reference
        // number a new item needs.  This used to surface as a NullPointerException naming
        // getNewReferenceNumber, which points the reader at the wrong object.
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> Outlines.createNewOutlineItem(new Library()));
        assertTrue(thrown.getMessage().contains("state manager"), thrown.getMessage());
    }

    @DisplayName("creating an outline item on a loaded document gives it a reference")
    @Test
    public void createNewOutlineItemGetsAReference() throws Exception {
        Document document = new Document();
        try (InputStream in = OutlinesTest.class.getResourceAsStream("/updater/R&D-05-Carbon.pdf")) {
            document.setInputStream(in, "R&D-05-Carbon.pdf");
            OutlineItem item = Outlines.createNewOutlineItem(document.getCatalog().getLibrary());
            assertNotNull(item.getPObjectReference(),
                    "a new outline item needs a reference; it is part of the per object encryption key");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("the root item inherits the outline dictionary's reference")
    @Test
    public void rootItemCarriesTheReference() {
        // the reference is part of the per object encryption key, so an authored title on this item
        // cannot be written back correctly without it
        Reference reference = new Reference(12, 0);
        Outlines outlines = new Outlines(new Library(), entries("Count", 1));
        outlines.setPObjectReference(reference);
        assertEquals(reference, outlines.getRootOutlineItem().getPObjectReference());
    }
}
