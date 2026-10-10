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

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnnotationGeometryTest {

    private static final Rectangle2D PAGE = new Rectangle2D.Double(0, 0, 600, 800);
    private static final Rectangle2D R = new Rectangle2D.Double(100, 100, 50, 40);

    private static Rectangle2D rect(double x, double y, double w, double h) {
        return new Rectangle2D.Double(x, y, w, h);
    }

    @DisplayName("move translates and stays on the page")
    @Test
    void move() {
        assertEquals(rect(110, 90, 50, 40), AnnotationGeometry.move(R, 10, -10, PAGE));
        assertEquals(rect(0, 0, 50, 40), AnnotationGeometry.move(R, -500, -500, PAGE));
        assertEquals(rect(550, 760, 50, 40), AnnotationGeometry.move(R, 5000, 5000, PAGE));
    }

    @DisplayName("corner handles move two edges, edge handles one")
    @Test
    void resizeHandles() {
        assertEquals(rect(90, 95, 60, 45), AnnotationGeometry.resize(R, 0, -10, -5, PAGE));  // TL
        assertEquals(rect(100, 95, 50, 45), AnnotationGeometry.resize(R, 1, -10, -5, PAGE)); // T
        assertEquals(rect(100, 100, 60, 45), AnnotationGeometry.resize(R, 4, 10, 5, PAGE));  // BR
        assertEquals(rect(90, 100, 60, 40), AnnotationGeometry.resize(R, 7, -10, 99, PAGE)); // L
        assertEquals(rect(100, 100, 50, 52), AnnotationGeometry.resize(R, 5, 99, 12, PAGE)); // B
    }

    @DisplayName("an edge stops at the minimum size instead of crossing its opposite")
    @Test
    void minimumSize() {
        Rectangle2D shrunk = AnnotationGeometry.resize(R, 4, -500, -500, PAGE);
        assertEquals(AnnotationGeometry.MIN_SIZE, shrunk.getWidth(), 1e-9);
        assertEquals(AnnotationGeometry.MIN_SIZE, shrunk.getHeight(), 1e-9);
        assertEquals(100, shrunk.getX(), 1e-9);
        Rectangle2D fromTopLeft = AnnotationGeometry.resize(R, 0, 500, 500, PAGE);
        assertEquals(150 - AnnotationGeometry.MIN_SIZE, fromTopLeft.getX(), 1e-9);
    }

    @DisplayName("resize can't leave the page")
    @Test
    void resizeStaysOnPage() {
        assertEquals(rect(0, 0, 150, 140), AnnotationGeometry.resize(R, 0, -1000, -1000, PAGE));
        assertEquals(rect(100, 100, 500, 700), AnnotationGeometry.resize(R, 4, 1000, 1000, PAGE));
    }
}
