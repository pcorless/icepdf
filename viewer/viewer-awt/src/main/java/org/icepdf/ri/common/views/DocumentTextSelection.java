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
package org.icepdf.ri.common.views;

/**
 * The authoritative text selection for a document: a single anchor&#8594;focus caret pair
 * expressed as document-level {@code (page, offset)} positions.  {@code anchor} is the
 * fixed end (where a drag or shift-extend started); {@code focus} is the moving end.
 * Per-page character ranges are derived from this on demand
 * (see {@code TextSelectionSupport.rangeForPage}).
 * <br>
 * The state is intentionally tiny (four ints) so it survives page disposal by the memory
 * manager; the offsets re-resolve against a page's {@code TextSequence} when it is
 * re-initialized.
 *
 * @since 7.5
 */
public final class DocumentTextSelection {

    private int anchorPage;
    private int anchorOffset;
    private int focusPage;
    private int focusOffset;
    private boolean empty = true;

    /**
     * @return true when there is no selection at all.
     */
    public boolean isEmpty() {
        return empty;
    }

    /**
     * @return true when a caret exists but nothing is highlighted (anchor == focus).
     */
    public boolean isCollapsed() {
        return !empty && anchorPage == focusPage && anchorOffset == focusOffset;
    }

    /**
     * Places both anchor and focus at a single position (mouse press / click).
     *
     * @param page   page index
     * @param offset char offset within that page's sequence
     */
    public void collapseTo(int page, int offset) {
        anchorPage = focusPage = page;
        anchorOffset = focusOffset = offset;
        empty = false;
    }

    /**
     * Moves the focus end only, keeping the anchor fixed (drag / shift-extend).
     *
     * @param page   page index
     * @param offset char offset within that page's sequence
     */
    public void extendTo(int page, int offset) {
        if (empty) {
            collapseTo(page, offset);
            return;
        }
        focusPage = page;
        focusOffset = offset;
    }

    /**
     * Sets both ends explicitly (e.g. select-all, word/line click, API).
     */
    public void set(int anchorPage, int anchorOffset, int focusPage, int focusOffset) {
        this.anchorPage = anchorPage;
        this.anchorOffset = anchorOffset;
        this.focusPage = focusPage;
        this.focusOffset = focusOffset;
        empty = false;
    }

    /**
     * Clears the selection.
     */
    public void clear() {
        empty = true;
        anchorPage = focusPage = 0;
        anchorOffset = focusOffset = 0;
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

    @Override
    public String toString() {
        return "DocumentTextSelection[" + (empty ? "empty" :
                "anchor(" + anchorPage + ":" + anchorOffset + ") focus(" + focusPage + ":" + focusOffset + ")") + ']';
    }
}
