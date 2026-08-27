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
package org.icepdf.core.util.edit.content;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.fonts.FontFactory;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.WordText;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Whether the text somebody wants to type can be written in the font it would have to be written in.
 * <p>
 * Replacing text reuses the font of the run being replaced - there is nowhere else for the new
 * characters to come from - and a font in a PDF is not a typeface, it is whatever subset of one the
 * document happened to need. A composite (CID) font is the sharp case: the character code for a
 * character is found by running the font's {@code /ToUnicode} map backwards, and that map only
 * answers for characters the font is already being used to draw. Type a character the document never
 * used and there is no code to write.
 * <p>
 * Left unchecked this fails silently - a wrong glyph, or a blank where a letter should be - which is
 * the worst way for it to fail, because the document looks edited.
 *
 * @since 7.5.0
 */
public class TextEditCapability {

    private TextEditCapability() {
    }

    /**
     * The characters of {@code newText} that cannot be written in the font covering {@code bounds}.
     * <p>
     * Checked by round trip: the character is mapped to a code the way the writer will map it, and
     * that code is mapped back. If it does not come back the same character, the font has no code for
     * it and writing it would put something else on the page.
     * <p>
     * <b>What this does not catch.</b> A font whose {@code /ToUnicode} is an identity mapping answers
     * every round trip, because the map is not consulted so much as bypassed. Such a font can report
     * a character writable whose glyph is not in the embedded subset, and the result is a blank. The
     * check that would close that gap is glyph presence in the font program, which the
     * {@link FontFile} interface does not currently expose.
     *
     * @param page     page being edited
     * @param bounds   area of the text being replaced, in page space
     * @param newText  the replacement
     * @return the distinct characters that cannot be written, in the order they appear; empty when
     * the edit can be made as asked
     * @throws InterruptedException if resolving the page's fonts is interrupted
     */
    public static List<Character> unsupportedCharacters(Page page, Rectangle bounds, String newText)
            throws InterruptedException {
        if (newText == null || newText.isEmpty()) {
            return new ArrayList<>();
        }
        Font pdfFont = fontAt(page, bounds);
        FontFile font = pdfFont != null ? pdfFont.getFont() : null;
        List<Character> unsupported = new ArrayList<>();
        if (font == null) {
            // Nothing to check against - no glyph of the selection could be resolved to a font - so
            // this cannot say the edit will fail, and saying so would block edits that work.
            return unsupported;
        }
        Set<Character> seen = new LinkedHashSet<>();
        for (int i = 0, max = newText.length(); i < max; i++) {
            char character = newText.charAt(i);
            if (seen.add(character) && !canWrite(font, character)) {
                unsupported.add(character);
            }
        }
        return unsupported;
    }

    /**
     * Whether the text here can be edited at all.
     * <p>
     * Almost always yes. A character the run's own font cannot write is not a refusal - it is written
     * in a substitute font instead - so the only text that cannot be edited is text whose glyphs are
     * not characters in a font to begin with.
     *
     * @param page   page being edited
     * @param bounds area of the text in question, in page space
     * @return true when an edit can be offered
     * @throws InterruptedException if resolving the page's fonts is interrupted
     */
    public static boolean canEdit(Page page, Rectangle bounds) throws InterruptedException {
        return unsupportedReason(page, bounds) == null;
    }

    /**
     * Whether making this edit means writing it in a font other than the one the text is drawn in.
     * <p>
     * Not a problem, but a visible change: the correction will read as part of its line without being
     * the same typeface. Worth telling whoever asked for it, since they are looking at the document.
     *
     * @param page    page being edited
     * @param bounds  area of the text being replaced, in page space
     * @param newText the replacement
     * @return true when a substitute font will be used
     * @throws InterruptedException if resolving the page's fonts is interrupted
     */
    public static boolean requiresSubstitution(Page page, Rectangle bounds, String newText)
            throws InterruptedException {
        return !unsupportedCharacters(page, bounds, newText).isEmpty();
    }

    /**
     * Why this text cannot be edited, as a key a caller can turn into a message, or null when it
     * can be.
     * <p>
     * A key rather than a sentence because the caller is a user interface with its own translations;
     * core has no business deciding what language to apologise in. The only value today is
     * {@code "type3"}.
     *
     * @param page   page being edited
     * @param bounds area of the text in question, in page space
     * @return the reason key, or null when the text can be edited
     * @throws InterruptedException if resolving the page's fonts is interrupted
     */
    public static String unsupportedReason(Page page, Rectangle bounds) throws InterruptedException {
        if (isType3(page, bounds)) {
            return "type3";
        }
        return null;
    }

    /**
     * Whether the text here is drawn with a Type 3 font.
     * <p>
     * A Type 3 glyph is a content stream the page draws, not a character in a font program, so there
     * is nothing to write a new character <em>as</em>: a replacement would need its own glyph
     * procedures built and added to the font's {@code /CharProcs}. Out of scope, and better refused
     * plainly than attempted badly.
     */
    private static boolean isType3(Page page, Rectangle bounds) throws InterruptedException {
        Font font = fontAt(page, bounds);
        return font != null && FontFactory.FONT_SUBTYPE_TYPE_3.equals(font.getSubType());
    }

    /**
     * Whether this font has a character code for this character.
     * <p>
     * A space is always allowed: a font that draws nothing for it still advances, and refusing an
     * edit for want of a space would be absurd.
     */
    private static boolean canWrite(FontFile font, char character) {
        if (Character.isWhitespace(character)) {
            return true;
        }
        char code = font.toSelector(character);
        String roundTrip = font.toUnicode(code);
        return roundTrip != null && roundTrip.length() == 1 && roundTrip.charAt(0) == character;
    }

    /**
     * The font the replacement would be written in: the one belonging to the first glyph of the
     * selection, in the order the page draws them, which is the run the writer replaces.
     */
    /**
     * The font the replacement would be written in, which a caller building a substitute needs to
     * match the style of.
     *
     * @param page   page being edited
     * @param bounds area of the text being replaced
     * @return the font, or null when no glyph there could be resolved to one
     * @throws InterruptedException if resolving the page's fonts is interrupted
     */
    public static Font fontAt(Page page, Rectangle bounds) throws InterruptedException {
        if (page.getViewText() == null || bounds == null) {
            return null;
        }
        Resources resources = page.getResources();
        if (resources == null) {
            return null;
        }
        for (LineText line : page.getViewText().getPageLines()) {
            for (WordText word : line.getWords()) {
                for (GlyphText glyph : word.getGlyphs()) {
                    Rectangle2D glyphBounds = glyph.getBounds();
                    if (glyphBounds != null && bounds.intersects(glyphBounds)) {
                        Name fontName = glyph.getFontName();
                        Font font = fontName != null ? resources.getFont(fontName) : null;
                        if (font != null) {
                            return font;
                        }
                    }
                }
            }
        }
        return null;
    }
}
