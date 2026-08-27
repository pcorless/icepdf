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
package org.icepdf.core.pobjects.fonts.builders;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.fonts.FontDescriptor;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.zfont.Type1Font;
import org.icepdf.core.util.Library;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.icepdf.core.pobjects.fonts.FontDescriptor.ASCENT;
import static org.icepdf.core.pobjects.fonts.FontDescriptor.CAP_HEIGHT;
import static org.icepdf.core.pobjects.fonts.FontDescriptor.DESCENT;
import static org.icepdf.core.pobjects.fonts.FontDescriptor.FLAGS;
import static org.icepdf.core.pobjects.fonts.FontDescriptor.FONT_BBOX;
import static org.icepdf.core.pobjects.fonts.Font.FONT_FLAG_NON_SYMBOLIC;
import static org.icepdf.core.pobjects.fonts.FontDescriptor.ITALIC_ANGLE;
import static org.icepdf.core.pobjects.fonts.FontDescriptor.STEM_V;
import static org.icepdf.core.pobjects.fonts.FontFactory.FONT_SUBTYPE_TYPE_1;
import static org.icepdf.core.pobjects.fonts.zfont.SimpleFont.TO_UNICODE_KEY;

/**
 * The fallback font dictionary, for a face that cannot be embedded.
 * <p>
 * A simple font dictionary must carry {@code /Widths}, {@code /FirstChar}, {@code /LastChar} and a
 * {@code /FontDescriptor}, with one exception: the standard 14 fonts, which every reader is required
 * to have and to know the metrics of (PDF 32000-1, 9.6.2.1). This wrote {@code /FirstChar 32} and
 * {@code /LastChar 255} and then neither the widths those two describe nor a descriptor - a
 * combination that is legal for none of the fourteen and correct for no font at all: a reader is told
 * the font covers 224 codes and given no metrics for any of them, so the text is spaced by guesswork.
 * <p>
 * A standard 14 name now omits all four and lets the reader use the metrics it already has. Any other
 * name gets real widths measured from the substitute face, and a descriptor built from it.
 */
public class Type1FontBuilder {

    private static final Logger LOGGER = Logger.getLogger(Type1FontBuilder.class.toString());

    /**
     * WinAnsiEncoding's printable range, which is what this font declares.
     */
    private static final int FIRST_CHAR = 32;
    private static final int LAST_CHAR = 255;

    /**
     * A PDF glyph space is 1000 units to the em, and a font derived at size 1 measures in ems.
     */
    private static final float GLYPH_SPACE = 1000f;

    private final Library library;
    private final String fontName;
    private final FontFile fontFile;

    public Type1FontBuilder(Library library, String fontName) {
        this(library, fontName, null);
    }

    /**
     * @param fontFile the face that will actually be used to draw this text, measured for the widths
     *                 and the descriptor.  Without it only a standard 14 name can be written
     *                 correctly.
     */
    public Type1FontBuilder(Library library, String fontName, FontFile fontFile) {
        this.library = library;
        this.fontName = fontName;
        this.fontFile = fontFile;
    }

    public Type1Font Build() {
        // This font is not embedded, and PDF/A requires that every font is.  The path is taken when
        // the face cannot be embedded - a licence that forbids it, or embedding turned off - so the
        // choice is this or no text at all; but a document that reaches here will not validate, and
        // silence about that is how it gets discovered by a validator instead of by us.
        LOGGER.warning("Falling back to a non-embedded Type 1 font for '" + fontName
                + "'; the document will not conform to PDF/A, which requires embedded fonts");

        DictionaryEntries fontDictionary = new DictionaryEntries();
        // /Type /Font.  This used to put the *name* "Subtype" in as the value of /Type, which is
        // not a type any reader knows.
        fontDictionary.put(Font.TYPE_KEY, Font.TYPE);
        fontDictionary.put(Font.SUBTYPE_KEY, FONT_SUBTYPE_TYPE_1);
        fontDictionary.put(Font.NAME_KEY, new Name(fontName));
        fontDictionary.put(Font.BASEFONT_KEY, new Name(fontName));
        fontDictionary.put(Font.ENCODING_KEY, new Name("WinAnsiEncoding"));

        List<Integer> codes = new ArrayList<>();
        for (int code = FIRST_CHAR; code <= LAST_CHAR; code++) {
            codes.add(code);
        }
        fontDictionary.put(TO_UNICODE_KEY, ToUnicodeCMap.forCodes(library, codes));

        if (!Font.isCore14Name(fontName)) {
            if (fontFile != null) {
                fontDictionary.put(new Name("FirstChar"), FIRST_CHAR);
                fontDictionary.put(new Name("LastChar"), LAST_CHAR);
                fontDictionary.put(new Name("Widths"), widths());
                fontDictionary.put(new Name("FontDescriptor"), fontDescriptor());
            } else {
                // Better to say so than to write /FirstChar and /LastChar describing widths that are
                // not there, which is what used to happen for every font including these.
                LOGGER.warning("No font file available to measure '" + fontName + "'; writing it "
                        + "without /Widths or /FontDescriptor, which is only valid for a standard 14 "
                        + "font and this is not one");
            }
        }

        Type1Font font = new Type1Font(library, fontDictionary);
        font.setPObjectReference(library.getStateManager().getNewReferenceNumber());
        library.getStateManager().addTempChange(new PObject(font, font.getPObjectReference()));
        return font;
    }

    /**
     * One width per code from /FirstChar to /LastChar, measured from the face that will draw the
     * text.  A code the encoding defines nothing at gets 0, which is what the specification says to
     * write for a code with no glyph.
     */
    private List<Integer> widths() {
        FontFile measured = fontFile.deriveFont(1f);
        List<Integer> widths = new ArrayList<>(LAST_CHAR - FIRST_CHAR + 1);
        for (int code = FIRST_CHAR; code <= LAST_CHAR; code++) {
            int unicode = WinAnsiEncoding.unicodeOf(code);
            if (unicode < 0) {
                widths.add(0);
            } else {
                widths.add(Math.round((float) measured.getAdvance((char) unicode).getX() * GLYPH_SPACE));
            }
        }
        return widths;
    }

    /**
     * A descriptor for a font that is not embedded: it describes the face well enough for a reader to
     * pick a substitute, which is all it can do without the font programme.
     */
    private Reference fontDescriptor() {
        FontFile measured = fontFile.deriveFont(1f);
        Rectangle2D bounds = measured.getMaxCharBounds();

        DictionaryEntries descriptor = new DictionaryEntries();
        descriptor.put(Font.TYPE_KEY, new Name("FontDescriptor"));
        descriptor.put(new Name("FontName"), new Name(fontName));
        // Exactly one of Symbolic and Nonsymbolic shall be set (PDF 32000-1, Table 123).  This font
        // declares WinAnsiEncoding and draws text, so it is nonsymbolic.
        descriptor.put(FLAGS, FONT_FLAG_NON_SYMBOLIC);
        descriptor.put(FONT_BBOX, List.of(
                (float) bounds.getMinX() * GLYPH_SPACE,
                (float) bounds.getMinY() * GLYPH_SPACE,
                (float) bounds.getMaxX() * GLYPH_SPACE,
                (float) bounds.getMaxY() * GLYPH_SPACE));
        descriptor.put(ITALIC_ANGLE, 0);
        descriptor.put(ASCENT, (float) measured.getAscent() * GLYPH_SPACE);
        descriptor.put(DESCENT, (float) measured.getDescent() * GLYPH_SPACE);
        descriptor.put(CAP_HEIGHT, (float) measured.getAscent() * GLYPH_SPACE);
        // there is no metric for this in the face, so it is estimated, as it is for TrueType
        descriptor.put(STEM_V, (float) bounds.getWidth() * GLYPH_SPACE * .13f);

        StateManager stateManager = library.getStateManager();
        Reference reference = stateManager.getNewReferenceNumber();
        FontDescriptor fontDescriptor = new FontDescriptor(library, descriptor);
        fontDescriptor.setPObjectReference(reference);
        stateManager.addTempChange(new PObject(fontDescriptor, reference));
        return reference;
    }
}
