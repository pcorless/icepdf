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

import org.icepdf.core.pobjects.graphics.text.*;


/**
 * Keyboard caret movement over a {@link DocumentSelection}'s focus, with no toolkit in it.  Each
 * move returns the new selection: collapsed at the new position, or - when extending (shift) -
 * the same anchor with the focus moved.
 * <p>
 * Moves cross page boundaries: right/left past a page's end/start, word moves at a page edge,
 * and up/down off the first/last line land on the next page that has text (pages with no text
 * layer, such as scans, are skipped).  If a page on the way isn't loaded the move is dropped (the
 * selection is returned unchanged) and the page is requested, so the next keypress succeeds;
 * nothing blocks.
 * <p>
 * Up/down keep a sticky goal x, as editors do: moving through a short line and back returns to the
 * original column.  Any change not made by a vertical move resets it.
 */
final class CaretNavigator {

    enum Move {LEFT, RIGHT, WORD_LEFT, WORD_RIGHT, LINE_START, LINE_END, UP, DOWN}

    /** Page text as the view has it. */
    interface Texts {
        /** The page's sequence, or null if not loaded or the page has no text. */
        TextSequence get(int pageIndex);

        /** True once the page's text is loaded, even if it turned out to have none. */
        boolean isLoaded(int pageIndex);

        /** Asks for the page's text to be loaded. */
        void request(int pageIndex);
    }

    private static final int NOT_LOADED = -2;

    private final Texts texts;
    private final int pageCount;
    private double goalX = -1;
    // the selection this navigator last produced by a vertical move; anything else resets goalX.
    private DocumentSelection lastVertical;

    CaretNavigator(Texts texts, int pageCount) {
        this.texts = texts;
        this.pageCount = pageCount;
    }

    /**
     * @return the selection after the move; {@code current} itself if nothing moved (no selection,
     * text not loaded, or already at the document's edge)
     */
    DocumentSelection move(DocumentSelection current, Move move, boolean extend) {
        if (current == null) return null;
        if (current != lastVertical) goalX = -1;
        int page = current.getFocusPage();
        TextSequence sequence = loadedText(page);
        if (sequence == null) return current;
        int offset = Math.min(current.getFocusOffset(), sequence.length());
        switch (move) {
            case RIGHT:
            case LEFT:
                return horizontal(current, sequence, page, offset, move == Move.RIGHT, extend);
            case WORD_RIGHT:
            case WORD_LEFT: {
                boolean forward = move == Move.WORD_RIGHT;
                int boundary = sequence.nextBoundary(offset, BreakType.WORD, forward);
                // at a page edge, roll over one glyph to the neighbouring page.
                if (boundary == offset) return horizontal(current, sequence, page, offset, forward, extend);
                return apply(current, page, boundary, extend);
            }
            case LINE_START:
            case LINE_END: {
                OffsetRange line = sequence.lineRange(offset);
                return apply(current, page, move == Move.LINE_END ? line.getEnd() : line.getStart(), extend);
            }
            default:
                return vertical(current, sequence, page, offset, move == Move.DOWN, extend);
        }
    }

    private DocumentSelection horizontal(DocumentSelection current, TextSequence sequence, int page, int offset,
                                         boolean forward, boolean extend) {
        if (forward) {
            if (offset < sequence.length()) {
                return apply(current, page, sequence.nextBoundary(offset, BreakType.GLYPH, true), extend);
            }
            int next = nextTextPage(page, 1);
            return next >= 0 ? apply(current, next, 0, extend) : current;
        }
        if (offset > 0) return apply(current, page, sequence.nextBoundary(offset, BreakType.GLYPH, false), extend);
        int previous = nextTextPage(page, -1);
        return previous >= 0 ? apply(current, previous, texts.get(previous).length(), extend) : current;
    }

    private DocumentSelection vertical(DocumentSelection current, TextSequence sequence, int page, int offset,
                                       boolean down, boolean extend) {
        Caret caret = new Caret(offset, Bias.FORWARD);
        if (goalX < 0) goalX = sequence.caretRect(caret).getX();
        Caret adjacent = down ? sequence.caretBelow(caret, goalX) : sequence.caretAbove(caret, goalX);
        DocumentSelection result;
        if (adjacent != null) {
            result = apply(current, page, adjacent.getOffset(), extend);
        } else {
            int target = nextTextPage(page, down ? 1 : -1);
            if (target < 0) return current;
            TextSequence other = texts.get(target);
            int line = down ? 0 : other.lineCount() - 1;
            result = apply(current, target, other.caretAtLine(line, goalX).getOffset(), extend);
        }
        lastVertical = result;
        return result;
    }

    private static DocumentSelection apply(DocumentSelection current, int page, int offset, boolean extend) {
        return extend ? current.withFocus(page, offset) : DocumentSelection.collapsed(page, offset);
    }

    /** The page's sequence if loaded and non-empty; requests it if not loaded. */
    private TextSequence loadedText(int page) {
        if (page < 0 || page >= pageCount) return null;
        if (!texts.isLoaded(page)) {
            texts.request(page);
            return null;
        }
        TextSequence sequence = texts.get(page);
        return sequence != null && sequence.length() > 0 ? sequence : null;
    }

    /**
     * The nearest page after {@code from} in direction {@code step} with text, or -1 at the
     * document's edge, or {@link #NOT_LOADED} (with that page requested) if one on the way isn't
     * loaded yet.
     */
    private int nextTextPage(int from, int step) {
        for (int page = from + step; page >= 0 && page < pageCount; page += step) {
            if (!texts.isLoaded(page)) {
                texts.request(page);
                return NOT_LOADED;
            }
            TextSequence sequence = texts.get(page);
            if (sequence != null && sequence.length() > 0 && sequence.lineCount() > 0) return page;
        }
        return -1;
    }
}
