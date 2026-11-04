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
package org.icepdf.core.pobjects.fonts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The core-14 AFM metrics. The average width was summed into an int, and every glyph width is a
 * fraction of an em, so it always came out 0.
 */
public class AFMTest {

    @DisplayName("Courier is monospaced: its average width is its one width, 600/1000")
    @Test
    public void courierAverageWidth() {
        AFM courier = AFM.AFMs.get("courier");
        assertEquals(0.6f, courier.getAvgWidth(), 1e-4f);
        assertEquals(0.6f, courier.getMaxWidth(), 1e-4f);
    }

    @DisplayName("every core-14 font has an average width between zero and its widest glyph")
    @Test
    public void averageWithinRange() {
        assertEquals(14, AFM.AFMs.size());
        for (AFM afm : AFM.AFMs.values()) {
            float avg = afm.getAvgWidth();
            assertTrue(avg > 0 && avg <= afm.getMaxWidth(), afm.getFontName() + " average " + avg);
        }
    }
}
