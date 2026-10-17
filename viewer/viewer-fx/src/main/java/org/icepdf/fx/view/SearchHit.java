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

import org.icepdf.core.pobjects.graphics.text.OffsetRange;

/**
 * One search match: a range of a page's reading-order text, plus what was matched and where it is.
 *
 * @param pageIndex zero-based page
 * @param range     canonical offsets into the page's {@code TextSequence}; usable directly as a
 *                  {@code DocumentSelection}
 * @param text      the matched text
 * @param x         left of the match's bounds, PDF user space
 * @param y         bottom of the match's bounds, PDF user space
 * @param width     bounds width
 * @param height    bounds height
 * @param before    text leading up to the match on its line, for a results list; may be empty
 * @param after     text following the match on its line; may be empty
 */
public record SearchHit(int pageIndex, OffsetRange range, String text,
                        double x, double y, double width, double height,
                        String before, String after) implements Comparable<SearchHit> {

    /** A hit without context. */
    public SearchHit(int pageIndex, OffsetRange range, String text, double x, double y, double width, double height) {
        this(pageIndex, range, text, x, y, width, height, "", "");
    }

    /** The centre of the match's bounds, for {@link PdfView#ensureVisible}. */
    public PagePoint centre() {
        return new PagePoint(pageIndex, x + width / 2, y + height / 2);
    }

    /** Document order: page, then offset. */
    @Override
    public int compareTo(SearchHit other) {
        if (pageIndex != other.pageIndex) return Integer.compare(pageIndex, other.pageIndex);
        if (range.getStart() != other.range.getStart()) return Integer.compare(range.getStart(), other.range.getStart());
        return Integer.compare(range.getEnd(), other.range.getEnd());
    }
}
