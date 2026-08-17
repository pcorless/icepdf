/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.graphics.text;

import java.awt.geom.Rectangle2D;

/**
 * A detected text column on a page: the union bounds of the lines that make up the column plus the
 * reading-order {@link OffsetRange} those lines occupy in the page's {@link TextSequence}.
 * <p>
 * Columns are a geometric <em>lens</em> over the reading order (see {@link TextSequence#columns()});
 * they do not change the reading order.  Their purpose is column-aware mouse selection: a drag can
 * be constrained to the column it started in until the pointer passes {@link #getBottom() the
 * column's bottom edge}, matching the way Acrobat selects flowing multi-column text.
 * <p>
 * All geometry is in page space (y-up), the same space as {@link GlyphText#getBounds()}.
 *
 * @see TextSequence#columns()
 * @see TextSequence#caretAt(java.awt.geom.Point2D, OffsetRange)
 * @since 7.5
 */
public final class ColumnBlock {

    private final Rectangle2D.Double bounds;
    private final OffsetRange range;

    ColumnBlock(Rectangle2D.Double bounds, OffsetRange range) {
        this.bounds = bounds;
        this.range = range;
    }

    /**
     * @return the union bounding box of the column's lines, in page space.
     */
    public Rectangle2D.Double getBounds() {
        return bounds;
    }

    /**
     * @return the contiguous reading-order offset range occupied by the column's lines.
     */
    public OffsetRange getRange() {
        return range;
    }

    /**
     * The column's bottom edge in page space.  Page space is y-up, so this is the minimum y of the
     * union bounds &mdash; a drag point at or above this y is still "inside" the column; once it
     * drops below, selection may wrap to the next column.
     *
     * @return the column's bottom y in page space.
     */
    public double getBottom() {
        return bounds.getMinY();
    }

    @Override
    public String toString() {
        return "ColumnBlock[" + bounds + ", " + range + "]";
    }
}
