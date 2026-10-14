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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FontDirectoriesTest {

    @DisplayName("extra font directories are one per line; empty clears the preference")
    @Test
    void directories() {
        ViewerPreferences preferences = ViewerPreferences.inMemory();
        assertTrue(FontDirectories.of(preferences).isEmpty());
        FontDirectories.set(preferences, List.of("/a", "/b c"));
        assertEquals(List.of("/a", "/b c"), FontDirectories.of(preferences));
        FontDirectories.set(preferences, List.of());
        assertNull(preferences.get(FontDirectories.DIRECTORIES, null));
    }
}
