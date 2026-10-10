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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** The font cache file and the in-memory preferences adapter core's FontManager reads it through. */
class FontSettingsTest {

    @TempDir
    Path home;
    private String previousHome;

    @BeforeEach
    void useScratchHome() {
        previousHome = System.getProperty(ViewerPreferences.HOME_PROPERTY);
        System.setProperty(ViewerPreferences.HOME_PROPERTY, home.toString());
    }

    @AfterEach
    void restoreHome() {
        if (previousHome == null) System.clearProperty(ViewerPreferences.HOME_PROPERTY);
        else System.setProperty(ViewerPreferences.HOME_PROPERTY, previousHome);
    }

    @DisplayName("an entry is family|decorations|path; anything else is skipped")
    @Test
    void parse() {
        FontSettings.FontEntry entry = FontSettings.parse("Arial Bold", "Arial|1|/fonts/arialbd.ttf");
        assertEquals(new FontSettings.FontEntry("Arial Bold", "Arial", 1, "/fonts/arialbd.ttf"), entry);
        assertNull(FontSettings.parse("x", "Arial|bold|/f.ttf"));
        assertNull(FontSettings.parse("x", "Arial|1"));
        assertNull(FontSettings.parse("x", null));
    }

    @DisplayName("the cache lives in the viewer's home, is versioned, and drops fonts whose file has gone")
    @Test
    void cacheRoundTrip() throws Exception {
        assertEquals(home.resolve("fonts.properties"), FontSettings.cacheFile());
        assertNull(FontSettings.readCache(), "no cache yet");
        Path font = Files.createFile(home.resolve("present.ttf"));
        Properties fonts = new Properties();
        fonts.setProperty("Present", "Present|0|" + font);
        fonts.setProperty("Gone", "Gone|0|" + home.resolve("gone.ttf"));
        fonts.setProperty("Broken", "nonsense");
        FontSettings.writeCache(fonts);

        Properties read = FontSettings.readCache();
        assertNotNull(read);
        assertEquals(List.of("Present"), List.copyOf(read.stringPropertyNames()));

        FontSettings.clearCache();
        assertNull(FontSettings.readCache());
    }

    @DisplayName("a cache from another version is ignored")
    @Test
    void otherVersion() throws Exception {
        Path font = Files.createFile(home.resolve("f.ttf"));
        Files.writeString(FontSettings.cacheFile(), "__icepdf.cache.version=0\nF=F|0|" + font.toString().replace("\\", "/") + "\n");
        assertNull(FontSettings.readCache());
    }

    @DisplayName("the preferences adapter reads and writes the backing properties only")
    @Test
    void propertiesPreferences() throws Exception {
        Properties backing = new Properties();
        backing.setProperty("a", "1");
        FontSettings.PropertiesPreferences preferences = new FontSettings.PropertiesPreferences(backing);
        assertEquals("1", preferences.get("a", null));
        preferences.put("b", "2");
        assertEquals("2", backing.getProperty("b"));
        assertEquals(2, preferences.keys().length);
        preferences.remove("a");
        assertFalse(backing.containsKey("a"));
        preferences.flush();
    }

    @DisplayName("extra font directories are one per line; empty clears the preference")
    @Test
    void directories() {
        ViewerPreferences preferences = ViewerPreferences.inMemory();
        assertTrue(FontSettings.directories(preferences).isEmpty());
        FontSettings.setDirectories(preferences, List.of("/a", "/b c"));
        assertEquals(List.of("/a", "/b c"), FontSettings.directories(preferences));
        FontSettings.setDirectories(preferences, List.of());
        assertNull(preferences.get(FontSettings.DIRECTORIES, null));
    }
}
