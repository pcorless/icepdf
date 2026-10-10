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
package org.icepdf.fx.viewer;

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
}
