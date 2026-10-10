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
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.core.search.TextSearch;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

/**
 * Whole-document search and hit navigation, with no toolkit in it.
 * <p>
 * {@link #run} scans pages in order on the caller's thread (a worker in the view), handing each
 * page's hits to a listener as soon as the page is done, so results appear progressively and a
 * cancel takes effect between pages.  Navigation is a pair of pure functions over the hit list.
 */
final class DocumentSearch {

    interface Listener {
        /** A page has been searched; {@code hits} may be empty.  Called on the searching thread. */
        void pageSearched(int pageIndex, List<SearchHit> hits, int pagesDone, int pageCount);
    }

    private DocumentSearch() {
    }

    /**
     * @param texts     page &#8594; its sequence, loading as needed (blocking); null for no text
     * @param terms     terms to find; hits from several terms are merged in document order
     * @param pageCount pages in the document
     * @param cancelled polled between pages
     * @param listener  receives each page's hits
     * @return false if cancelled before the last page
     */
    static boolean run(IntFunction<TextSequence> texts, List<SearchTerm> terms, int pageCount,
                       BooleanSupplier cancelled, Listener listener) {
        for (int page = 0; page < pageCount; page++) {
            if (cancelled.getAsBoolean()) return false;
            listener.pageSearched(page, searchPage(texts.apply(page), page, terms), page + 1, pageCount);
        }
        return true;
    }

    /** One page's hits for all terms, in document order. */
    static List<SearchHit> searchPage(TextSequence sequence, int pageIndex, List<SearchTerm> terms) {
        if (sequence == null) return Collections.emptyList();
        List<SearchHit> hits = new ArrayList<>();
        for (SearchTerm term : terms) {
            for (OffsetRange range : TextSearch.find(sequence, term)) {
                Rectangle2D.Double bounds = null;
                for (Rectangle2D.Double r : sequence.rectsFor(range)) {
                    if (bounds == null) bounds = new Rectangle2D.Double(r.x, r.y, r.width, r.height);
                    else bounds.add(r);
                }
                if (bounds == null) continue;
                hits.add(new SearchHit(pageIndex, range, oneLine(sequence.text(range)),
                        bounds.x, bounds.y, bounds.width, bounds.height,
                        contextBefore(sequence, range.getStart()), contextAfter(sequence, range.getEnd())));
            }
        }
        Collections.sort(hits);
        return hits;
    }

    /** Characters of context kept on each side of a hit. */
    static final int CONTEXT = 40;

    /** Up to {@link #CONTEXT} characters before {@code start} on its line, starting at a word. */
    static String contextBefore(TextSequence sequence, int start) {
        int lineStart = sequence.lineRange(start).getStart();
        if (start <= lineStart) return "";
        int from = Math.max(lineStart, start - CONTEXT);
        String text = oneLine(sequence.text(from, start));
        if (from > lineStart) {
            int space = text.indexOf(' ');
            text = "\u2026" + (space >= 0 && space < text.length() - 1 ? text.substring(space + 1) : text);
        }
        return text;
    }

    /** Up to {@link #CONTEXT} characters after {@code end} on its line, ending at a word. */
    static String contextAfter(TextSequence sequence, int end) {
        if (end <= 0 || end >= sequence.length()) return "";
        int lineEnd = sequence.lineRange(end - 1).getEnd();
        if (end >= lineEnd) return "";
        int to = Math.min(lineEnd, end + CONTEXT);
        String text = oneLine(sequence.text(end, to));
        if (to < lineEnd) {
            int space = text.lastIndexOf(' ');
            text = (space > 0 ? text.substring(0, space) : text) + "\u2026";
        }
        return text;
    }

    private static String oneLine(String text) {
        return text.replace('\n', ' ').replace('\r', ' ');
    }

    /**
     * The hit after the current one, wrapping; with no current hit, the first hit at or after
     * {@code fromPage} (else the first hit).
     *
     * @return an index into {@code hits}, or -1 if there are none
     */
    static int next(List<SearchHit> hits, int current, int fromPage) {
        if (hits.isEmpty()) return -1;
        if (current >= 0 && current < hits.size()) return (current + 1) % hits.size();
        for (int i = 0; i < hits.size(); i++) {
            if (hits.get(i).pageIndex() >= fromPage) return i;
        }
        return 0;
    }

    /**
     * The hit before the current one, wrapping; with no current hit, the last hit at or before
     * {@code fromPage} (else the last hit).
     */
    static int previous(List<SearchHit> hits, int current, int fromPage) {
        if (hits.isEmpty()) return -1;
        if (current >= 0 && current < hits.size()) return (current - 1 + hits.size()) % hits.size();
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).pageIndex() <= fromPage) return i;
        }
        return hits.size() - 1;
    }
}
