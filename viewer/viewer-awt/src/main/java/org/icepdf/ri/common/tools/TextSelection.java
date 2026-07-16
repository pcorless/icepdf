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

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.text.Bias;
import org.icepdf.core.pobjects.graphics.text.BreakType;
import org.icepdf.core.pobjects.graphics.text.Caret;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.OffsetRange;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentTextSelection;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;
import org.icepdf.ri.common.views.PageViewComponentImpl;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TextSelection captures the work needed to do basic text, word and line selection.  Selection is
 * expressed as caret character offsets into a page's {@link TextSequence}: a mouse point maps to a
 * caret via {@link TextSequence#caretAt}, and the document-level anchor&#8594;focus selection is
 * held by the {@link DocumentViewModel} ({@link DocumentTextSelection}).  The legacy per-glyph
 * selection flags are kept in sync (write-through) so that redaction, highlight and text-edit
 * consumers keep working; painting and text extraction derive directly from the offset model.
 */
public class TextSelection extends SelectionBoxHandler {

    protected static final Logger logger =
            Logger.getLogger(TextSelection.class.getName());

    public int selectedCount;

    protected Point lastMousePressedLocation;
    protected Point lastMouseLocation;

    // Pointer to make sure the GC doesn't collect a page while selection state is present
    protected Page pageLock;

    // sticky page-space column for vertical caret navigation; -1 when not navigating vertically.
    protected double goalX = -1;

    public TextSelection(DocumentViewController documentViewController, AbstractPageViewComponent pageViewComponent) {
        super(documentViewController, pageViewComponent);
    }

    @Override
    protected void checkAndApplyPreferences() {

    }

    /**
     * Handles double and triple left mouse clicks to select a word or line of text respectively.
     *
     * @param clickCount        number of mouse clicks to interpret for line or word selection.
     * @param clickPoint        point that mouse was clicked.
     * @param pageViewComponent parent page view component
     */
    public void wordLineSelection(int clickCount, Point clickPoint, AbstractPageViewComponent pageViewComponent) {
        try {
            // triple click selects the whole line.
            if (clickCount == 3) {
                lineSelectHandler(pageViewComponent.getPage(), (Point) clickPoint.clone());
            }
            // double click selects the word that was clicked.
            else if (clickCount == 2) {
                wordSelectHandler(pageViewComponent.getPage(), (Point) clickPoint.clone());
            }
            if (pageViewComponent != null) {
                pageViewComponent.requestFocus();
            }
        } catch (InterruptedException e) {
            logger.fine("Text selection page access interrupted");
        }
    }

    /**
     * Selection started, records the anchor caret at the start point.
     *
     * @param startPoint        starting selection position.
     * @param isFirst           start of selection if true
     * @param pageViewComponent parent page component
     */
    public void selectionStart(Point startPoint, AbstractPageViewComponent pageViewComponent, boolean isFirst) {
        try {
            Page currentPage = pageViewComponent.getPage();
            if (currentPage != null) {
                PageText pageText = currentPage.getViewText();
                int offset = caretOffset(pageText, startPoint);
                pageLock = currentPage;
                documentViewController.getDocumentViewModel()
                        .collapseTo(pageViewComponent.getPageIndex(), offset);
                selectedCount = 0;
                syncSelection();
            }
            pageViewComponent.repaint();
        } catch (InterruptedException e) {
            logger.fine("Text selection page access interrupted");
        }
    }

    /**
     * Selection ended, fires the selection property change if any text was selected.
     *
     * @param pageViewComponent page component view
     * @param endPoint          end point of drag
     */
    public void selectionEnd(Point endPoint, AbstractPageViewComponent pageViewComponent) {
        if (selectedCount > 0) {
            documentViewController.getDocumentViewModel().addSelectedPageText(pageViewComponent);
            documentViewController.firePropertyChange(PropertyConstants.TEXT_SELECTED, null, null);
        }
        clearRectangle(pageViewComponent);
        pageViewComponent.repaint();
    }

    public void clearSelection() {
        // release the page lock so the Reference API can collect the page post selection.
        pageLock = null;
        selectedCount = 0;
    }

    public void clearSelectionState() {
        for (AbstractPageViewComponent page : documentViewController.getDocumentViewModel().getPageComponents()) {
            ((PageViewComponentImpl) page).getTextSelectionPageHandler().clearSelection();
        }
    }

    /**
     * Selection drag, extends the focus caret to the drag point on the given page.
     *
     * @param dragPoint         drag location in the page component's coordinates.
     * @param pageViewComponent page being dragged over.
     * @param isDown            unused, retained for call-site compatibility.
     * @param isMovingRight     unused, retained for call-site compatibility.
     */
    public void selection(Point dragPoint, AbstractPageViewComponent pageViewComponent,
                          boolean isDown, boolean isMovingRight) {
        try {
            if (pageViewComponent != null) {
                Page currentPage = pageViewComponent.getPage();
                if (currentPage != null) {
                    pageLock = currentPage;
                    PageText pageText = currentPage.getViewText();
                    int offset = caretOffset(pageText, dragPoint);
                    DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
                    if (documentViewModel.getTextSelection().isEmpty()) {
                        documentViewModel.collapseTo(pageViewComponent.getPageIndex(), offset);
                    } else {
                        documentViewModel.extendTo(pageViewComponent.getPageIndex(), offset);
                    }
                    selectedCount = documentViewModel.getTextSelection().isCollapsed() ? 0 : 1;
                    syncSelection();
                    lastMouseLocation = dragPoint;
                }
            }
        } catch (InterruptedException e) {
            logger.fine("Text selection page access interrupted");
        }
    }

    public boolean selectionTextSelectIcon(Point mouseLocation, AbstractPageViewComponent pageViewComponent) {
        boolean foundSelectableText = false;
        try {
            Page currentPage = pageViewComponent.getPage();
            if (currentPage != null) {
                PageText pageText = currentPage.getViewText();
                if (pageText != null) {
                    Point2D.Float pageMouseLocation = convertToPageSpace(mouseLocation);
                    foundSelectableText = pageText.getTextSequence().hitsText(pageMouseLocation);
                    documentViewController.setViewCursor(foundSelectableText
                            ? DocumentViewController.CURSOR_TEXT_SELECTION
                            : DocumentViewController.CURSOR_SELECT);
                }
            }
        } catch (InterruptedException e) {
            logger.fine("Text selection page access interrupted");
        }
        return foundSelectableText;
    }

    /**
     * Maps a point in the page component's coordinates to a caret character offset in the page's
     * reading-order text sequence.
     */
    protected int caretOffset(PageText pageText, Point componentPoint) {
        if (pageText == null) return 0;
        return pageText.getTextSequence().caretAt(convertToPageSpace(componentPoint)).getOffset();
    }

    /**
     * Projects the authoritative selection onto the legacy per-glyph flags and repaints; delegates
     * to {@link TextSelectionSupport#applyDocumentSelection} so the mouse and keyboard paths behave
     * identically.
     */
    protected void syncSelection() {
        TextSelectionSupport.applyDocumentSelection(documentViewController.getDocumentViewModel());
    }

    /**
     * Paints the page's selection (derived from the document selection offset model) and any search
     * highlight (still driven by the per-word highlight flags).
     *
     * @param g                 graphics context to paint to.
     * @param pageViewComponent page view component to paint selected text on.
     * @param documentViewModel document model contains view properties such as zoom and rotation.
     */
    public static void paintSelectedText(Graphics g,
                                         AbstractPageViewComponent pageViewComponent,
                                         DocumentViewModel documentViewModel) throws InterruptedException {
        Graphics2D gg = (Graphics2D) g;
        AffineTransform prePaintTransform = gg.getTransform();
        Color oldColor = gg.getColor();
        Stroke oldStroke = gg.getStroke();
        gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Page.SELECTION_ALPHA));
        gg.setStroke(new BasicStroke(1.0f));

        Page currentPage = pageViewComponent.getPage();
        PageText pageText = TextSelectionSupport.loadedPageText(pageViewComponent);
        if (currentPage != null && pageText != null) {
            AffineTransform pageTransform = currentPage.getPageTransform(
                    documentViewModel.getPageBoundary(),
                    documentViewModel.getViewRotation(),
                    documentViewModel.getViewZoom());
            TextSequence sequence = pageText.getTextSequence();

            // selection fill, derived from the document-level offset model (or full page for select-all).
            OffsetRange range = documentViewModel.isSelectAll() ? sequence.fullRange()
                    : TextSelectionSupport.rangeForPage(documentViewModel.getTextSelection(),
                    pageViewComponent.getPageIndex(), sequence);
            if (range != null) {
                gg.setColor(Page.selectionColor);
                for (Rectangle2D.Double rect : sequence.rectsFor(range)) {
                    GeneralPath path = new GeneralPath(rect);
                    path.transform(pageTransform);
                    gg.fill(path);
                }
            }

            // search highlight fill, still driven by the per-word highlight flags.
            for (LineText lineText : pageText.getPageLines()) {
                for (WordText wordText : lineText.getWords()) {
                    if (wordText.isHighlighted()) {
                        gg.setColor(wordText.isHighlightCursor()
                                ? Page.highlightCursorColor : wordText.getHighlightColor());
                        GeneralPath path = new GeneralPath(wordText.getBounds());
                        path.transform(pageTransform);
                        gg.fill(path);
                    }
                }
            }
        }
        gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        gg.setTransform(prePaintTransform);
        gg.setStroke(oldStroke);
        gg.setColor(oldColor);
    }

    /**
     * Utility for painting text bounds (debug).
     *
     * @param g graphics context to paint to.
     * @throws InterruptedException thread interrupted.
     */
    protected void paintTextBounds(Graphics g) throws InterruptedException {
        Page currentPage = pageViewComponent.getPage();
        AffineTransform pageTransform = getPageTransform();
        Graphics2D gg = (Graphics2D) g;
        Color oldColor = g.getColor();
        g.setColor(Color.red);

        PageText pageText = currentPage.getViewText();
        if (pageText != null) {
            ArrayList<LineText> pageLines = pageText.getPageLines();
            if (pageLines != null) {
                for (LineText lineText : pageLines) {
                    for (WordText wordText : lineText.getWords()) {
                        for (org.icepdf.core.pobjects.graphics.text.GlyphText glyph : wordText.getGlyphs()) {
                            g.setColor(Color.black);
                            GeneralPath glyphSpritePath = new GeneralPath(glyph.getBounds());
                            glyphSpritePath.transform(pageTransform);
                            gg.draw(glyphSpritePath);
                        }
                    }
                    g.setColor(Color.red);
                    GeneralPath glyphSpritePath = new GeneralPath(lineText.getBounds());
                    glyphSpritePath.transform(pageTransform);
                    gg.draw(glyphSpritePath);
                }
            }
        }
        g.setColor(oldColor);
    }

    /**
     * Selects the word under the mouse (double-click).
     *
     * @param currentPage   page to look for text on.
     * @param mouseLocation location of mouse in the page component's coordinates.
     * @throws InterruptedException thread interrupted.
     */
    protected void wordSelectHandler(Page currentPage, Point mouseLocation) throws InterruptedException {
        selectRangeAtPoint(currentPage, mouseLocation, true);
    }

    /**
     * Selects the line under the mouse (triple-click).
     *
     * @param currentPage   page to select on.
     * @param mouseLocation location of mouse in the page component's coordinates.
     * @throws InterruptedException thread interrupted.
     */
    protected void lineSelectHandler(Page currentPage, Point mouseLocation) throws InterruptedException {
        selectRangeAtPoint(currentPage, mouseLocation, false);
    }

    private void selectRangeAtPoint(Page currentPage, Point mouseLocation, boolean word) throws InterruptedException {
        if (currentPage == null) return;
        PageText pageText = currentPage.getViewText();
        if (pageText == null) return;
        TextSequence sequence = pageText.getTextSequence();
        int offset = caretOffset(pageText, mouseLocation);
        OffsetRange range = word ? sequence.wordRange(offset) : sequence.lineRange(offset);
        if (range.isEmpty()) return;
        int pageIndex = pageViewComponent.getPageIndex();
        pageLock = currentPage;
        documentViewController.getDocumentViewModel()
                .setTextSelection(pageIndex, range.getStart(), pageIndex, range.getEnd());
        selectedCount = 1;
        syncSelection();
        documentViewController.firePropertyChange(PropertyConstants.TEXT_SELECTED, null, null);
    }

    // ------------------------------------------------------------------
    // Keyboard caret navigation.  All operate on the document-level focus caret; the focus page
    // may differ from this handler's own page.  extend == true moves the focus only (shift-select).
    // ------------------------------------------------------------------

    public void caretRight(boolean extend) {
        horizontalCaret(true, extend);
    }

    public void caretLeft(boolean extend) {
        horizontalCaret(false, extend);
    }

    private void horizontalCaret(boolean forward, boolean extend) {
        DocumentViewModel model = documentViewController.getDocumentViewModel();
        DocumentTextSelection selection = model.getTextSelection();
        if (selection.isEmpty()) return;
        try {
            int page = selection.getFocusPage(), offset = selection.getFocusOffset();
            TextSequence seq = sequenceAt(page);
            if (seq == null) return;
            int newPage = page, newOffset;
            if (forward) {
                if (offset < seq.length()) newOffset = seq.nextBoundary(offset, BreakType.GLYPH, true);
                else if (page < lastPageIndex()) {
                    newPage = page + 1;
                    newOffset = 0;
                } else newOffset = offset;
            } else {
                if (offset > 0) newOffset = seq.nextBoundary(offset, BreakType.GLYPH, false);
                else if (page > 0) {
                    newPage = page - 1;
                    TextSequence prev = sequenceAt(newPage);
                    newOffset = prev != null ? prev.length() : 0;
                } else newOffset = 0;
            }
            applyCaret(newPage, newOffset, extend, false);
        } catch (InterruptedException e) {
            logger.fine("Caret navigation interrupted");
        }
    }

    public void caretWordRight(boolean extend) {
        wordCaret(true, extend);
    }

    public void caretWordLeft(boolean extend) {
        wordCaret(false, extend);
    }

    private void wordCaret(boolean forward, boolean extend) {
        DocumentViewModel model = documentViewController.getDocumentViewModel();
        DocumentTextSelection selection = model.getTextSelection();
        if (selection.isEmpty()) return;
        try {
            TextSequence seq = sequenceAt(selection.getFocusPage());
            if (seq == null) return;
            int offset = selection.getFocusOffset();
            int nb = seq.nextBoundary(offset, BreakType.WORD, forward);
            if (nb == offset) {
                // at a page edge, roll over one glyph to the neighbouring page.
                horizontalCaret(forward, extend);
                return;
            }
            applyCaret(selection.getFocusPage(), nb, extend, false);
        } catch (InterruptedException e) {
            logger.fine("Caret navigation interrupted");
        }
    }

    public void caretLineStart(boolean extend) {
        lineEdgeCaret(false, extend);
    }

    public void caretLineEnd(boolean extend) {
        lineEdgeCaret(true, extend);
    }

    private void lineEdgeCaret(boolean end, boolean extend) {
        DocumentViewModel model = documentViewController.getDocumentViewModel();
        DocumentTextSelection selection = model.getTextSelection();
        if (selection.isEmpty()) return;
        try {
            TextSequence seq = sequenceAt(selection.getFocusPage());
            if (seq == null) return;
            OffsetRange line = seq.lineRange(selection.getFocusOffset());
            applyCaret(selection.getFocusPage(), end ? line.getEnd() : line.getStart(), extend, false);
        } catch (InterruptedException e) {
            logger.fine("Caret navigation interrupted");
        }
    }

    public void caretDown(boolean extend) {
        verticalCaret(true, extend);
    }

    public void caretUp(boolean extend) {
        verticalCaret(false, extend);
    }

    private void verticalCaret(boolean down, boolean extend) {
        DocumentViewModel model = documentViewController.getDocumentViewModel();
        DocumentTextSelection selection = model.getTextSelection();
        if (selection.isEmpty()) return;
        try {
            int page = selection.getFocusPage(), offset = selection.getFocusOffset();
            TextSequence seq = sequenceAt(page);
            if (seq == null) return;
            if (goalX < 0) goalX = seq.caretRect(new Caret(offset, Bias.FORWARD)).getX();
            Caret adjacent = down
                    ? seq.caretBelow(new Caret(offset, Bias.FORWARD), goalX)
                    : seq.caretAbove(new Caret(offset, Bias.FORWARD), goalX);
            if (adjacent != null) {
                applyCaret(page, adjacent.getOffset(), extend, true);
            } else if (down && page < lastPageIndex()) {
                TextSequence next = sequenceAt(page + 1);
                if (next != null) applyCaret(page + 1, next.caretAtLine(0, goalX).getOffset(), extend, true);
            } else if (!down && page > 0) {
                TextSequence prev = sequenceAt(page - 1);
                if (prev != null) applyCaret(page - 1, prev.caretAtLine(prev.lineCount() - 1, goalX).getOffset(), extend, true);
            }
        } catch (InterruptedException e) {
            logger.fine("Caret navigation interrupted");
        }
    }

    private void applyCaret(int newPage, int newOffset, boolean extend, boolean verticalMotion) {
        if (!verticalMotion) goalX = -1;
        DocumentViewModel model = documentViewController.getDocumentViewModel();
        if (extend) model.extendTo(newPage, newOffset);
        else model.collapseTo(newPage, newOffset);
        TextSelectionSupport.applyDocumentSelection(model);
        focusAndScrollCaret(newPage, newOffset);
    }

    private TextSequence sequenceAt(int pageIndex) throws InterruptedException {
        java.util.List<AbstractPageViewComponent> pages = documentViewController.getDocumentViewModel().getPageComponents();
        if (pageIndex < 0 || pageIndex >= pages.size()) return null;
        Page page = pages.get(pageIndex).getPage();
        if (page == null) return null;
        PageText pageText = page.getViewText();
        return pageText != null ? pageText.getTextSequence() : null;
    }

    private int lastPageIndex() {
        return documentViewController.getDocumentViewModel().getPageComponents().size() - 1;
    }

    private void focusAndScrollCaret(int pageIndex, int offset) {
        DocumentViewModel model = documentViewController.getDocumentViewModel();
        java.util.List<AbstractPageViewComponent> pages = model.getPageComponents();
        if (pageIndex < 0 || pageIndex >= pages.size()) return;
        AbstractPageViewComponent pageComponent = pages.get(pageIndex);
        pageComponent.requestFocusInWindow();
        try {
            Page page = pageComponent.getPage();
            if (page == null) return;
            PageText pageText = page.getViewText();
            if (pageText == null) return;
            AffineTransform transform = page.getPageTransform(
                    model.getPageBoundary(), model.getViewRotation(), model.getViewZoom());
            Rectangle2D.Double caret = pageText.getTextSequence().caretRect(new Caret(offset, Bias.FORWARD));
            Rectangle bounds = transform.createTransformedShape(caret).getBounds();
            bounds.grow(8, 24);
            pageComponent.scrollRectToVisible(bounds);
        } catch (InterruptedException e) {
            logger.fine("Caret scroll interrupted");
        }
    }

    @Override
    public void setSelectionRectangle(Point cursorLocation, Rectangle selection) {
    }

    public static GeneralPath convertTextShapesToBounds(ArrayList<Shape> textShapes) {
        if (textShapes != null && !textShapes.isEmpty()) {

            // bound of the selected text
            Rectangle2D shapeBounds;
            double padding;
            // padding out the bound, so we get a better hits when looking for redacted text. Since the redaction
            // box is derived from the glyph bounds we can get rounding errors when a contains call is made and
            // a glyph will be just slightly outside the redaction bounds and contains will return false
            Area area = new Area();
            for (Shape bounds : textShapes) {
                shapeBounds = bounds.getBounds2D();
                padding = shapeBounds.getHeight() * 0.025;
                shapeBounds.setRect(
                        shapeBounds.getX() - padding,
                        shapeBounds.getY() - padding,
                        shapeBounds.getWidth() + (padding * 2),
                        shapeBounds.getHeight() + (padding * 2));
                // area is important here as we want a union of the shapes, not multiple separate paths.
                area.add(new Area(shapeBounds));
            }
            GeneralPath textPath = new GeneralPath();
            textPath.append(area, false);


            return textPath;
        }
        return null;
    }

    /**
     * Convert the shapes that make up the annotation to page space so that
     * they will scale correctly at different zooms.
     *
     * @param bounds bounds to convert to page space
     * @param path   path
     * @return transformed bBox.
     */
    protected Rectangle convertToPageSpace(ArrayList<Shape> bounds,
                                           GeneralPath path) {
        Page currentPage = pageViewComponent.getPage();
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        AffineTransform at = currentPage.getToPageSpaceTransform(
                documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(),
                documentViewModel.getViewZoom());
        // convert the two points as well as the bbox.
        Rectangle tBbox = at.createTransformedShape(path).getBounds();
        // convert the points
        Shape bound;
        for (int i = 0; i < bounds.size(); i++) {
            bound = bounds.get(i);
            bound = at.createTransformedShape(bound);
            bounds.set(i, bound);
        }

        path.transform(at);

        return tBbox;
    }
}
