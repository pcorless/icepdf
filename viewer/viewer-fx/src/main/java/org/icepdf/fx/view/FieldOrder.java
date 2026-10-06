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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Tab order of a page's form fields, from the page's /Tabs entry (ISO 32000-1 12.5.1, Table 30):
 * R row order (top to bottom, then left to right), C column order (left to right, then top to
 * bottom), anything else (S structure order, or no entry) the order of the /Annots array.  No
 * toolkit; works on anything with a user-space rectangle.
 */
final class FieldOrder {

    /** Rows/columns this close (points) count as the same line, so slightly ragged forms tab naturally. */
    static final double SAME_LINE = 4;

    private FieldOrder() {
    }

    /**
     * @param fields   in /Annots order
     * @param rectOf   a field's rectangle in PDF user space (y up)
     * @param tabs     the page's /Tabs name, or null
     */
    static <T> List<T> order(List<T> fields, Function<T, Rectangle2D> rectOf, String tabs) {
        if ("R".equals(tabs)) {
            // rows: top edges within SAME_LINE of each other; then left to right within a row.
            return lines(fields, f -> -rectOf.apply(f).getMaxY(), f -> rectOf.apply(f).getMinX());
        }
        if ("C".equals(tabs)) {
            return lines(fields, f -> rectOf.apply(f).getMinX(), f -> -rectOf.apply(f).getMaxY());
        }
        return new ArrayList<>(fields);
    }

    /**
     * Sorts by {@code across}, clusters neighbours within SAME_LINE into lines (clustering rather
     * than fixed bands, so a line straddling a band boundary isn't split), then sorts each line by
     * {@code along}.
     */
    private static <T> List<T> lines(List<T> fields, Function<T, Double> across, Function<T, Double> along) {
        List<T> byAcross = new ArrayList<>(fields);
        byAcross.sort(Comparator.comparingDouble(across::apply));
        List<T> out = new ArrayList<>(fields.size());
        List<T> line = new ArrayList<>();
        double lineStart = Double.NaN;
        for (T f : byAcross) {
            double a = across.apply(f);
            if (!line.isEmpty() && a - lineStart > SAME_LINE) {
                line.sort(Comparator.comparingDouble(along::apply));
                out.addAll(line);
                line.clear();
            }
            if (line.isEmpty()) lineStart = a;
            line.add(f);
        }
        line.sort(Comparator.comparingDouble(along::apply));
        out.addAll(line);
        return out;
    }

    /** The index after {@code current} (wrapping), or the first when nothing is current. */
    static int next(int size, int current, boolean backwards) {
        if (size == 0) return -1;
        if (current < 0) return backwards ? size - 1 : 0;
        return backwards ? (current - 1 + size) % size : (current + 1) % size;
    }
}
