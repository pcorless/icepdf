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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Saved thread counts become the pools' system properties, unless the command line set them. */
class PerformanceSettingsTest {

    // the render pool's property: only read when the shared pool is first created, which no unit test does.
    private static final PerformanceSettings.Pool POOL = PerformanceSettings.Pool.RENDER;

    @AfterEach
    void clearProperty() {
        System.clearProperty(POOL.property());
    }

    @DisplayName("a saved count is handed over as the pool's system property")
    @Test
    void savedCountApplies() {
        System.clearProperty(POOL.property());
        ViewerPreferences preferences = ViewerPreferences.inMemory();
        PerformanceSettings.save(preferences, POOL, 3);
        PerformanceSettings.apply(preferences);
        assertEquals("3", System.getProperty(POOL.property()));
    }

    @DisplayName("automatic saves nothing and sets nothing")
    @Test
    void automaticLeavesPropertyAlone() {
        System.clearProperty(POOL.property());
        ViewerPreferences preferences = ViewerPreferences.inMemory();
        PerformanceSettings.save(preferences, POOL, 3);
        PerformanceSettings.save(preferences, POOL, null);
        assertNull(preferences.get(POOL.key(), null));
        PerformanceSettings.apply(preferences);
        assertNull(System.getProperty(POOL.property()));
    }

    @DisplayName("a count given on the command line wins over a saved one")
    @Test
    void commandLineWins() {
        System.setProperty(POOL.property(), "5");
        ViewerPreferences preferences = ViewerPreferences.inMemory();
        PerformanceSettings.save(preferences, POOL, 3);
        PerformanceSettings.apply(preferences);
        assertEquals("5", System.getProperty(POOL.property()));
    }

    @DisplayName("a hand-edited bad count reads as automatic")
    @Test
    void badCountIsAutomatic() {
        ViewerPreferences preferences = ViewerPreferences.inMemory();
        for (String bad : new String[]{"0", "-2", "lots", String.valueOf(PerformanceSettings.MAX_THREADS + 1)}) {
            preferences.put(POOL.key(), bad);
            assertNull(PerformanceSettings.saved(preferences, POOL), bad);
        }
        preferences.put(POOL.key(), " 12 ");
        assertEquals(12, PerformanceSettings.saved(preferences, POOL));
    }
}
