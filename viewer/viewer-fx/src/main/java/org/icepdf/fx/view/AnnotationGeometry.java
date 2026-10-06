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

import java.awt.geom.Rectangle2D;

/**
 * Move/resize geometry for annotation editing, in page view space (logical px, y down), with no
 * toolkit in it.  Handles are numbered clockwise from the top-left as drawn on screen
 * ({@link AnnotationUiLayer#handlePoint}): 0 TL, 1 T, 2 TR, 3 R, 4 BR, 5 B, 6 BL, 7 L.
 */
final class AnnotationGeometry {

    /** The smallest an annotation can be resized to, logical px. */
    static final double MIN_SIZE = 8;

    private AnnotationGeometry() {
    }

    /** The rectangle moved by (dx, dy), kept inside {@code bounds} (the page). */
    static Rectangle2D move(Rectangle2D rect, double dx, double dy, Rectangle2D bounds) {
        double x = clamp(rect.getX() + dx, bounds.getMinX(), bounds.getMaxX() - rect.getWidth());
        double y = clamp(rect.getY() + dy, bounds.getMinY(), bounds.getMaxY() - rect.getHeight());
        return new Rectangle2D.Double(x, y, rect.getWidth(), rect.getHeight());
    }

    /**
     * The rectangle with handle {@code handle} dragged by (dx, dy): corners move two edges, edge
     * handles one.  An edge can't cross its opposite (the size stops at {@link #MIN_SIZE}) nor leave
     * {@code bounds}.
     */
    static Rectangle2D resize(Rectangle2D rect, int handle, double dx, double dy, Rectangle2D bounds) {
        double left = rect.getMinX();
        double top = rect.getMinY();
        double right = rect.getMaxX();
        double bottom = rect.getMaxY();
        boolean moveLeft = handle == 0 || handle == 6 || handle == 7;
        boolean moveRight = handle == 2 || handle == 3 || handle == 4;
        boolean moveTop = handle == 0 || handle == 1 || handle == 2;
        boolean moveBottom = handle == 4 || handle == 5 || handle == 6;
        if (moveLeft) left = clamp(left + dx, bounds.getMinX(), right - MIN_SIZE);
        if (moveRight) right = clamp(right + dx, left + MIN_SIZE, bounds.getMaxX());
        if (moveTop) top = clamp(top + dy, bounds.getMinY(), bottom - MIN_SIZE);
        if (moveBottom) bottom = clamp(bottom + dy, top + MIN_SIZE, bounds.getMaxY());
        return new Rectangle2D.Double(left, top, right - left, bottom - top);
    }

    /** Resize cursor direction for a handle, as a compass string (nw, n, ne, e, se, s, sw, w). */
    static String direction(int handle) {
        return switch (handle) {
            case 0 -> "nw";
            case 1 -> "n";
            case 2 -> "ne";
            case 3 -> "e";
            case 4 -> "se";
            case 5 -> "s";
            case 6 -> "sw";
            default -> "w";
        };
    }

    private static double clamp(double v, double lo, double hi) {
        return hi < lo ? lo : Math.max(lo, Math.min(hi, v));
    }
}
