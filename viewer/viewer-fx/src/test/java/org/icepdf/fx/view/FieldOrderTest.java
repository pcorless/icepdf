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
package org.icepdf.fx.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldOrderTest {

    // a 2x2 grid of fields (y up): a b on top, c d below; listed in a scrambled /Annots order.
    private static final Map<String, Rectangle2D> RECTS = Map.of(
            "a", new Rectangle2D.Double(100, 700, 50, 20),
            "b", new Rectangle2D.Double(300, 701, 50, 20),   // a point off: still the same row
            "c", new Rectangle2D.Double(100, 600, 50, 20),
            "d", new Rectangle2D.Double(300, 600, 50, 20));
    private static final List<String> ANNOTS = List.of("d", "a", "c", "b");

    @DisplayName("row order: top to bottom, left to right, ragged rows tolerated")
    @Test
    void rows() {
        assertEquals(List.of("a", "b", "c", "d"), FieldOrder.order(ANNOTS, RECTS::get, "R"));
    }

    @DisplayName("column order: left to right, top to bottom")
    @Test
    void columns() {
        assertEquals(List.of("a", "c", "b", "d"), FieldOrder.order(ANNOTS, RECTS::get, "C"));
    }

    @DisplayName("structure order or no /Tabs: the /Annots order")
    @Test
    void annotsOrder() {
        assertEquals(ANNOTS, FieldOrder.order(ANNOTS, RECTS::get, "S"));
        assertEquals(ANNOTS, FieldOrder.order(ANNOTS, RECTS::get, null));
    }

    @DisplayName("next / previous wrap, and start at the ends")
    @Test
    void next() {
        assertEquals(0, FieldOrder.next(4, -1, false));
        assertEquals(3, FieldOrder.next(4, -1, true));
        assertEquals(2, FieldOrder.next(4, 1, false));
        assertEquals(0, FieldOrder.next(4, 3, false));
        assertEquals(3, FieldOrder.next(4, 0, true));
        assertEquals(-1, FieldOrder.next(0, -1, false));
    }
}
