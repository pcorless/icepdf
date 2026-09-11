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
package org.icepdf.annotation;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.FreeTextAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.WriteMode;
import org.icepdf.ri.util.FontPropertiesManager;
import org.icepdf.signing.SigningTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;

import static org.icepdf.core.pobjects.annotations.FreeTextAnnotation.INSETS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How far a repair scope reaches.
 * <p>
 * This is the case the old {@code boolean isNew} parameter could not express at all.  Building a
 * FreeText appearance does not stop at the appearance form: it embeds a font, which drags in a font
 * descriptor, a font programme and a /ToUnicode CMap, and {@code saveAppearanceStream()} promotes
 * every one of them into the change set through methods that take no flag and have no way of
 * knowing why they were called.  Before the scope, opening a document with a FreeText annotation
 * that had no appearance stream booked all of those as user edits, and the viewer then offered to
 * save a file the user had not touched.
 */
public class AnnotationRepairScopeTest {

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    private Document openFixture() throws Exception {
        Document document = new Document();
        InputStream fixture = SigningTest.class.getResourceAsStream("/annotation/hello_pdfa1.pdf");
        document.setInputStream(fixture, "hello_pdfa1.pdf");
        return document;
    }

    /**
     * Builds the appearance the way FreeTextAnnotationComponent does, appearance stream and embedded
     * font both.
     */
    private FreeTextAnnotation buildFreeText(Document document, Page page) {
        Library library = document.getCatalog().getLibrary();
        Rectangle rect = new Rectangle(250, 200, 400, 50);
        Rectangle tBbox = page.convertToPageSpace(rect, Page.BOUNDARY_CROPBOX, 0f, 1.0f);
        tBbox.setLocation(tBbox.x - INSETS, tBbox.y - tBbox.height - INSETS);

        FreeTextAnnotation annotation = (FreeTextAnnotation) AnnotationFactory.buildAnnotation(
                library, Annotation.SUBTYPE_FREE_TEXT, tBbox);
        annotation.setContents("Hello World");
        return annotation;
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
    @DisplayName("a repair scope covers the fonts the appearance stream drags in")
    public void repairScopeReachesEmbeddedFonts() throws Exception {
        Document document = openFixture();
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            AffineTransform pageTransform = page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0f, 1.0f);
            FreeTextAnnotation annotation = buildFreeText(document, page);
            StateManager stateManager = document.getStateManager();

            stateManager.repairing(() -> {
                annotation.resetAppearanceStream(pageTransform);
                annotation.saveAppearanceStream();
            });

            assertTrue(stateManager.hasWritableChanges(),
                    "guard: the appearance and its font objects should be in the change set");
            assertTrue(countOfType(stateManager, StateManager.Type.REPAIR) > 1,
                    "guard: more than just the form - the font objects should be here too");
            assertEquals(0, countOfType(stateManager, StateManager.Type.CHANGE),
                    "saveAppearanceStream and saveFont take no flag; only the scope can reach them");
            assertFalse(stateManager.hasUnsavedUserChanges(),
                    "repairing a FreeText annotation the file left without an appearance is not a user edit");
        } finally {
            document.dispose();
        }
    }

    @Test
    @DisplayName("authoring the same annotation outside a scope is a user change")
    public void authoringOutsideScopeIsAUserChange() throws Exception {
        Document document = openFixture();
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            AffineTransform pageTransform = page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0f, 1.0f);
            FreeTextAnnotation annotation = buildFreeText(document, page);
            StateManager stateManager = document.getStateManager();

            annotation.resetAppearanceStream(pageTransform);
            page.addAnnotation(annotation);
            annotation.saveAppearanceStream();

            assertEquals(0, countOfType(stateManager, StateManager.Type.REPAIR),
                    "nothing here is housekeeping; the user drew this annotation");
            assertTrue(stateManager.hasUnsavedUserChanges());
        } finally {
            document.dispose();
        }
    }

    @Test
    @DisplayName("a repair is still written out when a save happens for another reason")
    public void repairsSurviveAnIncrementalSave() throws Exception {
        Document document = openFixture();
        byte[] saved;
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            AffineTransform pageTransform = page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0f, 1.0f);
            FreeTextAnnotation annotation = buildFreeText(document, page);
            StateManager stateManager = document.getStateManager();

            stateManager.repairing(() -> {
                annotation.resetAppearanceStream(pageTransform);
                page.addAnnotation(annotation);
                annotation.saveAppearanceStream();
            });
            assertFalse(stateManager.hasUnsavedUserChanges(), "do not prompt for it");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.writeToOutputStream(out, WriteMode.INCREMENT_UPDATE);
            saved = out.toByteArray();
        } finally {
            document.dispose();
        }

        Document reopened = new Document();
        try {
            reopened.setInputStream(new ByteArrayInputStream(saved), "repaired.pdf");
            Page page = reopened.getPageTree().getPage(0);
            page.init();
            assertEquals(1, page.getAnnotations().size(),
                    "the repair was written: the file on disk should match what the user was looking at");
            assertTrue(page.getAnnotations().get(0).hasAppearanceStream());
        } finally {
            reopened.dispose();
        }
    }
}
