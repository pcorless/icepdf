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

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import org.icepdf.core.pobjects.graphics.text.DocumentSelection;
import org.icepdf.core.pobjects.graphics.text.OffsetRange;
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.fx.i18n.Messages;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * What a screen reader sees of a {@link PdfView}: a read-only text area holding one page's text,
 * the way a browser exposes a document.  The page is the one the caret is on, or the current page
 * when there's no caret.  Offsets are the page's {@link TextSequence} offsets, so the caret and
 * selection a reader tracks are the view's own; lines are the sequence's reading-order lines, and
 * bounds and hit tests go through the page transform, so a reader's highlight lands on the glyphs.
 * <p>
 * The attributes follow JavaFX's {@code TextArea} contract (the one the Windows UI Automation and
 * macOS bridges read): {@code BOUNDS_FOR_RANGE} takes an inclusive end and answers in screen
 * coordinates, {@code LINE_END} includes the line's newline, and both {@code LINE_*} answer null
 * past the last line.
 * <p>
 * JavaFX has no way to announce a message, so a page change is spoken as a caret move: while an
 * assistive technology is connected the view keeps a caret on the page being read (placed at the
 * start of the current page on focus and when the page changes), and a reader speaks the line the
 * caret lands on.  None of this runs without an assistive technology connected.
 */
final class PdfViewAccessibility {

    private final PdfViewSkin skin;
    private final PdfView view;
    // what a reader names the view by (LABELED_BY): "Page 3 of 10".  Never shown.
    private final Label name = new Label();
    // the page last exposed, and its text, to notice when TEXT changes.
    private int exposedPage = -1;
    private TextSequence exposedText;
    private int[] lineStarts = new int[0];

    PdfViewAccessibility(PdfViewSkin skin) {
        this.skin = skin;
        this.view = skin.getSkinnable();
        updateName();
    }

    // whether an assistive technology is connected; a smoke test stands in for one.
    static java.util.function.BooleanSupplier active = Platform::isAccessibilityActive;

    static boolean isActive() {
        return active.getAsBoolean();
    }

    // ---- what the view exposes ------------------------------------------------------------

    /** The page whose text is exposed: the caret's page, else the current page; -1 with no document. */
    int page() {
        if (view.getDocument() == null || view.getPageCount() == 0) return -1;
        DocumentSelection selection = view.getTextSelection();
        return selection != null ? selection.getFocusPage() : view.getCurrentPageIndex();
    }

    /** The exposed page's text, or null if it isn't loaded (it is then requested) or has none. */
    private TextSequence sequence() {
        int page = page();
        if (page < 0) return null;
        TextSequence sequence = skin.textSequence(page);
        if (sequence == null) skin.requestText(page);
        if (page != exposedPage || sequence != exposedText) {
            exposedPage = page;
            exposedText = sequence;
            lineStarts = lineStarts(sequence);
        }
        return sequence;
    }

    private String text() {
        TextSequence sequence = sequence();
        return sequence != null ? sequence.text().toString() : "";
    }

    Object query(AccessibleAttribute attribute, Object... parameters) {
        switch (attribute) {
            case TEXT:
                return text();
            case EDITABLE:
                return false;
            case LABELED_BY:
                updateName();
                return name;
            case HELP: {
                int page = page();
                if (page < 0) return null;
                return skin.isTextLoaded(page) && sequence() == null ? Messages.get("pdfView.a11y.noText") : null;
            }
            case FONT:
                return Font.getDefault();
            case CARET_OFFSET:
                return caret();
            case SELECTION_START:
                return selection().getStart();
            case SELECTION_END:
                return selection().getEnd();
            case LINE_FOR_OFFSET: {
                TextSequence sequence = sequence();
                int offset = (Integer) parameters[0];
                if (sequence == null || offset > sequence.length()) return sequence == null && offset == 0 ? 0 : null;
                return lineFor(offset);
            }
            case LINE_START: {
                int line = (Integer) parameters[0];
                sequence();
                return line >= 0 && line < lineStarts.length ? lineStarts[line] : null;
            }
            case LINE_END: {
                int line = (Integer) parameters[0];
                sequence();
                if (line < 0 || line >= lineStarts.length) return null;
                return line + 1 < lineStarts.length ? lineStarts[line + 1] : text().length();
            }
            case BOUNDS_FOR_RANGE:
                return bounds((Integer) parameters[0], (Integer) parameters[1]);
            case OFFSET_AT_POINT:
                return offsetAt((Point2D) parameters[0]);
            default:
                return null;
        }
    }

    /** @return true if handled */
    boolean execute(AccessibleAction action, Object... parameters) {
        int page = page();
        if (page < 0) return false;
        switch (action) {
            case SET_TEXT_SELECTION: {
                int start = (Integer) parameters[0];
                int end = (Integer) parameters[1];
                view.setTextSelection(DocumentSelection.of(page, start, page, end));
                skin.revealCaret();
                return true;
            }
            case SHOW_TEXT_RANGE: {
                TextSequence sequence = sequence();
                if (sequence == null) return true;
                List<Rectangle2D.Double> rects = sequence.rectsFor((Integer) parameters[0], (Integer) parameters[1]);
                if (!rects.isEmpty()) {
                    Rectangle2D.Double r = rects.get(0);
                    view.ensureVisible(new PagePoint(page, r.getCenterX(), r.getCenterY()));
                }
                return true;
            }
            default:
                return false;
        }
    }

    private int caret() {
        DocumentSelection selection = view.getTextSelection();
        TextSequence sequence = sequence();
        if (selection == null || sequence == null) return 0;
        return Math.min(selection.getFocusOffset(), sequence.length());
    }

    private OffsetRange selection() {
        DocumentSelection selection = view.getTextSelection();
        TextSequence sequence = sequence();
        if (selection == null || sequence == null) return OffsetRange.of(0, 0);
        OffsetRange range = selection.rangeForPage(page(), sequence);
        if (range == null || range.isEmpty()) {
            int caret = caret();
            return OffsetRange.of(caret, caret);
        }
        return range;
    }

    private int lineFor(int offset) {
        int line = 0;
        for (int i = 1; i < lineStarts.length && lineStarts[i] <= offset; i++) line = i;
        return line;
    }

    private Bounds[] bounds(int start, int endInclusive) {
        TextSequence sequence = sequence();
        int page = page();
        if (sequence == null) return new Bounds[0];
        List<Bounds> out = new ArrayList<>();
        for (Rectangle2D.Double rect : sequence.rectsFor(start, Math.min(endInclusive + 1, sequence.length()))) {
            Bounds screen = skin.pageRectToScreen(page, rect);
            if (screen != null) out.add(screen);
        }
        return out.toArray(new Bounds[0]);
    }

    private Integer offsetAt(Point2D screenPoint) {
        TextSequence sequence = sequence();
        if (sequence == null || screenPoint == null) return 0;
        Point2D local = view.screenToLocal(screenPoint);
        PagePoint point = local != null ? skin.pageAt(local.getX(), local.getY()) : null;
        if (point == null || point.pageIndex() != page()) return caret();
        return sequence.caretAt(point.toAwt()).getOffset();
    }

    /** Line starts in the sequence's canonical text, which has one '\n' between lines. */
    private static int[] lineStarts(TextSequence sequence) {
        if (sequence == null) return new int[]{0};
        CharSequence text = sequence.text();
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') starts.add(i + 1);
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    private void updateName() {
        int page = page();
        String text = page < 0 ? Messages.get("pdfView.a11y.noDocument")
                : Messages.format("pdfView.a11y.page", page + 1, view.getPageCount());
        // the label is never in a scene, so it has no skin to answer TEXT from its text.
        name.setText(text);
        name.setAccessibleText(text);
    }

    // ---- telling the reader what changed ---------------------------------------------------

    /** The selection (and so the caret) changed. */
    void selectionChanged() {
        if (!isActive()) return;
        if (page() != exposedPage) {
            pageChanged();
        } else {
            notifySelection();
        }
    }

    /**
     * The current page changed.  With a caret on another page, it moves to the start of this one
     * so a reader speaks the new page's first line; otherwise the exposed text just changes.
     */
    void currentPageChanged() {
        if (!isActive()) return;
        DocumentSelection selection = view.getTextSelection();
        int current = view.getCurrentPageIndex();
        if (selection != null && selection.isCollapsed() && selection.getFocusPage() != current && followsCaret()) {
            view.setTextSelection(DocumentSelection.collapsed(current, 0));   // -> selectionChanged
        } else if (page() != exposedPage) {
            pageChanged();
        }
    }

    /** The view took keyboard focus: give a reader a caret to read from, at the current page's start. */
    void focusGained() {
        if (!isActive()) return;
        if (view.getTextSelection() == null && followsCaret() && page() >= 0) {
            view.setTextSelection(DocumentSelection.collapsed(view.getCurrentPageIndex(), 0));
        }
    }

    /** A page's text finished loading. */
    void textReady(int pageIndex) {
        if (!isActive() || pageIndex != page()) return;
        exposedPage = -1;
        pageChanged();
    }

    /** A new document, or none. */
    void documentChanged() {
        exposedPage = -1;
        exposedText = null;
        updateName();
        if (isActive()) pageChanged();
    }

    private boolean followsCaret() {
        return view.isFocused() && view.getToolMode().selectsText();
    }

    private void pageChanged() {
        updateName();
        sequence();
        view.notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT);
        notifySelection();
    }

    private void notifySelection() {
        view.notifyAccessibleAttributeChanged(AccessibleAttribute.SELECTION_START);
        view.notifyAccessibleAttributeChanged(AccessibleAttribute.SELECTION_END);
        view.notifyAccessibleAttributeChanged(AccessibleAttribute.CARET_OFFSET);
    }
}
