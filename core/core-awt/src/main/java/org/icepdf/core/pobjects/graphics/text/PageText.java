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
package org.icepdf.core.pobjects.graphics.text;

import org.icepdf.core.pobjects.OptionalContents;
import org.icepdf.core.util.Defs;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.logging.Logger;

/**
 * Page text represents the root element of a page's text hierarchy which
 * looks something like this.
 * <ul>
 * <li>PageText -&gt; LineText* -&gt; WordText* -&gt; GlyphText*</li>
 * </ul>
 * The hierarchy elements are build by the content parser when text extraction
 * is enabled.  It is build to seperate the huristics used to calculate
 * word and line detection which is used for text extraction/search,
 * search highlighting and text highlighting.
 * <br>
 * It very important to note that all coordinates system represented in this
 * hierarchy of object has been normalized to the page space.  This allows for
 * object to be sorted and drawn. Also this structure is not used for page
 * layout and painting.  It is is used for painting text selectin via UI input
 * or search.  The seperation is needed so that the text represented in Page
 * text can be padded and sorted to aid in text extraction readability.
 *
 * @since 4.0
 */
public class PageText implements TextSelect {

    private static final Logger logger = Logger.getLogger(PageText.class.getName());

    private static final boolean checkForDuplicates;

    /** Merge sliced lines that share a baseline back into a single line (see {@link #mergeLinesByBaseline}). */
    private static final boolean mergeBaselines;

    /** Collapse letter-spaced runs (e.g. "S P E C I F I C A T I O N S") into words (see {@link #collapseLetterSpacing}). */
    private static final boolean collapseLetterSpacing;

    // Two lines are merged into one only when their cross-axis extents overlap by at least this fraction of the
    // smaller extent - enough to catch a fragment split off the same baseline, but not two stacked rows.
    private static final double LINE_MERGE_OVERLAP = 0.5;

    // A letter-spaced run must have at least this many short tokens before it is collapsed.
    private static final int LETTER_SPACING_MIN_RUN = 4;
    // Tokens longer than this end a letter-spaced run - they are ordinary words, not spaced-out letters.
    private static final int LETTER_SPACING_MAX_TOKEN = 4;
    // If the largest inter-token gap is no more than this multiple of the median gap the run is treated as a single
    // word (uniform spacing, no word breaks) - this keeps "SPECIFICATIONS" from splitting on its even letter gaps.
    private static final double LETTER_SPACING_UNIFORM_FACTOR = 2.0;

    /** Page reading-order strategy applied to the sorted line list. */
    private enum ReadingOrder {PLOT, YSORT, XYCUT}

    /**
     * The reading order used when nothing is configured.  Kept as a single constant so the planned
     * plot&rarr;xycut default flip (after corpus validation) is a one-line change.
     */
    private static final ReadingOrder DEFAULT_READING_ORDER = ReadingOrder.PLOT;

    private static final ReadingOrder readingOrder;

    static {
        checkForDuplicates = Defs.booleanProperty(
                "org.icepdf.core.views.page.text.trim.duplicates", false);

        mergeBaselines = Defs.booleanProperty(
                "org.icepdf.core.views.page.text.mergeBaselines", true);

        collapseLetterSpacing = Defs.booleanProperty(
                "org.icepdf.core.views.page.text.collapseLetterSpacing", true);

        readingOrder = resolveReadingOrder();
    }

    /**
     * Resolves the page reading order.  {@code org.icepdf.core.views.page.text.readingOrder} is the
     * canonical setting ({@code plot} | {@code ysort} | {@code xycut}); an unset or unrecognised
     * value falls back to {@link #DEFAULT_READING_ORDER}.
     * <p>
     * The older boolean {@code org.icepdf.core.views.page.text.preserveColumns} is <b>deprecated</b>
     * and honoured only as an alias when {@code readingOrder} is not set: {@code true} keeps the
     * default order, {@code false} selects {@code ysort}.  Prefer {@code readingOrder} alone.
     */
    private static ReadingOrder resolveReadingOrder() {
        String mode = Defs.sysProperty("org.icepdf.core.views.page.text.readingOrder");
        if (mode != null) {
            switch (mode.trim().toLowerCase()) {
                case "ysort":
                    return ReadingOrder.YSORT;
                case "xycut":
                    return ReadingOrder.XYCUT;
                case "plot":
                    return ReadingOrder.PLOT;
                default:
                    // an unrecognised value is almost always a typo (e.g. "ycut"); falling back
                    // silently makes it look like the flag had no effect at all.
                    logger.warning("Unknown reading-order mode '" + mode + "' for "
                            + "org.icepdf.core.views.page.text.readingOrder; expected one of "
                            + "plot, ysort, xycut.  Falling back to the default.");
                    return DEFAULT_READING_ORDER;
            }
        }
        // deprecated preserveColumns alias, consulted only when readingOrder is unset.
        if (Defs.sysProperty("org.icepdf.core.views.page.text.preserveColumns") != null) {
            logger.warning("org.icepdf.core.views.page.text.preserveColumns is deprecated; "
                    + "use org.icepdf.core.views.page.text.readingOrder=plot|ysort|xycut instead.");
            boolean preserveColumns = Defs.booleanProperty(
                    "org.icepdf.core.views.page.text.preserveColumns", true);
            return preserveColumns ? DEFAULT_READING_ORDER : ReadingOrder.YSORT;
        }
        return DEFAULT_READING_ORDER;
    }

    // pointer to current line during document parse, no other use.
    private LineText currentLine;
    private float pageRotation;

    private final ArrayList<LineText> pageLines;
    private ArrayList<LineText> sortedPageLines;

    // reading-order view over sortedPageLines; lazily built, invalidated on re-sort.
    private TextSequence textSequence;

    private AffineTransform previousTextTransform;
    private AffineTransform previousXObjectTransform;

    private LinkedHashMap<OptionalContents, PageText> optionalPageLines;

    public PageText() {
        this(0f);
    }

    public PageText(float pageRotation) {
        pageLines = new ArrayList<>(64);
        this.pageRotation = pageRotation;
    }

    public void setPageRotation(float pageRotation) {
        this.pageRotation = pageRotation;
    }

    public void newLine(LinkedList<OptionalContents> oCGs) {
        if (oCGs != null && oCGs.size() > 0) {
            if (optionalPageLines == null) {
                optionalPageLines = new LinkedHashMap<>(10);
            }
            OptionalContents optionalContent = oCGs.peek();
            PageText pageText = optionalPageLines.get(optionalContent);
            if (pageText == null) {
                // create a text object add the glyph.
                pageText = new PageText(pageRotation);
                pageText.newLine();
                optionalPageLines.put(optionalContent, pageText);
            } else {
                pageText.newLine();
            }
        }
    }

    public void newLine() {
        // make sure we don't insert a new line if the previous has no words.
        if (currentLine != null &&
                currentLine.getWords().size() == 0) {
            return;
        }
        currentLine = new LineText(pageRotation);
        pageLines.add(currentLine);
    }

    protected void addGlyph(GlyphText sprite) {
        if (currentLine == null) {
            newLine();
        }
        currentLine.addText(sprite);
    }

    /**
     * Creates a copy of the pageLines array and sorts that text both
     * vertically and horizontally to aid in the proper ordering during text
     * extraction.  The value is cached so any changes to optional content
     * visibility should require that the cache is refreshed with a call to
     * {@link #sortAndFormatText}.
     * <br>
     * During the extraction process extra space will automatically be added
     * between words.  However, depending on how the PDF is encoded can result
     * in too many extra spaces.  So as a result this feature can be turned off
     * with the system property org.icepdf.core.views.page.text.autoSpace which
     * is set to True by default.
     *
     * @return list of page lines that are in the main content stream and any
     * visible layers.
     */
    public ArrayList<LineText> getPageLines() {
        if (sortedPageLines == null) {
            sortAndFormatText();
        }
        return sortedPageLines;
    }

    /**
     * Gets the reading-order {@link TextSequence} for this page, a flattened view over the
     * sorted page lines that maps between page-space points, character offsets, and the
     * underlying glyph/word/line structure.  The value is cached and rebuilt whenever the
     * page re-sorts (see {@link #sortAndFormatText}).
     *
     * @return reading-order sequence for this page's visible text.
     */
    public TextSequence getTextSequence() {
        if (textSequence == null) {
            textSequence = new TextSequence(this);
        }
        return textSequence;
    }

    /**
     * Gets all visible lines, checking the page text for any text that is
     * in an optional content group and that that group is flagged as visible.
     *
     * @return list of all visible lineText.
     */
    private ArrayList<LineText> getVisiblePageLines(boolean skip) {
        ArrayList<LineText> visiblePageLines = skip ? new ArrayList<>() : new ArrayList<>(pageLines);
        // add optional content text that is visible.
        // check optional content.
        if (optionalPageLines != null) {
            // iterate over optional content keys and extract text from visible groups
            Set<OptionalContents> keys = optionalPageLines.keySet();
            for (OptionalContents key : keys) {
                if (key != null && key.isVisible()) {
                    ArrayList<LineText> pageLines = optionalPageLines.get(key).getVisiblePageLines(false);
                    LineText currentLine = new LineText(pageRotation);
                    visiblePageLines.add(currentLine);
                    for (LineText lineText : pageLines) {
                        currentLine.addAll(lineText.getWords());
                        // recalculate the bounds.
                        currentLine.getBounds();
                    }
                }
            }
        }
        return visiblePageLines;
    }

    private ArrayList<LineText> getAllPageLines() {
        ArrayList<LineText> visiblePageLines = new ArrayList<>(pageLines);
        // add optional content text that is visible.
        // check optional content.
        if (optionalPageLines != null) {
            // iterate over optional content keys and extract text from visible groups
            Set<OptionalContents> keys = optionalPageLines.keySet();
            LineText currentLine = new LineText(pageRotation);
            visiblePageLines.add(currentLine);
            for (OptionalContents key : keys) {
                if (key != null) {
                    ArrayList<LineText> pageLines = optionalPageLines.get(key).getVisiblePageLines(true);
                    for (LineText lineText : pageLines) {
                        currentLine.addAll(lineText.getWords());
                    }
                }
            }
            // recalculate the bounds.
            currentLine.getBounds();
        }
        return visiblePageLines;
    }

    /**
     * Adds the specified pageLines to the array of pageLines. Generally only
     * called when passing text form xObjects up to their parent shapes text.
     *
     * @param pageLines page lines to add.
     */
    public void addPageLines(ArrayList<LineText> pageLines) {
        if (pageLines != null) {
            this.pageLines.addAll(pageLines);
        }
    }

    public void setTextTransform(AffineTransform affineTransform) {
        // look to see if we have shear and thus text that has been rotated, if so we insert a page break
        if (previousTextTransform != null && currentLine != null) {
            // hard round as we're just looking for a 90 degree shift in writing direction.
            // if found we clear the current work so we can start a new word.
            if ((previousTextTransform.getShearX() < 0 && (int) affineTransform.getShearX() > 0) ||
                    (previousTextTransform.getShearX() > 0 && (int) affineTransform.getShearX() < 0) ||
                    (previousTextTransform.getShearY() < 0 && (int) affineTransform.getShearY() > 0) ||
                    (previousTextTransform.getShearY() > 0 && (int) affineTransform.getShearY() < 0)) {
                currentLine.clearCurrentWord();
            }
        }
        previousTextTransform = affineTransform;
    }

    public void addGlyph(GlyphText glyphText, LinkedList<OptionalContents> oCGs) {
        if (oCGs != null && oCGs.size() > 0) {
            if (oCGs.peek() != null) {
                addOptionalPageLines(oCGs.peek(), glyphText);
            }
        } else {
            addGlyph(glyphText);
        }
    }

    protected void addOptionalPageLines(OptionalContents optionalContent,
                                        GlyphText sprite) {
        if (optionalPageLines == null) {
            optionalPageLines = new LinkedHashMap<>(10);
        }
        PageText pageText = optionalPageLines.get(optionalContent);
        if (pageText == null) {
            // create a text object add the glyph.
            pageText = new PageText(pageRotation);
            pageText.addGlyph(sprite);
            optionalPageLines.put(optionalContent, pageText);
        } else {
            pageText.addGlyph(sprite);
        }
    }

    /**
     * Utility method to normalize text created in a Xform content stream
     * and is only called from the contentParser when parsing 'Do' token.
     *
     * @param transform do matrix transform
     */
    public void applyXObjectTransform(AffineTransform transform) {
        if (previousXObjectTransform != null) {
            // back out transform in the less common case a xObject is reused.
            try {
                AffineTransform inverse = previousXObjectTransform.createInverse();
                applyTextTransform(inverse);
            } catch (NoninvertibleTransformException e) {
                // intentionally left blank
            }
        }
        previousXObjectTransform = transform;
        applyTextTransform(transform);
    }

    /**
     * Utility to apply specified transform to all glyphs in the pageLine array
     */
    private void applyTextTransform(AffineTransform transform) {
        for (LineText lineText : pageLines) {
            lineText.clearBounds();
            for (WordText wordText : lineText.getWords()) {
                wordText.clearBounds();
                for (GlyphText glyph : wordText.getGlyphs()) {
                    glyph.normalizeToUserSpace(transform, null);
                }
            }
        }
    }

    public void clearSelected() {
        for (LineText lineText : pageLines) {
            lineText.clearSelected();
        }
        if (sortedPageLines != null) {
            for (LineText lineText : sortedPageLines) {
                lineText.clearSelected();
            }
        }
        // check optional content.
        if (optionalPageLines != null) {
            // iterate over optional content keys and extract text from visible groups
            Set<OptionalContents> keys = optionalPageLines.keySet();
            ArrayList<LineText> optionalLines;
            for (OptionalContents key : keys) {
                if (key != null) {
                    optionalLines = optionalPageLines.get(key).getAllPageLines();
                    for (LineText lineText : optionalLines) {
                        lineText.clearSelected();
                    }
                }
            }
        }
    }

    public void clearHighlighted() {
        for (LineText lineText : pageLines) {
            lineText.clearHighlighted();
        }
        for (LineText lineText : sortedPageLines) {
            lineText.clearHighlighted();
        }
        // check optional content.
        if (optionalPageLines != null) {
            // iterate over optional content keys and extract text from visible groups
            Set<OptionalContents> keys = optionalPageLines.keySet();
            ArrayList<LineText> optionalLines;
            for (OptionalContents key : keys) {
                if (key != null && key.isVisible()) {
                    optionalLines = optionalPageLines.get(key).getAllPageLines();
                    for (LineText lineText : optionalLines) {
                        lineText.clearHighlighted();
                    }
                }
            }
        }
    }

    public void clearHighlightedCursor() {
        for (LineText lineText : pageLines) {
            lineText.clearHighlightedCursor();
        }
        if (sortedPageLines != null) {
            for (LineText lineText : sortedPageLines) {
                lineText.clearHighlightedCursor();
            }
        }
        // check optional content.
        if (optionalPageLines != null) {
            // iterate over optional content keys and extract text from visible groups
            Set<OptionalContents> keys = optionalPageLines.keySet();
            ArrayList<LineText> optionalLines;
            for (OptionalContents key : keys) {
                if (key != null && key.isVisible()) {
                    optionalLines = optionalPageLines.get(key).getAllPageLines();
                    for (LineText lineText : optionalLines) {
                        lineText.clearHighlightedCursor();
                    }
                }
            }
        }
    }

    public StringBuilder getSelected() {
        StringBuilder selectedText = new StringBuilder();
        ArrayList<LineText> pageLines = getPageLines();
        if (pageLines != null) {
            StringBuilder selectedLineText;
            for (LineText lineText : pageLines) {
                selectedLineText = lineText.getSelected();
                if (selectedLineText != null && selectedLineText.length() > 0) {
                    selectedText.append(selectedLineText);
                    selectedText.append("\n");
                }
            }
        }
        return selectedText;
    }

    public ArrayList<WordText> getSelectedWordText() {
        ArrayList<WordText> selectedWordText = new ArrayList<>();
        for (LineText lineText : getPageLines()) {
            for (WordText word : lineText.getWords()) {
                if (word.isSelected()) {
                    selectedWordText.add(word);
                }
            }
        }
        return selectedWordText;
    }

    public void selectAll() {
        ArrayList<LineText> pageLines = getPageLines();
        if (pageLines != null) {
            for (LineText lineText : pageLines) {
                lineText.selectAll();
            }
        }
    }

    public void deselectAll() {
        for (LineText lineText : pageLines) {
            lineText.clearSelected();
        }
    }

    public String toString() {
        StringBuilder extractedText = new StringBuilder();
        final List<LineText> sortedLines = getPageLines();
        for (LineText lineText : sortedLines) {

            for (WordText wordText : lineText.getWords()) {
                extractedText.append(wordText.getText());
            }
            extractedText.append('\n');
        }
        return extractedText.toString();
    }

    /**
     * Utility to find the currently displayed word instance as there is a chance that a page's PageText has been
     * reinstantiated.
     *
     * @param word text to find.
     * @return current object of the same wordText value.
     */
    public WordText find(WordText word) {
        for (LineText lineText : pageLines) {
            for (WordText wordText : lineText.getWords()) {
                if (word.equals(wordText)) return wordText;
            }
        }
        if (optionalPageLines != null) {
            // iterate over optional content keys and extract text from visible groups
            Set<OptionalContents> keys = optionalPageLines.keySet();
            ArrayList<LineText> optionalLines;
            for (OptionalContents key : keys) {
                if (key != null && key.isVisible()) {
                    optionalLines = optionalPageLines.get(key).getAllPageLines();
                    for (LineText lineText : optionalLines) {
                        for (WordText wordText : lineText.getWords()) {
                            if (word.equals(wordText)) return wordText;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Sorts the given pageLines vertically (y coordinate) in page space. .
     *
     * @param pageLines page lines to sort, not directly sorted, new array is created for sorted data.
     * @return new array of sorted pages lines
     */
    private ArrayList<LineText> sortLinesVertically(ArrayList<LineText> pageLines) {
        ArrayList<LineText> sortedPageLines = new ArrayList<>(64);
        // move over all
        for (LineText pageLine : pageLines) {
            // all page words will be on one line
            java.util.List<WordText> words = pageLine.getWords();
            if (words != null && words.size() > 0) {
                // break the words into lines on every change of y
                double lastY = Math.round(words.get(0).getTextExtractionBounds().y);
                int start = 0, end = 0;
                double currentY, diff;
                for (WordText word : words) {
                    currentY = Math.round(word.getTextExtractionBounds().getY());
                    // little bit of tolerance for detecting a line,  basically anything that is
                    // >  then half the current word height / 2 will be marked as a break.
                    // this works well enough sub and super script and inconsistencies
                    // on table base text.
                    diff = Math.abs(currentY - lastY);
                    if (diff != 0 && diff > word.getTextExtractionBounds().getHeight() / 2) {
                        LineText lineText = new LineText(pageRotation);
                        lineText.addAll(words.subList(start, end));
                        sortedPageLines.add(lineText);
                        start = end;
                    }
                    end++;
                    lastY = currentY;
                }
                if (start < end) {
                    LineText lineText = new LineText(pageRotation);
                    lineText.addAll(words.subList(start, end));
                    sortedPageLines.add(lineText);
                }
            }
        }
        // The slicing above only ever splits a single input line - it can never merge words that were emitted as
        // separate lines (each T*/'/'" or per-glyph text object starts a fresh line in the parser).  Documents that
        // draw letter-spaced titles, or one text-showing operator per glyph, therefore end up with one glyph per
        // LineText and stay that way.  Merge lines that share a baseline back into a single line so downstream
        // x-sorting and word/space detection can reconstruct the visual line.
        if (mergeBaselines) {
            sortedPageLines = mergeLinesByBaseline(sortedPageLines);
        }
        return sortedPageLines;
    }

    /**
     * Merges <em>consecutive</em> lines that sit on the same baseline (top-y within half the line height) into a
     * single line.  Only adjacent lines in the incoming (content-stream/plot) order are considered, so the merge
     * collapses runs of per-glyph or letter-spaced lines - which the parser emits consecutively - without globally
     * re-sorting.  This preserves the plot reading order and, because two columns are not interleaved glyph-by-glyph
     * in the stream, avoids pulling column text onto a shared line.  Words keep their order within a band and are
     * re-sorted by x downstream in {@link #sortAndFormatText}.
     *
     * @param lines sliced lines to merge.
     * @return merged lines, one per run of same-baseline lines.
     */
    private ArrayList<LineText> mergeLinesByBaseline(ArrayList<LineText> lines) {
        if (lines.size() < 2) {
            return lines;
        }
        ArrayList<LineText> merged = new ArrayList<>(lines.size());
        LineText current = null;
        double bandLo = 0, bandHi = 0;
        for (LineText line : lines) {
            // Only vertical (rotated/stacked) lines are merged, and only when their x columns overlap substantially.
            // Horizontal lines are never merged: the parser can split a visual line into fragments, but a fragment on
            // the same baseline is not reliably distinguishable from the next row (tight leading makes their boxes
            // overlap), so fusing them would scramble reading order across the corpus.  Vertical runs are columns,
            // disjoint from the horizontal rows around them, so merging them by x overlap is safe.
            boolean vertical = WordText.detectVerticalText && line.isVerticalWriting();
            if (!vertical) {
                merged.add(line);
                current = null; // a horizontal line breaks any open vertical band
                continue;
            }
            Rectangle2D.Double bounds = line.getBounds();
            double lo = bounds.getX();
            double hi = lo + bounds.getWidth();
            double overlap = Math.min(hi, bandHi) - Math.max(lo, bandLo);
            double minExtent = Math.min(hi - lo, bandHi - bandLo);
            if (current != null && minExtent > 0 && overlap >= LINE_MERGE_OVERLAP * minExtent) {
                // same column as the previous vertical line - fold its words in, keeping the original band interval.
                current.addAll(line.getWords());
            } else {
                current = new LineText(pageRotation);
                current.addAll(line.getWords());
                merged.add(current);
                bandLo = lo;
                bandHi = hi;
            }
        }
        return merged;
    }

    /** True only for a word whose text is entirely whitespace (a real space word, not punctuation flagged whitespace). */
    private static boolean isBlank(WordText word) {
        return word.getText().trim().isEmpty();
    }

    /** True for a multi-character token made entirely of lowercase letters - a real embedded word (e.g. "and"). */
    private static boolean isLowercaseWord(String text) {
        if (text.length() < 2) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetter(c) || !Character.isLowerCase(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Allocation-free scan for whether a line contains at least one collapsible letter-spaced run, so the common
     * case (ordinary prose, no run) skips the rebuild entirely.  Mirrors the run-gathering in
     * {@link #collapseLetterSpacing} but only counts tokens.
     */
    private boolean hasLetterSpacedRun(List<WordText> words) {
        int n = words.size(), i = 0;
        while (i < n) {
            WordText w0 = words.get(i);
            if (w0.isWhiteSpace() || w0.getText().length() > LETTER_SPACING_MAX_TOKEN) {
                i++;
                continue;
            }
            int tokens = 0, singles = 0, j = i;
            boolean hasLowerWord = false;
            while (j < n) {
                WordText w = words.get(j);
                if (w.isWhiteSpace() || w.getText().length() > LETTER_SPACING_MAX_TOKEN) {
                    break;
                }
                tokens++;
                if (w.getText().trim().length() == 1) {
                    singles++;
                }
                if (isLowercaseWord(w.getText())) {
                    hasLowerWord = true;
                }
                j++;
                int q = j;
                while (q < n && isBlank(words.get(q))) {
                    q++;
                }
                if (q > j && q < n && !words.get(q).isWhiteSpace()
                        && words.get(q).getText().length() <= LETTER_SPACING_MAX_TOKEN) {
                    j = q;
                }
            }
            if (tokens >= LETTER_SPACING_MIN_RUN && singles * 4 >= tokens * 3 && !hasLowerWord) {
                return true;
            }
            i = Math.max(j, i + 1);
        }
        return false;
    }

    /**
     * Collapses letter-spaced runs within a line into whole words.  Headings are often drawn with a full space
     * between every letter ("S P E C I F I C A T I O N S", or "P E R F O R M A N C E  F I R S T"), whether the
     * spacing comes from real space glyphs in the content or from synthetic spaces inserted for a wide gap.  By any
     * geometric measure those inter-letter gaps look exactly like word spaces, so the only reliable signal is
     * structural: a run of mostly single-character short tokens.  Such a run is merged into one word, re-inserting a
     * space only where the inter-token gap is much larger than the run's typical gap (a genuine word boundary).
     *
     * @param line line whose words have already been placed in reading order.
     */
    private void collapseLetterSpacing(LineText line) {
        List<WordText> words = line.getWords();
        if (words.size() < LETTER_SPACING_MIN_RUN || !hasLetterSpacedRun(words)) {
            // fast path: most lines contain no letter-spaced run, so avoid allocating/rebuilding the word list.
            return;
        }
        boolean vertical = WordText.detectVerticalText && line.isVerticalWriting();
        List<WordText> result = new ArrayList<>(words.size());
        int i = 0, n = words.size();
        while (i < n) {
            if (words.get(i).isWhiteSpace()) {
                result.add(words.get(i));
                i++;
                continue;
            }
            // gather a maximal run of short tokens separated by at most one whitespace word
            List<WordText> tokens = new ArrayList<>();
            List<WordText> sepAfter = new ArrayList<>();
            int j = i, singles = 0;
            boolean hasLowerWord = false;
            while (j < n) {
                WordText w = words.get(j);
                if (w.isWhiteSpace()) {
                    break; // handled below as a separator lookahead
                }
                if (w.getText().length() > LETTER_SPACING_MAX_TOKEN) {
                    break;
                }
                tokens.add(w);
                if (w.getText().trim().length() == 1) {
                    singles++;
                }
                if (isLowercaseWord(w.getText())) {
                    hasLowerWord = true;
                }
                j++;
                // bridge a run of blank space words to the next token, but only if that token still qualifies - a
                // wide word gap can be several spaces, and the run must not be split there.  Only truly blank words
                // are bridged: punctuation (e.g. TOC leader dots) is flagged whitespace but carries real glyphs, so
                // it must terminate the run and pass through untouched rather than be swallowed as a separator.
                int q = j;
                while (q < n && isBlank(words.get(q))) {
                    q++;
                }
                if (q > j && q < n && !words.get(q).isWhiteSpace()
                        && words.get(q).getText().length() <= LETTER_SPACING_MAX_TOKEN) {
                    sepAfter.add(words.get(j));
                    j = q;
                } else {
                    sepAfter.add(null);
                }
            }
            // require most tokens to be single characters: real prose with a few short words ("in the U.S.")
            // must not be mistaken for a spaced-out heading.  A multi-character all-lowercase token (e.g. "and" in a
            // letter-spaced "L A T E X and pdfL A T E X" logo) is a real embedded word, not a spaced-out letter, so
            // it disqualifies the run - uppercase fragments like "OR"/"MANC" (from "PERFORMANCE") still collapse.
            boolean qualifies = tokens.size() >= LETTER_SPACING_MIN_RUN
                    && singles * 4 >= tokens.size() * 3
                    && !hasLowerWord;
            if (qualifies) {
                result.addAll(rebuildLetterSpacedRun(tokens, sepAfter, vertical));
                i = j;
            } else {
                result.add(words.get(i));
                i++;
            }
        }
        line.setWords(result);
    }

    /**
     * Rebuilds a detected letter-spaced run: concatenates the token glyphs into one word, breaking into a new word
     * (with the original separating space) wherever the inter-token gap exceeds the run's median gap by more than
     * {@link #LETTER_SPACING_BOUNDARY_FACTOR}.
     */
    private List<WordText> rebuildLetterSpacedRun(List<WordText> tokens, List<WordText> sepAfter, boolean vertical) {
        double[] gaps = new double[tokens.size() - 1];
        for (int k = 0; k < gaps.length; k++) {
            gaps[k] = tokenGap(tokens.get(k), tokens.get(k + 1), vertical);
        }
        double median = medianGap(gaps);
        double max = 0;
        for (double g : gaps) {
            max = Math.max(max, g);
        }
        // Uniform gaps mean a single spaced-out word (no breaks).  Otherwise a word break is a gap sitting above the
        // midpoint between the typical letter gap (median) and the widest gap (max) - large enough to exclude a minor
        // per-glyph anomaly but below a genuine inter-word gap.
        boolean uniform = max <= LETTER_SPACING_UNIFORM_FACTOR * median;
        double threshold = (median + max) / 2;
        List<WordText> rebuilt = new ArrayList<>();
        WordText current = new WordText(pageRotation);
        for (int k = 0; k < tokens.size(); k++) {
            for (GlyphText glyph : tokens.get(k).getGlyphs()) {
                current.addText(glyph);
            }
            if (k < gaps.length && !uniform && gaps[k] > threshold) {
                // real word boundary - close the current word and keep a separating space
                rebuilt.add(current);
                if (sepAfter.get(k) != null) {
                    rebuilt.add(sepAfter.get(k));
                }
                current = new WordText(pageRotation);
            }
        }
        rebuilt.add(current);
        return rebuilt;
    }

    /** Box-to-box gap between two consecutive tokens along the line's writing axis. */
    private double tokenGap(WordText prev, WordText next, boolean vertical) {
        Rectangle2D.Double a = prev.getTextExtractionBounds();
        Rectangle2D.Double b = next.getTextExtractionBounds();
        if (vertical) {
            double down = Math.abs(b.y - (a.y + a.height));
            double up = Math.abs(a.y - (b.y + b.height));
            return Math.min(down, up);
        }
        return Math.abs(b.x - (a.x + a.width));
    }

    private static double medianGap(double[] gaps) {
        if (gaps.length == 0) {
            return 0;
        }
        double[] sorted = gaps.clone();
        java.util.Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
    }

    /**
     * Insert optional content into the main LineText array, basically we are trying to consolidate all the
     * visible text in the document.
     *
     * @param sortedPageLines List of LineText to add visible optional content to.
     */
    private void insertOptionalLines(ArrayList<LineText> sortedPageLines) {
        ArrayList<LineText> optionalPageLines = getVisiblePageLines(true);
        for (LineText optionalPageLine : optionalPageLines) {
            double yOptional = optionalPageLine.getBounds().y;
            boolean found = false;
            for (LineText sortedPageLine : sortedPageLines) {
                Rectangle2D.Double sortedBounds = sortedPageLine.getBounds();
                double height = sortedBounds.height;
                double y = sortedBounds.y;
                double diff = Math.abs(yOptional - y);
                // corner case inclusion of a word and a space which is out of order from the
                // rest of the text in the document.
                if (diff < height) {
                    sortedPageLine.addAll(optionalPageLine.getWords());
                    found = true;
                    break;
                }
            }
            if (!found) {
                sortedPageLines.add(optionalPageLine);
            }
        }
    }

    /**
     * Takes the raw page lines represented as one continuous line and sorts the
     * text by the y access of the word bounds.  The words are then sliced into
     * separate lines base on y changes.  And finally each newly sorted line is
     * sorted once more by each words x coordinate.
     */
    public void sortAndFormatText() {
        ArrayList<LineText> visiblePageLines = new ArrayList<>(pageLines);
        // create new array for storing the sorted lines
        ArrayList<LineText> sortedPageLines = sortLinesVertically(visiblePageLines);
        // try and insert the option words on existing lines
        if (sortedPageLines.size() == 0) {
            sortedPageLines = getVisiblePageLines(true);
        } else {
            insertOptionalLines(sortedPageLines);
        }

        // sort again
        sortedPageLines = sortLinesVertically(sortedPageLines);

        // do a rough check for duplicate strings that are sometimes generated
        // by Chrystal Reports.  Enable with
        // -Dorg.icepdf.core.views.page.text.trim.duplicates=true
        if (checkForDuplicates) {
            for (final LineText lineText : sortedPageLines) {
                final List<WordText> words = lineText.getWords();
                if (words.size() > 0) {
                    final List<WordText> trimmedWords = new ArrayList<>();
                    final Set<String> refs = new HashSet<>();
                    for (final WordText wordText : words) {
                        // use regular rectangle so get a little rounding.
                        final String key = wordText.getText() + wordText.getBounds().getBounds();
                        if (refs.add(key)) {
                            trimmedWords.add(wordText);
                        }
                    }
                    lineText.setWords(trimmedWords);
                }
            }
        }

        // sort each line by x coordinate; vertical lines order along their writing direction instead.
        if (sortedPageLines.size() > 0) {
            for (LineText lineText : sortedPageLines) {
                if (mergeBaselines && WordText.detectVerticalText && lineText.isVerticalWriting()) {
                    lineText.getWords().sort(new WordPositionComparator(lineText.getWriteDirection()));
                } else {
                    lineText.getWords().sort(new WordPositionComparator());
                }
            }
        }

        // collapse letter-spaced runs ("S P E C I F I C A T I O N S" -> "SPECIFICATIONS") now that words are in
        // reading order along their line.
        if (collapseLetterSpacing && sortedPageLines.size() > 0) {
            for (LineText lineText : sortedPageLines) {
                collapseLetterSpacing(lineText);
            }
        }

        // recalculate the line bounds.
        if (sortedPageLines.size() > 0) {
            for (LineText lineText : sortedPageLines) {
                lineText.getBounds();
            }
        }

        // order the lines according to the configured reading-order strategy.  PLOT keeps the
        // content-stream order the slicing produced; YSORT is a global top-to-bottom sort; XYCUT
        // applies geometry-driven column/band ordering (see XYCutReadingOrder).
        if (sortedPageLines.size() > 0) {
            switch (readingOrder) {
                case YSORT:
                    LinePositionComparator.orderTopLeft(sortedPageLines);
                    break;
                case XYCUT:
                    sortedPageLines = XYCutReadingOrder.order(sortedPageLines);
                    break;
                case PLOT:
                default:
                    break;
            }
        }

        // Round out the word bounds
        for (LineText lineText : sortedPageLines) {
            List<WordText> words = lineText.getWords();
            if (words.size() > 0) {
                WordText wordTex;
                Rectangle2D.Double currentWord, nextWord;
                for (int i = 0, max = words.size() - 2; i < max; i++) {
                    nextWord = words.get(i + 1).getBounds();
                    currentWord = words.get(i).getBounds();
                    // use regular rectangle so get a little rounding.
                    double diff = nextWord.x - (currentWord.x + currentWord.width);
                    if (diff > 0) {
                        currentWord.setRect(currentWord.x, currentWord.y,
                                currentWord.width + diff, currentWord.height);
                    }
                }
            }
        }

        // assign back the sorted lines.
        this.sortedPageLines = sortedPageLines;

        // invalidate the reading-order view so it rebuilds from the new sort.
        this.textSequence = null;
    }


}
