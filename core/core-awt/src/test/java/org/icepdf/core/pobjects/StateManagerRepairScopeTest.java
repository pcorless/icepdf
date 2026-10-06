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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The repair scope itself, at the level of the state manager.
 * <p>
 * These are the rules the rest of the annotation code leans on, so they are asserted directly
 * rather than inferred from a document: what the scope records, that it nests, that it is per
 * thread, that it unwinds on an exception, and - the one that used to be a plain map put - that a
 * repair can never quietly undo a user edit.
 */
public class StateManagerRepairScopeTest {

    private Document document;
    private StateManager stateManager;
    private Library library;

    @BeforeEach
    public void openDocument() throws Exception {
        document = new Document();
        document.setFile(Path.of("src/test/resources/annotations/text_field.pdf").toAbsolutePath().toString());
        library = document.getPageTree().getLibrary();
        stateManager = library.getStateManager();
    }

    @AfterEach
    public void closeDocument() {
        if (document != null) {
            document.dispose();
        }
    }

    private PObject object(int number) {
        return new PObject(new DictionaryEntries(), new Reference(number, 0));
    }

    private StateManager.Type typeOf(PObject pObject) {
        return stateManager.getChange(pObject.getReference()).getType();
    }

    @Test
    @DisplayName("outside a scope a change is a user edit")
    public void changeDefaultsToUserEdit() {
        PObject pObject = object(900);
        stateManager.addChange(pObject);

        assertEquals(StateManager.Type.CHANGE, typeOf(pObject));
        assertTrue(stateManager.hasUnsavedUserChanges());
    }

    @Test
    @DisplayName("inside a scope a change is a repair")
    public void changeInsideScopeIsRepair() {
        PObject pObject = object(901);
        stateManager.repairing(() -> stateManager.addChange(pObject));

        assertEquals(StateManager.Type.REPAIR, typeOf(pObject));
        assertFalse(stateManager.hasUnsavedUserChanges(), "a repair must not make the document look modified");
        assertTrue(stateManager.hasWritableChanges(), "but it still has to be written when a save happens");
    }

    @Test
    @DisplayName("scopes nest, and only the outermost exit ends the scope")
    public void scopesNest() {
        PObject inner = object(902);
        PObject afterInner = object(903);
        PObject afterOuter = object(904);

        stateManager.repairing(() -> {
            stateManager.repairing(() -> stateManager.addChange(inner));
            // still inside the outer scope
            stateManager.addChange(afterInner);
        });
        stateManager.addChange(afterOuter);

        assertEquals(StateManager.Type.REPAIR, typeOf(inner));
        assertEquals(StateManager.Type.REPAIR, typeOf(afterInner));
        assertEquals(StateManager.Type.CHANGE, typeOf(afterOuter));
        assertFalse(stateManager.isRepairing());
    }

    @Test
    @DisplayName("a scope unwinds when the repair throws")
    public void scopeUnwindsOnException() {
        assertThrows(IllegalStateException.class, () -> stateManager.repairing(() -> {
            throw new IllegalStateException("appearance generation failed");
        }));

        assertFalse(stateManager.isRepairing(), "a failed repair must not leave the thread marked as repairing");

        PObject pObject = object(905);
        stateManager.addChange(pObject);
        assertEquals(StateManager.Type.CHANGE, typeOf(pObject));
    }

    @Test
    @DisplayName("a scope on one thread does not mark another thread's edits as repairs")
    public void scopeIsPerThread() throws Exception {
        PObject repaired = object(906);
        PObject edited = object(907);
        CountDownLatch insideScope = new CountDownLatch(1);
        CountDownLatch editDone = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread editor = new Thread(() -> {
            try {
                // the page-init thread is mid repair while this runs.
                assertTrue(insideScope.await(5, TimeUnit.SECONDS));
                assertFalse(stateManager.isRepairing());
                stateManager.addChange(edited);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                editDone.countDown();
            }
        }, "edit-thread");
        editor.start();

        stateManager.repairing(() -> {
            stateManager.addChange(repaired);
            insideScope.countDown();
            try {
                assertTrue(editDone.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        editor.join(5000);

        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        assertEquals(StateManager.Type.REPAIR, typeOf(repaired));
        assertEquals(StateManager.Type.CHANGE, typeOf(edited), "the other thread was not repairing");
    }

    @Test
    @DisplayName("a repair never demotes a user edit, but does keep its content")
    public void repairDoesNotDemoteUserEdit() {
        Reference reference = new Reference(908, 0);
        DictionaryEntries edited = new DictionaryEntries();
        edited.put(new Name("Edited"), Boolean.TRUE);
        DictionaryEntries repaired = new DictionaryEntries();
        repaired.put(new Name("Repaired"), Boolean.TRUE);

        stateManager.addChange(new PObject(edited, reference));
        stateManager.repairing(() -> stateManager.addChange(new PObject(repaired, reference)));

        StateManager.Change change = stateManager.getChange(reference);
        assertEquals(StateManager.Type.CHANGE, change.getType(),
                "a zoom-triggered appearance regeneration used to make a real edit look like library housekeeping");
        assertEquals(repaired, change.getPObject().getObject(), "the newest content still wins");
        assertTrue(stateManager.hasUnsavedUserChanges());
    }

    @Test
    @DisplayName("a user edit promotes an earlier repair")
    public void userEditPromotesRepair() {
        Reference reference = new Reference(909, 0);
        stateManager.repairing(() -> stateManager.addChange(new PObject(new DictionaryEntries(), reference)));
        assertFalse(stateManager.hasUnsavedUserChanges());

        stateManager.addChange(new PObject(new DictionaryEntries(), reference));

        assertEquals(StateManager.Type.CHANGE, stateManager.getChange(reference).getType());
        assertTrue(stateManager.hasUnsavedUserChanges());
    }

    @Test
    @DisplayName("a repair does not resurrect a deleted object")
    public void repairDoesNotResurrectDeletion() {
        Reference reference = new Reference(910, 0);
        stateManager.addDeletion(reference);

        stateManager.repairing(() -> stateManager.addChange(new PObject(new DictionaryEntries(), reference)));

        assertEquals(StateManager.Type.DELETE, stateManager.getChange(reference).getType());
        assertTrue(stateManager.hasUnsavedUserChanges());
    }

    @Test
    @DisplayName("a deletion is a user change")
    public void deletionIsAUserChange() {
        stateManager.addDeletion(new Reference(911, 0));

        assertTrue(stateManager.hasUnsavedUserChanges());
        assertTrue(stateManager.hasWritableChanges());
    }

    @Test
    @DisplayName("snapshotting clears unsaved changes, and the next edit brings them back")
    public void snapshotIsTheSavedBaseline() {
        stateManager.addChange(object(912));
        assertTrue(stateManager.hasUnsavedUserChanges());

        stateManager.setChangesSnapshot();
        assertFalse(stateManager.hasUnsavedUserChanges(), "saving and closing must not ask again");

        stateManager.addChange(object(913));
        assertTrue(stateManager.hasUnsavedUserChanges());
    }

    @Test
    @DisplayName("a repair after a save does not make the document look modified again")
    public void repairAfterSnapshotIsStillInvisible() {
        stateManager.addChange(object(914));
        stateManager.setChangesSnapshot();

        stateManager.repairing(() -> stateManager.addChange(object(915)));

        assertFalse(stateManager.hasUnsavedUserChanges());
        assertTrue(stateManager.hasWritableChanges());
    }

    @Test
    @DisplayName("editing the same object twice is one unsaved change, not two")
    public void reEditingOneObjectStaysOneChange() {
        Reference reference = new Reference(916, 0);
        stateManager.addChange(new PObject(new DictionaryEntries(), reference));
        stateManager.setChangesSnapshot();

        DictionaryEntries second = new DictionaryEntries();
        second.put(new Name("Second"), Boolean.TRUE);
        stateManager.addChange(new PObject(second, reference));

        assertTrue(stateManager.hasUnsavedUserChanges(),
                "same reference, different content - a size-only comparison used to miss this");
    }
}
