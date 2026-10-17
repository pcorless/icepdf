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
package org.icepdf.core.pobjects.graphics.text;

import java.util.function.IntFunction;

/**
 * An immutable document-level text selection: an anchor&#8594;focus caret pair, each a
 * {@code (page, offset)} position in that page's reading-order {@link TextSequence}.  The anchor is
 * the fixed end (where a drag or shift-extend started), the focus the moving end.
 * <p>
 * Four ints and no references to page content, so a selection survives page disposal and simply
 * re-resolves against a page's sequence when it is needed.  Per-page character ranges are derived on
 * demand with {@link #rangeForPage}.  Being immutable, a new instance is made for every change, which
 * suits observable properties in a UI toolkit.
 * <p>
 * Toolkit-neutral counterpart of the Swing viewer's mutable {@code DocumentTextSelection}, with the
 * same anchor/focus semantics.
 *
 * @since 7.5
 */
public final class DocumentSelection {

    private final int anchorPage;
    private final int anchorOffset;
    private final int focusPage;
    private final int focusOffset;

    private DocumentSelection(int anchorPage, int anchorOffset, int focusPage, int focusOffset) {
        if (anchorPage < 0 || focusPage < 0 || anchorOffset < 0 || focusOffset < 0) {
            throw new IllegalArgumentException("negative position: " + anchorPage + ":" + anchorOffset
                    + " -> " + focusPage + ":" + focusOffset);
        }
        this.anchorPage = anchorPage;
        this.anchorOffset = anchorOffset;
        this.focusPage = focusPage;
        this.focusOffset = focusOffset;
    }

    /**
     * A caret with nothing selected (a mouse press or click).
     */
    public static DocumentSelection collapsed(int page, int offset) {
        return new DocumentSelection(page, offset, page, offset);
    }

    public static DocumentSelection of(int anchorPage, int anchorOffset, int focusPage, int focusOffset) {
        return new DocumentSelection(anchorPage, anchorOffset, focusPage, focusOffset);
    }

    /**
     * Every page, start to end.  The end offset is open-ended and clamped per page by
     * {@link #rangeForPage}, so no page has to be loaded to build it.
     *
     * @param pageCount number of pages in the document, at least 1
     */
    public static DocumentSelection all(int pageCount) {
        if (pageCount < 1) throw new IllegalArgumentException("pageCount " + pageCount);
        return new DocumentSelection(0, 0, pageCount - 1, Integer.MAX_VALUE);
    }

    /**
     * The same anchor with a new focus (a drag or shift-extend).
     */
    public DocumentSelection withFocus(int page, int offset) {
        if (page == focusPage && offset == focusOffset) return this;
        return new DocumentSelection(anchorPage, anchorOffset, page, offset);
    }

    public int getAnchorPage() {
        return anchorPage;
    }

    public int getAnchorOffset() {
        return anchorOffset;
    }

    public int getFocusPage() {
        return focusPage;
    }

    public int getFocusOffset() {
        return focusOffset;
    }

    /**
     * @return true when the anchor and focus are the same position: a caret, nothing highlighted.
     */
    public boolean isCollapsed() {
        return anchorPage == focusPage && anchorOffset == focusOffset;
    }

    /**
     * @return true when the anchor is at or before the focus in document order.
     */
    public boolean isForward() {
        return anchorPage < focusPage || (anchorPage == focusPage && anchorOffset <= focusOffset);
    }

    public int startPage() {
        return isForward() ? anchorPage : focusPage;
    }

    public int startOffset() {
        return isForward() ? anchorOffset : focusOffset;
    }

    public int endPage() {
        return isForward() ? focusPage : anchorPage;
    }

    public int endOffset() {
        return isForward() ? focusOffset : anchorOffset;
    }

    /**
     * @return true if the page falls within the selection's page span.
     */
    public boolean coversPage(int pageIndex) {
        return pageIndex >= startPage() && pageIndex <= endPage();
    }

    /**
     * The part of the selection on one page: the first page contributes start&#8594;end of page,
     * pages in between the whole page, and the last page start of page&#8594;end; all clamped to the
     * sequence length.
     *
     * @param pageIndex page to derive the range for
     * @param sequence  that page's text sequence
     * @return the page's offset range, or null if the page is outside the selection or the sequence
     * is null.
     */
    public OffsetRange rangeForPage(int pageIndex, TextSequence sequence) {
        if (sequence == null || !coversPage(pageIndex)) return null;
        int start = pageIndex == startPage() ? startOffset() : 0;
        int end = pageIndex == endPage() ? endOffset() : sequence.length();
        return OffsetRange.of(start, end).clamp(sequence.length());
    }

    /**
     * Extracts the selected text, paragraph-formatted per page ({@link TextSequence#extractText}),
     * with a blank line between pages so a multi-page copy doesn't run pages together.
     *
     * @param sequences page index &#8594; that page's sequence; pages that return null are skipped
     * @return the selected text, empty for a collapsed selection
     */
    public String extractText(IntFunction<TextSequence> sequences) {
        StringBuilder text = new StringBuilder();
        for (int page = startPage(); page <= endPage(); page++) {
            TextSequence sequence = sequences.apply(page);
            OffsetRange range = rangeForPage(page, sequence);
            if (range == null || range.isEmpty()) continue;
            if (text.length() > 0) {
                text.append(sequence.extractSeparator()).append(sequence.extractSeparator());
            }
            text.append(sequence.extractText(range));
        }
        return text.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentSelection)) return false;
        DocumentSelection that = (DocumentSelection) o;
        return anchorPage == that.anchorPage && anchorOffset == that.anchorOffset
                && focusPage == that.focusPage && focusOffset == that.focusOffset;
    }

    @Override
    public int hashCode() {
        int result = anchorPage;
        result = 31 * result + anchorOffset;
        result = 31 * result + focusPage;
        result = 31 * result + focusOffset;
        return result;
    }

    @Override
    public String toString() {
        return "DocumentSelection[anchor(" + anchorPage + ":" + anchorOffset + ") focus("
                + focusPage + ":" + focusOffset + ")]";
    }
}
