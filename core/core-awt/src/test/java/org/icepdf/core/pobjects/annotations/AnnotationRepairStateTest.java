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
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.geom.AffineTransform;
import java.nio.file.Path;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The behaviour PR #183 was written for, asserted end to end on a document.
 * <p>
 * The library writes things of its own just to render a file - an appearance stream the file never
 * carried, a field appearance the file asked for with /NeedAppearances.  Closing such a document
 * must not offer to save it.  Regression GH-25 came back because the close prompt stopped reading
 * the change type; the two halves of that are what the first tests here pin.
 * <p>
 * The half that is easy to break while fixing the other: a genuine edit that happens to go through
 * the same appearance-regeneration code must still count.  Filling in a form field records nothing
 * except the appearance reset, so if that reset were treated as library housekeeping the user's
 * typing would be thrown away without a word.
 */
public class AnnotationRepairStateTest {

    private static final Path FIXTURES = Path.of("src/test/resources/annotations");

    private Document open(String fixture) throws Exception {
        Document document = new Document();
        document.setFile(FIXTURES.resolve(fixture).toAbsolutePath().toString());
        return document;
    }

    private Page initialisedPage(Document document) throws Exception {
        Page page = document.getPageTree().getPage(0);
        page.init();
        return page;
    }

    private long countOfType(StateManager stateManager, StateManager.Type type) {
        long count = 0;
        for (Iterator<StateManager.Change> it = stateManager.iteratorSortedByObjectNumber(); it.hasNext(); ) {
            if (it.next().getType() == type) {
                count++;
            }
        }
        return count;
    }

    @Test
    @DisplayName("generating a missing appearance stream is a repair, not an edit")
    public void missingAppearanceIsARepair() throws Exception {
        Document document = open("missing_appearance.pdf");
        try {
            Page page = initialisedPage(document);
            Annotation annotation = page.getAnnotations().get(0);
            assertTrue(annotation.hasAppearanceStream(), "the library should have built one to render with");

            StateManager stateManager = document.getStateManager();
            assertTrue(stateManager.hasWritableChanges(), "the generated appearance still has to be written");
            assertEquals(0, countOfType(stateManager, StateManager.Type.CHANGE),
                    "nothing here was asked for by the user");
            assertFalse(stateManager.hasUnsavedUserChanges(),
                    "opening a file with no appearance stream must not offer to save it on close");
        } finally {
            document.dispose();
        }
    }

    @Test
    @DisplayName("honouring /NeedAppearances is a repair, not an edit")
    public void needAppearancesIsARepair() throws Exception {
        Document document = open("need_appearances.pdf");
        try {
            initialisedPage(document);

            StateManager stateManager = document.getStateManager();
            assertTrue(stateManager.hasWritableChanges(),
                    "guard: if nothing was generated this test would pass without proving anything");
            assertEquals(0, countOfType(stateManager, StateManager.Type.CHANGE));
            assertFalse(stateManager.hasUnsavedUserChanges(),
                    "the file asked the reader to build the appearances; the user typed nothing");
        } finally {
            document.dispose();
        }
    }

    @Test
    @DisplayName("a file the library has no repairs to make records nothing at all")
    public void intactFileRecordsNothing() throws Exception {
        Document document = open("text_field.pdf");
        try {
            initialisedPage(document);

            StateManager stateManager = document.getStateManager();
            assertFalse(stateManager.hasWritableChanges(), "control: this fixture needs no repair");
            assertFalse(stateManager.hasUnsavedUserChanges());
        } finally {
            document.dispose();
        }
    }

    @Test
    @DisplayName("committing a form field value is an unsaved user change")
    public void fieldValueCommitIsAUserChange() throws Exception {
        Document document = open("text_field.pdf");
        try {
            Page page = initialisedPage(document);
            TextWidgetAnnotation widget = (TextWidgetAnnotation) page.getAnnotations().get(0);
            StateManager stateManager = document.getStateManager();
            assertFalse(stateManager.hasUnsavedUserChanges(), "nothing has happened yet");

            // what TextWidgetComponent does when the field loses focus.
            FieldDictionary fieldDictionary = widget.getFieldDictionary();
            fieldDictionary.setFieldValue("typed by the user", widget.getPObjectReference());
            widget.resetAppearanceStream(new AffineTransform());

            assertTrue(stateManager.hasUnsavedUserChanges(),
                    "setFieldValue registers nothing itself, so the appearance reset is the only record of the "
                            + "typing - treat it as library housekeeping and the user's input is lost silently");
            assertEquals(StateManager.Type.CHANGE,
                    stateManager.getChange(widget.getPObjectReference()).getType());
        } finally {
            document.dispose();
        }
    }

    @Test
    @DisplayName("an edit survives a later repair of the same annotation")
    public void editSurvivesLaterRepair() throws Exception {
        Document document = open("text_field.pdf");
        try {
            Page page = initialisedPage(document);
            TextWidgetAnnotation widget = (TextWidgetAnnotation) page.getAnnotations().get(0);
            StateManager stateManager = document.getStateManager();

            widget.getFieldDictionary().setFieldValue("typed by the user", widget.getPObjectReference());
            widget.resetAppearanceStream(new AffineTransform());

            // a zoom or a rotate re-runs the same appearance generation, this time as housekeeping.
            stateManager.repairing(() -> widget.resetAppearanceStream(new AffineTransform()));

            assertTrue(stateManager.hasUnsavedUserChanges(),
                    "a repair must not undo the record of a user edit");
            assertEquals(StateManager.Type.CHANGE,
                    stateManager.getChange(widget.getPObjectReference()).getType());
        } finally {
            document.dispose();
        }
    }

    @Test
    @DisplayName("adding an annotation is an edit; adding one inside a repair scope is not")
    public void addAnnotationFollowsTheScope() throws Exception {
        Document document = open("text_field.pdf");
        try {
            Page page = initialisedPage(document);
            StateManager stateManager = document.getStateManager();

            Annotation repaired = AnnotationFactory.buildAnnotation(
                    document.getPageTree().getLibrary(), Annotation.SUBTYPE_POPUP, new java.awt.Rectangle(10, 10, 50, 50));
            assertNotNull(repaired);
            stateManager.repairing(() -> page.addAnnotation(repaired));
            assertFalse(stateManager.hasUnsavedUserChanges(),
                    "a popup manufactured so a markup annotation has somewhere to show is not a user edit");

            Annotation added = AnnotationFactory.buildAnnotation(
                    document.getPageTree().getLibrary(), Annotation.SUBTYPE_SQUARE, new java.awt.Rectangle(10, 10, 50, 50));
            assertNotNull(added);
            page.addAnnotation(added);
            assertTrue(stateManager.hasUnsavedUserChanges());
        } finally {
            document.dispose();
        }
    }

    @Test
    @DisplayName("a repaired document still saves its repairs when a save happens")
    public void repairsAreStillWritten() throws Exception {
        Document document = open("missing_appearance.pdf");
        try {
            initialisedPage(document);
            StateManager stateManager = document.getStateManager();

            assertFalse(stateManager.hasUnsavedUserChanges(), "do not prompt");
            assertTrue(stateManager.hasWritableChanges(),
                    "but once a save is happening for any reason, write the generated appearance out with it");
        } finally {
            document.dispose();
        }
    }
}
