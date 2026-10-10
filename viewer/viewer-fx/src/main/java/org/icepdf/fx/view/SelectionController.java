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

import org.icepdf.core.pobjects.graphics.text.ColumnBlock;
import org.icepdf.core.pobjects.graphics.text.DocumentSelection;
import org.icepdf.core.pobjects.graphics.text.OffsetRange;
import org.icepdf.core.pobjects.graphics.text.TextSequence;

import java.awt.geom.Point2D;
import java.util.function.IntFunction;

/**
 * Mouse text-selection logic with no toolkit in it: page points in, {@link DocumentSelection}s out.
 * The FX handler only converts events, so everything here is unit-testable against real PDFs
 * without a display.
 * <p>
 * Gestures, as in the Swing viewer:
 * <ul>
 *     <li>press: caret at the point (the selection collapses there);</li>
 *     <li>shift+press: extend the current selection to the point;</li>
 *     <li>double / triple press: select the word / line under the point;</li>
 *     <li>drag: extend to the point, column-aware (below).</li>
 * </ul>
 * Column-aware dragging (the Swing viewer's D4 rule): a point over no glyph - a line gap, margin
 * or gutter - resolves only within the column the pointer is over, or, in a true gutter, within the
 * column the drag started in, so a sideways drift can't jump the selection a whole column.  A
 * point directly over another column's text is never constrained, so a deliberate cross-column
 * drag still selects on into it.
 * <p>
 * Pages whose text isn't loaded yet are ignored: the selection is returned unchanged.
 */
final class SelectionController {

    private final IntFunction<TextSequence> texts;
    // where the drag started, for the gutter fallback.
    private ColumnBlock anchorColumn;
    private int anchorColumnPage = -1;
    // a double/triple click selected a word/line; the drag that follows mustn't collapse it.
    private boolean granular;

    /**
     * @param texts page index &#8594; that page's loaded sequence, or null if it isn't loaded
     */
    SelectionController(IntFunction<TextSequence> texts) {
        this.texts = texts;
    }

    /**
     * @param current    the selection before the press, may be null
     * @param point      where the press landed, or null if it was off every page
     * @param clickCount 1 single, 2 double, 3+ triple
     * @param extend     shift held
     * @return the new selection, which may be {@code current} unchanged or null
     */
    DocumentSelection press(DocumentSelection current, PagePoint point, int clickCount, boolean extend) {
        granular = false;
        if (point == null) {
            // a press between pages starts nothing; a drag onto a page will start a selection.
            anchorColumn = null;
            anchorColumnPage = -1;
            return extend ? current : null;
        }
        TextSequence sequence = texts.apply(point.pageIndex());
        if (sequence == null) return current;
        Point2D.Double p = point.toAwt();
        int offset = sequence.caretAt(p).getOffset();
        if (clickCount >= 2) {
            OffsetRange range = clickCount == 2 ? sequence.wordRange(offset) : sequence.lineRange(offset);
            if (range.isEmpty()) return current;
            granular = true;
            return DocumentSelection.of(point.pageIndex(), range.getStart(), point.pageIndex(), range.getEnd());
        }
        if (extend && current != null) {
            return current.withFocus(point.pageIndex(), columnAwareOffset(sequence, p, point.pageIndex()));
        }
        // resolve the anchor column geometrically by the point, not the offset: interleaved reading
        // orders can make column offset ranges overlap, but their x-bands never do.
        anchorColumn = sequence.columnAt(p);
        anchorColumnPage = point.pageIndex();
        return DocumentSelection.collapsed(point.pageIndex(), offset);
    }

    /**
     * @param current the selection so far, may be null (the drag began off the pages)
     * @param point   the pointer, already snapped onto the nearest page if it is between pages
     * @return the extended selection
     */
    DocumentSelection drag(DocumentSelection current, PagePoint point) {
        if (granular || point == null) return current;
        TextSequence sequence = texts.apply(point.pageIndex());
        if (sequence == null) return current;
        Point2D.Double p = point.toAwt();
        int offset = columnAwareOffset(sequence, p, point.pageIndex());
        if (current == null) {
            anchorColumn = sequence.columnAt(p);
            anchorColumnPage = point.pageIndex();
            return DocumentSelection.collapsed(point.pageIndex(), offset);
        }
        return current.withFocus(point.pageIndex(), offset);
    }

    void release() {
        granular = false;
    }

    private int columnAwareOffset(TextSequence sequence, Point2D.Double p, int pageIndex) {
        ColumnBlock pointColumn = sequence.columnAt(p);
        ColumnBlock constrain = pointColumn != null ? pointColumn
                : (pageIndex == anchorColumnPage ? anchorColumn : null);
        return sequence.caretAt(p, constrain).getOffset();
    }
}
