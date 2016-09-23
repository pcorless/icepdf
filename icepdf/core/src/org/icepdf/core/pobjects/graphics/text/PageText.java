/*
 * Copyright 2006-2016 ICEsoft Technologies Inc.
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
import java.util.*;

/**
 * Page text represents the root element of a page's text hierarchy which
 * looks something like this.
 * <ul>
 * PageText -&gt; LineText* -&gt; WordText* -&gt; GlyphText*
 * </ul>
 * The hierarchy elements are build by the content parser when text extraction
 * is enabled.  It is build to seperate the huristics used to calculate
 * word and line detection which is used for text extraction/search,
 * search highlighting and text highlighting.
 * <p/>
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

    private static boolean checkForDuplicates;
    private static boolean preserveColumns;

    static {
        checkForDuplicates = Defs.booleanProperty(
                "org.icepdf.core.views.page.text.trim.duplicates", false);

        preserveColumns = Defs.booleanProperty(
                "org.icepdf.core.views.page.text.preserveColumns", true);
    }

    // pointer to current line during document parse, no other use.
    private LineText currentLine;

    private ArrayList<LineText> pageLines;
    private ArrayList<LineText> sortedPageLines;

    private AffineTransform previousTextTransform;

    private LinkedHashMap<OptionalContents, PageText> optionalPageLines;

    public PageText() {
        pageLines = new ArrayList<LineText>(64);
    }

    public void newLine(LinkedList<OptionalContents> oCGs) {
        if (oCGs != null && oCGs.size() > 0) {
            if (optionalPageLines == null) {
                optionalPageLines = new LinkedHashMap<OptionalContents, PageText>(10);
            }
            OptionalContents optionalContent = oCGs.peek();
            PageText pageText = optionalPageLines.get(optionalContent);
            if (pageText == null) {
                // create a text object add the glyph.
                pageText = new PageText();
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
        currentLine = new LineText();
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
     * {@see sortAndFormatText}.
     * <p/>
     * During the extraction process extra space will automatically be added
     * between words.  However depending on how the PDF is encoded can result
     * in too many extra spaces.  So as a result this feature can be turned off
     * with the system property org.icepdf.core.views.page.text.autoSpace which
     * is set to True by default.
     *
     * @return list of page lines that are in the main content stream and any
     *         visible layers.
     */
    public ArrayList<LineText> getPageLines() {
        if (sortedPageLines == null) {
            sortAndFormatText();
        }
        return sortedPageLines;
    }

    /**
     * Gets all visible lines, checking the page text for any text that is
     * in an optional content group and that that group is flagged as visible.
     *
     * @return list of all visible lineText.
     */
    private ArrayList<LineText> getVisiblePageLines() {
        ArrayList<LineText> visiblePageLines = new ArrayList<LineText>(pageLines);
        // add optional content text that is visible.
        // check optional content.
        if (optionalPageLines != null) {
            // iterate over optional content keys and extract text from visible groups
            Set<OptionalContents> keys = optionalPageLines.keySet();
            LineText currentLine = new LineText();
            visiblePageLines.add(currentLine);
            for (OptionalContents key : keys) {
                if (key != null && key.isVisible()) {
                    ArrayList<LineText> pageLines = optionalPageLines.get(key).getVisiblePageLines();
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

    private ArrayList<LineText> getAllPageLines() {
        ArrayList<LineText> visiblePageLines = new ArrayList<LineText>(pageLines);
        // add optional content text that is visible.
        // check optional content.
        if (optionalPageLines != null) {
            // iterate over optional content keys and extract text from visible groups
            Set<OptionalContents> keys = optionalPageLines.keySet();
            LineText currentLine = new LineText();
            visiblePageLines.add(currentLine);
            for (OptionalContents key : keys) {
                if (key != null) {
                    ArrayList<LineText> pageLines = optionalPageLines.get(key).getVisiblePageLines();
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

    public void setTextTransform(AffineTransform affineTransform){
        // look to see if we have shear and thus text that has been rotated, if so we insert a page break
        if (previousTextTransform != null && currentLine != null){
            // hard round as we're just looking for a 90 degree shift in writing direction.
            // if found we clear the current work so we can start a new word.
            if ((previousTextTransform.getShearX() < 0 && (int)affineTransform.getShearX() > 0) ||
                    (previousTextTransform.getShearX() > 0 && (int)affineTransform.getShearX() < 0) ||
                    (previousTextTransform.getShearY() < 0 && (int)affineTransform.getShearY() > 0) ||
                    (previousTextTransform.getShearY() > 0 && (int)affineTransform.getShearY() < 0)) {
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
            optionalPageLines = new LinkedHashMap<OptionalContents, PageText>(10);
        }
        PageText pageText = optionalPageLines.get(optionalContent);
        if (pageText == null) {
            // create a text object add the glyph.
            pageText = new PageText();
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
        if (pageLines != null) {
            for (LineText lineText : pageLines) {
                lineText.clearSelected();
            }
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
                    if (optionalLines != null) {
                        for (LineText lineText : optionalLines) {
                            lineText.clearSelected();
                        }
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

    public StringBuilder getSelected() {
        StringBuilder selectedText = new StringBuilder();
        ArrayList<LineText> pageLines = getPageLines();
        if (pageLines != null) {
            for (LineText lineText : pageLines) {
                selectedText.append(lineText.getSelected());
            }
        }
        return selectedText;
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
        for (LineText lineText : pageLines) {

            for (WordText wordText : lineText.getWords()) {
                extractedText.append(wordText.getText());
            }
            extractedText.append('\n');
        }
        return extractedText.toString();
    }

    /**
     * Takes the raw page lines represented as one continuous line and sorts the
     * text by the y access of the word bounds.  The words are then sliced into
     * separate lines base on y changes.  And finally each newly sorted line is
     * sorted once more by each words x coordinate.
     */
    public void sortAndFormatText() {
        ArrayList<LineText> visiblePageLines = getVisiblePageLines();
        if (visiblePageLines != null && visiblePageLines.size() > 0 &&
                visiblePageLines.get(0) != null) {
            // create new array for storing the sorted lines
            ArrayList<LineText> sortedPageLines = new ArrayList<LineText>(64);
            // move over all
            for (LineText pageLine : visiblePageLines) {
                // all page words will be on one line
                java.util.List<WordText> words = pageLine.getWords();
                if (words != null && words.size() > 0) {
//                    if (!preserveColumns) {
//                        Collections.sort(words, new LinePositionComparator());
//                    }
                    // break the words into lines on every change of y
                    double lastY = Math.round(words.get(0).getTextExtractionBounds().y);
                    int start = 0, end = 0;
                    double currentY = 0, diff;
                    for (WordText word : words) {
                        currentY = Math.round(word.getTextExtractionBounds().getY());
                        // little bit of tolerance for detecting a line,  basically anything that is
                        // >  then half the current word height / 2 will be marked as a break.
                        // this works well enough sub and super script and inconsistencies
                        // on table base text.
                        diff = Math.abs(currentY - lastY);
                        if (diff != 0 && diff > word.getTextExtractionBounds().getHeight() / 2) {
                            LineText lineText = new LineText();
                            lineText.addAll(words.subList(start, end));
                            sortedPageLines.add(lineText);
                            start = end;
                        }
                        end++;
                        lastY = currentY;
                    }
                    if (start < end) {
                        LineText lineText = new LineText();
                        lineText.addAll(words.subList(start, end));
                        sortedPageLines.add(lineText);
                    }
                }
            }

            // do a rough check for duplicate strings that are sometimes generated
            // by Chrystal Reports.  Enable with
            // -Dorg.icepdf.core.views.page.text.trim.duplicates=true
            if (checkForDuplicates) {
                for (int k = 0, maxLines = sortedPageLines.size(); k < maxLines; k++) {
                    final LineText lineText = sortedPageLines.get(k);
                    final List<WordText> words = lineText.getWords();
                    if (words.size() > 0) {
                        final List<WordText> trimmedWords = new ArrayList<WordText>();
                        final Set<String> refs = new HashSet<String>();
                        for (final WordText wordText : words) {
                            final String key = wordText.getText() + wordText.getBounds();
                            if (refs.add(key)) {
                                trimmedWords.add(wordText);
                            }
                        }
                        lineText.setWords(trimmedWords);
                    }

                }

            }


            // sort each line by x coordinate.
            if (sortedPageLines.size() > 0) {
                for (LineText lineText : sortedPageLines) {
                    Collections.sort(lineText.getWords(),
                            new WordPositionComparator());
                }
            }

            // recalculate the line bounds.
            if (sortedPageLines.size() > 0) {
                for (LineText lineText : sortedPageLines) {
                    lineText.getBounds();
                }
            }

            // sort the lines
            if (sortedPageLines.size() > 0 && !preserveColumns) {
                Collections.sort(sortedPageLines,
                        new LinePositionComparator());
            }
            // assign back the sorted lines.
            this.sortedPageLines = sortedPageLines;
        }

    }


}
