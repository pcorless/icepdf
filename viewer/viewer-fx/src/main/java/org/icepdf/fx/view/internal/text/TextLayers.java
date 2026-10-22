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
package org.icepdf.fx.view.internal.text;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import org.icepdf.core.pobjects.graphics.text.Bias;
import org.icepdf.core.pobjects.graphics.text.Caret;
import org.icepdf.core.pobjects.graphics.text.DocumentSelection;
import org.icepdf.core.pobjects.graphics.text.OffsetRange;
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.fx.view.PagePoint;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.SearchHit;

import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Page text in the view: loading it off the FX thread while it is needed, drawing the selection,
 * search hits and the blinking caret on each page, and keyboard caret movement.
 */
public final class TextLayers {

    /** A page's text drawing: what {@link TextLayers} sets on each visible page. */
    public interface Overlay {
        int getPageIndex();

        void setSelection(TextSequence sequence, OffsetRange range);

        void setSearchHits(TextSequence sequence, List<SearchHit> hits);

        void setCaret(Rectangle2D.Double userRect);

        void setCaretVisible(boolean visible);
    }

    private static final Duration CARET_BLINK = Duration.millis(500);

    private final PdfView view;
    private final PageTextLoader loader;
    private final IntFunction<List<SearchHit>> searchHits;
    private final IntSupplier pageCount;
    private final Timeline caretBlink = new Timeline();
    // caret blink phase; the caret is drawn on the focus page's layer only.
    private boolean caretOn = true;
    private CaretNavigator caretNavigator;

    /**
     * @param searchHits the view's search hits on a page
     * @param pageCount  the document's page count
     * @param overlays   the visible pages, for the caret blink
     */
    public TextLayers(PdfView view, PageTextLoader loader, IntFunction<List<SearchHit>> searchHits,
                      IntSupplier pageCount, Supplier<Collection<? extends Overlay>> overlays) {
        this.view = view;
        this.loader = loader;
        this.searchHits = searchHits;
        this.pageCount = pageCount;
        caretBlink.getKeyFrames().add(new KeyFrame(CARET_BLINK, e -> {
            caretOn = !caretOn;
            overlays.get().forEach(layer -> layer.setCaretVisible(caretOn));
        }));
        caretBlink.setCycleCount(Animation.INDEFINITE);
    }

    public PageTextLoader loader() {
        return loader;
    }

    /** A new document: its text loads afresh and the caret navigator is rebuilt. */
    public void documentChanged(org.icepdf.core.pobjects.Document document) {
        loader.setDocument(document);
        caretNavigator = null;
    }

    public void dispose() {
        loader.clear();
        caretBlink.stop();
    }

    /** Page text is needed to select, or to draw a selection or search hits. */
    public boolean needsText() {
        return view.getToolMode().selectsText() || view.getTextSelection() != null || !view.getSearchHits().isEmpty();
    }

    /** Keeps the text of the pages {@code visible} accepts while text is needed, else none. */
    public void retain(IntPredicate visible) {
        loader.retain(needsText() ? visible : page -> false);
    }

    /** Draws a page's selection, search hits and caret, loading its text if needed. */
    public void update(Overlay layer) {
        if (!needsText()) {
            layer.setSelection(null, null);
            layer.setSearchHits(null, List.of());
            layer.setCaret(null);
            return;
        }
        int index = layer.getPageIndex();
        loader.request(index);
        TextSequence sequence = loader.get(index);
        DocumentSelection selection = view.getTextSelection();
        layer.setSelection(sequence, selection != null ? selection.rangeForPage(index, sequence) : null);
        layer.setSearchHits(sequence, searchHits.apply(index));
        // the caret sits at the focus end, as in the Swing viewer.
        boolean caretHere = isCaretActive() && sequence != null && selection.getFocusPage() == index;
        layer.setCaret(caretHere ? caretRect(sequence, selection) : null);
        layer.setCaretVisible(caretOn);
    }

    /** The focus caret's rectangle in the page's user space. */
    public static Rectangle2D.Double caretRect(TextSequence sequence, DocumentSelection selection) {
        return sequence.caretRect(new Caret(Math.min(selection.getFocusOffset(), sequence.length()), Bias.FORWARD));
    }

    /** A caret shows in the select tool, while focused, when there is a selection. */
    private boolean isCaretActive() {
        return view.getToolMode().selectsText() && view.isFocused() && view.getTextSelection() != null;
    }

    /** The selection moved: the caret shows solid again, so it is visible straight after interaction. */
    public void selectionChanged() {
        caretOn = true;
        updateCaretBlink(true);
    }

    /** Runs the blink while a caret is active; {@code restart} resets the cycle (caret moved). */
    public void updateCaretBlink(boolean restart) {
        if (!isCaretActive()) {
            caretBlink.stop();
            caretOn = true;
        } else if (restart || caretBlink.getStatus() != Animation.Status.RUNNING) {
            caretBlink.playFromStart();
        }
    }

    /** The page's loaded text, or null if it isn't loaded (or has none). */
    public TextSequence sequence(int pageIndex) {
        return loader.get(pageIndex);
    }

    /** True once the page's text is loaded, even if it has none. */
    public boolean isLoaded(int pageIndex) {
        return loader.isLoaded(pageIndex);
    }

    /** Loads a page's text, keeping it while off-screen (for the screen reader's page). */
    public void requestKept(int pageIndex) {
        loader.requestKept(pageIndex);
    }

    /** True if a page point is over a glyph of a page whose text is loaded. */
    public boolean isOverText(PagePoint point) {
        if (point == null) return false;
        TextSequence sequence = loader.get(point.pageIndex());
        return sequence != null && sequence.hitsText(point.toAwt());
    }

    /** Keyboard caret movement over the loaded text; rebuilt per document. */
    public CaretNavigator caretNavigator() {
        if (caretNavigator == null) {
            caretNavigator = new CaretNavigator(new CaretNavigator.Texts() {
                public TextSequence get(int pageIndex) {
                    return loader.get(pageIndex);
                }

                public boolean isLoaded(int pageIndex) {
                    return loader.isLoaded(pageIndex);
                }

                public void request(int pageIndex) {
                    loader.requestKept(pageIndex);
                }
            }, pageCount.getAsInt());
        }
        return caretNavigator;
    }
}
