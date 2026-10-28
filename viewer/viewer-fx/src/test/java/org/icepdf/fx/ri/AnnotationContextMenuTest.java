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
package org.icepdf.fx.ri;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The parts of the annotation menu that need no toolkit: the standard colours and the in-memory
 * colour history.  The menu itself is JavaFX controls and is checked by running the viewer.
 */
class AnnotationContextMenuTest {

    @DisplayName("every standard colour has a name, and they are the Swing viewer's ten")
    @Test
    void standardColours() {
        assertEquals(10, AnnotationContextMenu.STANDARD.length);
        assertEquals(AnnotationContextMenu.STANDARD.length, AnnotationContextMenu.STANDARD_NAMES.length);
        assertEquals(new Color(255, 0, 0), AnnotationContextMenu.STANDARD[1]);
        assertEquals("red", AnnotationContextMenu.STANDARD_NAMES[1]);
        // each name has its label in the bundle
        for (String name : AnnotationContextMenu.STANDARD_NAMES) {
            assertNotEquals("annotationMenu.colour." + name, org.icepdf.fx.i18n.Messages.get("annotationMenu.colour." + name));
        }
    }

    @DisplayName("the in-memory colour history keeps five, most recent first, no duplicates")
    @Test
    void history() {
        ColorHistory history = ColorHistory.inMemory();
        for (int i = 0; i < 12; i++) history.used(new Color(i, 0, 0));
        history.used(new Color(9, 0, 0));
        assertEquals(ColorHistory.DEFAULT_MAX_RECENT, history.recent().size());
        assertEquals(5, history.recent().size());
        assertEquals(new Color(9, 0, 0), history.recent().get(0));
        assertEquals(1, history.recent().stream().filter(new Color(9, 0, 0)::equals).count());
    }

    @DisplayName("the system property sets how many recent colours are kept")
    @Test
    void historyLength() {
        try {
            System.setProperty(ColorHistory.MAX_RECENT_PROPERTY, "8");
            ColorHistory history = ColorHistory.inMemory();
            for (int i = 0; i < 12; i++) history.used(new Color(i, 0, 0));
            assertEquals(8, history.recent().size());
            System.setProperty(ColorHistory.MAX_RECENT_PROPERTY, "0");
            assertEquals(1, ColorHistory.maxRecent(), "at least one");
        } finally {
            System.clearProperty(ColorHistory.MAX_RECENT_PROPERTY);
        }
    }

    @DisplayName("Use as Default: a rectangle's colour becomes the rectangle tool's; disabled once it is")
    @Test
    void useAsDefault(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        org.icepdf.fx.testing.Fx.start();
        org.icepdf.core.pobjects.Document document = new org.icepdf.core.pobjects.Document();
        document.setFile(org.icepdf.fx.testing.TinyPdf.onePage("", "").write(dir.resolve("one.pdf")).toString());
        org.icepdf.core.util.Library library = document.getCatalog().getLibrary();
        org.icepdf.core.pobjects.annotations.Annotation square = org.icepdf.core.pobjects.annotations.AnnotationFactory
                .buildAnnotation(library, org.icepdf.core.pobjects.annotations.Annotation.SUBTYPE_SQUARE,
                        new java.awt.Rectangle(0, 0, 50, 50));
        square.setColor(new Color(0, 112, 192));
        assertEquals(org.icepdf.fx.view.ToolMode.RECTANGLE, AnnotationContextMenu.toolFor(square));
        javafx.collections.ObservableMap<org.icepdf.fx.view.ToolMode, javafx.scene.paint.Color> tools =
                javafx.collections.FXCollections.observableHashMap();
        javafx.scene.control.MenuItem item = org.icepdf.fx.testing.Fx.call(() -> AnnotationContextMenu.useAsDefault(square, tools));
        assertNotNull(item);
        assertEquals("Use as Default for Rectangle", item.getText());
        assertFalse(item.isDisable());
        org.icepdf.fx.testing.Fx.run(() -> item.getOnAction().handle(null));
        assertEquals(javafx.scene.paint.Color.rgb(0, 112, 192), tools.get(org.icepdf.fx.view.ToolMode.RECTANGLE));
        assertTrue(org.icepdf.fx.testing.Fx.call(() -> AnnotationContextMenu.useAsDefault(square, tools)).isDisable());
        document.dispose();
    }
}
