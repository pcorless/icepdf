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

    @DisplayName("the in-memory colour history keeps ten, most recent first, no duplicates")
    @Test
    void history() {
        ColorHistory history = ColorHistory.inMemory();
        for (int i = 0; i < 12; i++) history.used(new Color(i, 0, 0));
        history.used(new Color(3, 0, 0));
        assertEquals(10, history.recent().size());
        assertEquals(new Color(3, 0, 0), history.recent().get(0));
        assertEquals(1, history.recent().stream().filter(new Color(3, 0, 0)::equals).count());
    }
}
