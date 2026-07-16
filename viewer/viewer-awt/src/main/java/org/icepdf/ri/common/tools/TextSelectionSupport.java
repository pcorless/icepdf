/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common.tools;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.pobjects.graphics.text.OffsetRange;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentTextSelection;
import org.icepdf.ri.common.views.DocumentViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stateless helpers that bridge the document-level {@link DocumentTextSelection} (owned by
 * the viewer) and the per-page {@link TextSequence} (owned by core).  These are the seam
 * described in TEXT-SELECTION-PLAN.md Appendix C.
 * <br>
 * <b>Note:</b> as of Phase 2 Step 1 these are built and unit-tested but not yet wired into
 * the live handlers/painting; that happens in Step 2.
 *
 * @since 7.5
 */
public final class TextSelectionSupport {

    private static final Logger logger = Logger.getLogger(TextSelectionSupport.class.getName());

    private TextSelectionSupport() {
    }

    /**
     * Derives the character range of a document selection that falls on a single page.
     * The first page contributes {@code anchor -> end}, interior pages the whole page, and
     * the last page {@code start -> focus}.
     *
     * @param sel       document selection
     * @param pageIndex page to derive the range for
     * @param seq       that page's text sequence
     * @return the offset range on this page, or {@code null} if the page is outside the
     * selection.
     */
    public static OffsetRange rangeForPage(DocumentTextSelection sel, int pageIndex, TextSequence seq) {
        if (sel == null || sel.isEmpty() || seq == null) return null;
        if (pageIndex < sel.startPage() || pageIndex > sel.endPage()) return null;
        int lo = (pageIndex == sel.startPage()) ? sel.startOffset() : 0;
        int hi = (pageIndex == sel.endPage()) ? sel.endOffset() : seq.length();
        return OffsetRange.of(lo, hi).clamp(seq.length());
    }

    /**
     * Reproduces the legacy per-node selection flags from an offset range so that existing
     * consumers ({@code getSelectedWordText}, highlight/redaction bounds, {@code getSelected})
     * keep working while selection moves to the offset model (D3 write-through bridge).
     * Clears the page's selection first, then marks the covered glyphs/words.
     *
     * @param pageText page whose node flags should mirror {@code range}
     * @param range    range to apply, may be null/empty to just clear
     */
    public static void applySelectionToFlags(PageText pageText, OffsetRange range) {
        if (pageText == null) return;
        pageText.clearSelected();
        if (range == null || range.isEmpty()) return;
        TextSequence seq = pageText.getTextSequence();
        for (GlyphText glyph : seq.glyphsIn(range)) {
            glyph.setSelected(true);
        }
        for (WordText word : seq.wordsIn(range)) {
            word.setHasSelected(true);
            OffsetRange wr = seq.rangeOf(word);
            // whole word inside the range -> mark the word selected as well (matches selectAll()).
            if (wr != null && range.getStart() <= wr.getStart() && range.getEnd() >= wr.getEnd()) {
                word.setSelected(true);
            }
        }
    }

    /**
     * Projects the authoritative {@link DocumentTextSelection} onto the legacy per-glyph selection
     * flags for every loaded page it covers (write-through bridge), clears pages that dropped out,
     * and repaints the affected pages.  Painting itself derives from the offset model, so unloaded
     * pages still render correctly when they come back.  Shared by the mouse and keyboard paths.
     *
     * @param model document view model holding the authoritative selection and page components.
     */
    public static void applyDocumentSelection(DocumentViewModel model) {
        // the caret just moved; keep it solid and restart the blink cycle.
        CaretBlink.reset();
        DocumentTextSelection selection = model.getTextSelection();

        // clear flags/repaint on pages that were previously selected.
        List<AbstractPageViewComponent> previous = model.getSelectedPageText();
        List<AbstractPageViewComponent> previousCopy =
                previous != null ? new ArrayList<>(previous) : new ArrayList<>();
        model.clearSelectedPageText();
        for (AbstractPageViewComponent page : previousCopy) {
            PageText pageText = loadedPageText(page);
            if (pageText != null) applySelectionToFlags(pageText, null);
            page.repaint();
        }
        if (selection.isEmpty()) return;

        // apply flags/repaint on pages the selection now covers.
        List<AbstractPageViewComponent> pages = model.getPageComponents();
        for (int index = selection.startPage(); index <= selection.endPage(); index++) {
            if (index < 0 || index >= pages.size()) continue;
            AbstractPageViewComponent page = pages.get(index);
            PageText pageText = loadedPageText(page);
            if (pageText != null) {
                OffsetRange range = rangeForPage(selection, index, pageText.getTextSequence());
                applySelectionToFlags(pageText, range);
            }
            model.addSelectedPageText(page);
            page.repaint();
        }
    }

    /**
     * Non-initializing page text accessor; returns null rather than triggering a parse on the EDT.
     *
     * @param page page component
     * @return the page's already-built text, or null if not loaded.
     */
    public static PageText loadedPageText(AbstractPageViewComponent page) {
        Page currentPage = page.getPage();
        if (currentPage == null) return null;
        Shapes shapes = currentPage.getShapes();
        return shapes != null ? shapes.getPageText() : null;
    }

    /**
     * Extracts the selected text for a document selection by walking each covered page's
     * {@link TextSequence}.  Independent of the legacy node flags.
     *
     * @param sel      document selection
     * @param document document to read page text from
     * @return concatenated selected text, or empty string.
     */
    public static String selectedText(DocumentTextSelection sel, Document document) {
        if (sel == null || sel.isEmpty() || document == null) return "";
        StringBuilder sb = new StringBuilder();
        try {
            for (int p = sel.startPage(); p <= sel.endPage(); p++) {
                PageText pageText = document.getPageText(p);
                if (pageText == null) continue;
                TextSequence seq = pageText.getTextSequence();
                OffsetRange r = rangeForPage(sel, p, seq);
                if (r != null) sb.append(seq.text(r));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Text selection extraction interrupted.");
            }
        }
        return sb.toString();
    }
}
