/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.pobjects.graphics.text;

import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

/**
 * Line text is make up WordText objects.  This structure is to aid the
 * the identification of words for text extraction, searching and selecting.
 *
 * @since 4.0
 */
public class LineText extends AbstractText implements TextSelect {

    private WordText currentWord;

    private ArrayList<WordText> words;

    public LineText() {
        words = new ArrayList<WordText>(16);
    }

    public Rectangle2D.Float getBounds() {
        // lazy load the bounds as the calculation is very expensive
        if (bounds == null) {
            // word bounds build from child word bounds.
            for (WordText word : words) {
                if (bounds == null) {
                    bounds = word.getBounds();
                } else {
                    bounds.add(word.getBounds());
                }
            }
            // empty line text check, return empty bound.
            if (bounds == null){
                bounds = new Rectangle2D.Float();
            }
        }
        return bounds;
    }


    public GeneralPath getGeneralPath() {
        // general path is calculated after the pages has been parsed, again
        // expensive so we only want to do it when we have to.
        if (generalPath == null) {
            generalPath = new GeneralPath(getBounds());
        }
        return generalPath;
    }

    /**
     * Add the sprite to the current word in this line/sentence.  This method
     * also candles white space detection and word division.
     *
     * @param sprite sprite to add to line.
     */
    protected void addText(GlyphText sprite) {

        // look for white space characters and insert whitespace word
        if (WordText.detectWhiteSpace(sprite)) {
            // add as a new word, nothing special otherwise
            WordText newWord = new WordText();
            newWord.setWhiteSpace(true);
            newWord.addText(sprite);
            addWord(newWord);
            // ready new word
            currentWord = null;
        }
        //  add punctuation as new words
        else if (WordText.detectPunctuation(sprite)) {
            // add as a new word, nothing special otherwise
            WordText newWord = new WordText();
            newWord.setWhiteSpace(true);
            newWord.addText(sprite);
            addWord(newWord);
            // ready new word
            currentWord = null;
        }
        // detect if there should be any spaces between the new sprite
        // and the last sprite.
        else if (getCurrentWord().detectSpace(sprite)) {
            // build space word.
            WordText spaceWord = currentWord.buildSpaceWord(sprite);
            spaceWord.setWhiteSpace(true);
            // add space word,
            addWord(spaceWord);
            // ready a new word
            currentWord = null;
            // add the text again to register the glyph
            addText(sprite);
        }
        // business as usual
        else {
            getCurrentWord().addText(sprite);
        }
    }

    /**
     * Adds the specified word to the end of the line collection and makes
     * the new word the currentWord reference.
     *
     * @param wordText  word to add
     */
    private void addWord(WordText wordText) {

        // add the word, text or white space.
        this.words.add(wordText);

        // word test 
        currentWord = wordText;

    }

    /**
     * Gets the current word, if there is none, one is created.
     *
     * @return current word instance.
     */
    private WordText getCurrentWord() {
        if (currentWord == null) {
            currentWord = new WordText();
            words.add(currentWord);
        }
        return currentWord;
    }

    /**
     * Gets the words that make up this line.
     *
     * @return words in a line.
     */
    public ArrayList<WordText> getWords() {
        return words;
    }

    /**
     * Select all text in this line; all word and glyph children.
     */
    public void selectAll() {
        setSelected(true);
        setHasSelected(true);
        for (WordText word : words) {
            word.selectAll();
        }
    }

    /**
     * Deselects all text in this line; all word and glyph children.
     */
    public void clearSelected() {
        setSelected(false);
        setHasSelected(false);
        for (WordText word : words) {
            word.clearSelected();
        }
    }

    /**
     * Dehighlights all text in the line; all word and glyph children.
     */
    public void clearHighlighted() {
        setHighlighted(false);
        setHasHighlight(false);
        for (WordText word : words) {
            word.setHighlighted(false);
        }
    }

    /**
     * Interates over child elements getting the selected text as defined by
     * the child glyphs unicode value. Line breaks and spaces are preserved
     * where possible.
     * @return StringBuffer of selected text in this line.
     */
    public StringBuffer getSelected() {
        StringBuffer selectedText = new StringBuffer();
        for (WordText word : words) {
            selectedText.append(word.getSelected());
        }
        if (hasSelected) {
            selectedText.append('\n');
        }
        return selectedText;
    }

    public String toString() {
        return words.toString();
    }
}
