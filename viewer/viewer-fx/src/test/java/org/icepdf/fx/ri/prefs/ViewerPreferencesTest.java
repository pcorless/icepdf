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
package org.icepdf.fx.ri.prefs;

import org.icepdf.fx.view.FitMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The viewer's own settings file: typed values, bad values, recent files, and the save. */
class ViewerPreferencesTest {

    @DisplayName("values survive a save and load; bad values fall back to the default")
    @Test
    void roundTrip(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sub/viewer.properties");
        ViewerPreferences preferences = ViewerPreferences.load(file);
        preferences.putBoolean(ViewerPreferences.SEARCH_CASE, true);
        preferences.putDouble(ViewerPreferences.ZOOM, 1.5);
        preferences.putEnum(ViewerPreferences.FIT_MODE, FitMode.PAGE);
        preferences.put(ViewerPreferences.ANNOTATION_AUTHOR, "Ann é 中");
        preferences.save();
        assertTrue(Files.isRegularFile(file));
        assertFalse(Files.exists(file.resolveSibling("viewer.properties.tmp")));

        ViewerPreferences read = ViewerPreferences.load(file);
        assertTrue(read.getBoolean(ViewerPreferences.SEARCH_CASE, false));
        assertEquals(1.5, read.getDouble(ViewerPreferences.ZOOM, 1));
        assertEquals(FitMode.PAGE, read.getEnum(ViewerPreferences.FIT_MODE, FitMode.class, FitMode.WIDTH));
        assertEquals("Ann é 中", read.get(ViewerPreferences.ANNOTATION_AUTHOR, null));

        read.put(ViewerPreferences.ZOOM, "NaN");
        read.put(ViewerPreferences.FIT_MODE, "SIDEWAYS");
        assertEquals(1, read.getDouble(ViewerPreferences.ZOOM, 1));
        assertEquals(FitMode.WIDTH, read.getEnum(ViewerPreferences.FIT_MODE, FitMode.class, FitMode.WIDTH));
        read.put(ViewerPreferences.ZOOM, null);
        assertEquals(2, read.getDouble(ViewerPreferences.ZOOM, 2));
    }

    @DisplayName("recent files: most recent first, no duplicates, at most ten, missing files dropped")
    @Test
    void recentFiles(@TempDir Path dir) throws Exception {
        ViewerPreferences preferences = ViewerPreferences.inMemory();
        for (int i = 0; i < 12; i++) {
            preferences.addRecentFile(Files.createFile(dir.resolve("f" + i + ".pdf")));
        }
        List<Path> recent = preferences.getRecentFiles();
        assertEquals(10, recent.size());
        assertEquals(dir.resolve("f11.pdf"), recent.get(0));
        preferences.addRecentFile(dir.resolve("f5.pdf"));
        assertEquals(dir.resolve("f5.pdf"), preferences.getRecentFiles().get(0));
        assertEquals(10, preferences.getRecentFiles().size());
        Files.delete(dir.resolve("f5.pdf"));
        assertFalse(preferences.getRecentFiles().contains(dir.resolve("f5.pdf")));
        preferences.clearRecentFiles();
        assertTrue(preferences.getRecentFiles().isEmpty());
    }

    @DisplayName("in-memory settings never touch the disk")
    @Test
    void inMemory() {
        ViewerPreferences preferences = ViewerPreferences.inMemory();
        assertNull(preferences.getFile());
        preferences.putBoolean(ViewerPreferences.SEARCH_REGEX, true);
        preferences.save();
        assertTrue(preferences.getBoolean(ViewerPreferences.SEARCH_REGEX, false));
    }

    @DisplayName("recent colours: most recent first, no duplicates, at most five, survive a save")
    @Test
    void recentColours(@TempDir Path dir) {
        Path file = dir.resolve("viewer.properties");
        ViewerPreferences preferences = ViewerPreferences.load(file);
        assertTrue(preferences.getRecentColors().isEmpty());
        for (int i = 0; i < 12; i++) preferences.addRecentColor(new java.awt.Color(i, 0, 0));
        preferences.addRecentColor(new java.awt.Color(5, 0, 0));
        List<java.awt.Color> colors = preferences.getRecentColors();
        assertEquals(5, colors.size());
        assertEquals(new java.awt.Color(5, 0, 0), colors.get(0));
        assertEquals(new java.awt.Color(11, 0, 0), colors.get(1));
        assertEquals(1, colors.stream().filter(new java.awt.Color(5, 0, 0)::equals).count());
        preferences.save();
        assertEquals(colors, ViewerPreferences.load(file).getRecentColors());
        preferences.put(ViewerPreferences.RECENT_COLORS, "bad 255 255");
        assertEquals(List.of(new java.awt.Color(255)), preferences.getRecentColors());
    }

    @DisplayName("tool colours: stored per tool, null goes back to the default, a bad entry reads as the default")
    @Test
    void toolColours(@TempDir Path dir) {
        Path file = dir.resolve("viewer.properties");
        ViewerPreferences preferences = ViewerPreferences.load(file);
        assertNull(preferences.getToolColor(org.icepdf.fx.view.ToolMode.FREE_TEXT));
        preferences.setToolColor(org.icepdf.fx.view.ToolMode.FREE_TEXT, javafx.scene.paint.Color.rgb(0, 112, 192));
        preferences.save();
        ViewerPreferences reloaded = ViewerPreferences.load(file);
        assertEquals("#0070c0", reloaded.get(ViewerPreferences.TOOL_COLOR_PREFIX + "free-text", null));
        assertEquals(javafx.scene.paint.Color.rgb(0, 112, 192), reloaded.getToolColor(org.icepdf.fx.view.ToolMode.FREE_TEXT));
        assertNull(reloaded.getToolColor(org.icepdf.fx.view.ToolMode.INK));
        reloaded.setToolColor(org.icepdf.fx.view.ToolMode.FREE_TEXT, null);
        assertNull(reloaded.getToolColor(org.icepdf.fx.view.ToolMode.FREE_TEXT));
        reloaded.put(ViewerPreferences.TOOL_COLOR_PREFIX + "ink", "not a colour");
        assertNull(reloaded.getToolColor(org.icepdf.fx.view.ToolMode.INK));
    }
}
