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

import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import org.icepdf.fx.ri.prefs.ViewerPreferences;
import org.icepdf.fx.testing.Fx;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Colour labels: matching an annotation colour, the Swing import, storing them, and the colour menu. */
class ColorLabelsTest {

    private static final ColorLabels.Label YELLOW = new ColorLabels.Label(new Color(255, 255, 0), "To do");
    private static final ColorLabels.Label GREEN = new ColorLabels.Label(new Color(0, 176, 80), "Done");
    private static final ColorLabels.Label LIME = new ColorLabels.Label(new Color(0, 186, 80), "Nearly");

    @DisplayName("a colour matches the nearest label within the tolerance, and nothing beyond it")
    @Test
    void match() {
        List<ColorLabels.Label> labels = List.of(YELLOW, GREEN, LIME);
        assertSame(YELLOW, ColorLabels.match(labels, new Color(255, 255, 0), 8));
        assertSame(YELLOW, ColorLabels.match(labels, new Color(250, 251, 6), 8), "a few units off another tool");
        assertNull(ColorLabels.match(labels, new Color(240, 255, 0), 8), "one channel too far");
        assertSame(GREEN, ColorLabels.match(labels, new Color(0, 178, 80), 8), "nearest of two in range");
        assertSame(LIME, ColorLabels.match(labels, new Color(0, 185, 80), 8));
        assertSame(GREEN, ColorLabels.match(labels, new Color(0, 181, 80), 8), "a tie goes to the first");
        assertNull(ColorLabels.match(labels, null, 8));
        assertNull(ColorLabels.match(labels, new Color(250, 251, 6), 0), "zero tolerance is exact");
    }

    @DisplayName("the tolerance comes from the system property, clamped to 0..255")
    @Test
    void tolerance() {
        assertEquals(ColorLabels.DEFAULT_TOLERANCE, ColorLabels.tolerance());
        try {
            System.setProperty(ColorLabels.TOLERANCE_PROPERTY, "300");
            assertEquals(255, ColorLabels.tolerance());
            System.setProperty(ColorLabels.TOLERANCE_PROPERTY, "2");
            assertEquals(2, ColorLabels.tolerance());
        } finally {
            System.clearProperty(ColorLabels.TOLERANCE_PROPERTY);
        }
    }

    @DisplayName("the Swing viewer's rgb|label pairs import, signed ARGB included; bad pairs are skipped")
    @Test
    void parseSwing() {
        // Swing stores Color.getRGB(): opaque colours are negative ints.
        String value = new Color(255, 255, 0).getRGB() + "|To do|" + new Color(0, 176, 80).getRGB()
                + "|Done|oops|Bad|" + new Color(1, 2, 3).getRGB();
        assertEquals(List.of(YELLOW, GREEN), ColorLabels.parseSwing(value));
        assertEquals(List.of(), ColorLabels.parseSwing(null));
        assertEquals(List.of(), ColorLabels.parseSwing(""));
    }

    @DisplayName("labels save and read back in order, with spaces and an empty name; none is remembered as none")
    @Test
    void preferences(@TempDir Path dir) {
        Path file = dir.resolve("viewer.properties");
        ViewerPreferences preferences = ViewerPreferences.load(file);
        assertFalse(preferences.hasColorLabels());
        ColorLabels.Label blank = new ColorLabels.Label(new Color(1, 2, 3), "");
        preferences.setColorLabels(List.of(GREEN, YELLOW, blank));
        preferences.save();

        ViewerPreferences read = ViewerPreferences.load(file);
        assertTrue(read.hasColorLabels());
        assertEquals(List.of(GREEN, YELLOW, blank), read.getColorLabels());

        read.setColorLabels(List.of());
        read.save();
        ViewerPreferences empty = ViewerPreferences.load(file);
        assertTrue(empty.hasColorLabels(), "an emptied list is not imported again");
        assertEquals(List.of(), empty.getColorLabels());
    }

    @DisplayName("Change Colour lists the labels by name first, then the standard colours")
    @Test
    void menuListsLabelsFirst() {
        Fx.start();
        ColorLabels labels = ColorLabels.inMemory();
        labels.setLabels(List.of(YELLOW, GREEN));
        List<MenuItem> items = Fx.call(() -> AnnotationContextMenu.colourItems(new Color(0, 176, 80), null, labels,
                null, c -> {
                }));
        assertEquals("To do", items.get(0).getText());
        assertEquals("Done", items.get(1).getText());
        assertTrue(items.get(1).isDisable(), "the current colour is disabled");
        assertInstanceOf(SeparatorMenuItem.class, items.get(2));

        List<MenuItem> plain = Fx.call(() -> AnnotationContextMenu.colourItems(null, null, null, null, c -> {
        }));
        assertFalse(plain.get(0) instanceof SeparatorMenuItem, "no labels, no leading separator");
    }
}
