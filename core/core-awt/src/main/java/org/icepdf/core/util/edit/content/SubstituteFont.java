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
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.fonts.FontFactory;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.zfont.SimpleFont;
import org.icepdf.core.pobjects.fonts.builders.SimpleFontFactory;
import org.icepdf.core.pobjects.fonts.builders.TrueTypeFontEmbedder;
import org.icepdf.core.util.Library;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A font to write replacement text in when the original cannot write it.
 * <p>
 * The font a run is drawn with is not a typeface, it is whatever subset of one the document needed.
 * A composite font subset holds the characters the document already uses and no others, so correcting
 * a scanning error - the usual reason to edit at all - routinely needs a character that is not in it.
 * The alternatives are to refuse the edit or to write it in something else; this is something else.
 * <p>
 * The substitute will not be the original typeface and is not meant to be. It is chosen to be the
 * same <em>kind</em> of face - serif, sans or monospaced, and bold or italic if the original was - so
 * a corrected word reads as part of its line rather than as a different document. For the small edits
 * this is for, that is usually enough; for anything larger, republishing from the source is the
 * honest advice.
 *
 * @since 7.5.0
 */
public class SubstituteFont {

    private static final Logger logger = Logger.getLogger(SubstituteFont.class.getName());

    /** Names the standard 14 use, which the font factory resolves to whatever is actually available. */
    private static final String SANS = "Helvetica";
    private static final String SERIF = "Times New Roman";
    private static final String MONO = "Courier";

    private final Name resourceName;
    private final FontFile fontFile;
    private final byte subTypeFormat;

    private SubstituteFont(Name resourceName, FontFile fontFile, byte subTypeFormat) {
        this.resourceName = resourceName;
        this.fontFile = fontFile;
        this.subTypeFormat = subTypeFormat;
    }

    /**
     * The name the content stream selects this font by.
     */
    public Name getResourceName() {
        return resourceName;
    }

    /**
     * The font itself, for the advances the replacement's layout is computed from - which must come
     * from the substitute, not the original, or the text after it lands in the wrong place.
     */
    public FontFile getFontFile() {
        return fontFile;
    }

    /**
     * How wide a character code in this font is, as {@link Font#SIMPLE_FORMAT} or
     * {@link Font#CID_FORMAT}.
     * <p>
     * Not always simple. The substitute is subsetted to the replacement text, and text outside what
     * a one-byte WinAnsiEncoding can reach is built as a composite font - which is the ordinary case
     * here, since a character the document's own font cannot write is often one WinAnsiEncoding
     * cannot either. Assuming simple wrote a two-byte CID as one byte, so the substitute drew the
     * wrong glyphs at exactly the characters it was added for.
     */
    public byte getSubTypeFormat() {
        return subTypeFormat;
    }

    /**
     * Builds a substitute for {@code original}, embeds it, and adds it to the page's resources.
     *
     * @param page     page being edited, which gains the font resource
     * @param original the font being replaced, read for its style
     * @param text     the replacement text, which the substitute is subsetted to
     * @return the substitute, or null when one could not be built - in which case the caller should
     * refuse the edit rather than write something wrong
     */
    public static SubstituteFont forText(Page page, Font original, String text) {
        Library library = page.getLibrary();
        try {
            String faceName = faceFor(original);
            FontFile faceFile = FontFactory.getInstance().createFontFile(library, faceName);
            if (faceFile == null) {
                return null;
            }
            TrueTypeFontEmbedder embedder = new TrueTypeFontEmbedder(faceFile);
            for (int i = 0; i < text.length(); i++) {
                embedder.addToSubset(text.charAt(i));
            }
            SimpleFont substitute = SimpleFontFactory.createFont(library, faceName, embedder);
            if (substitute == null || substitute.getPObjectReference() == null) {
                return null;
            }
            // A name that cannot collide with the document's own, since the page keeps using those.
            Name resourceName = new Name("IcePdfEdit" + substitute.getPObjectReference().getObjectNumber());
            page.addFontResource(resourceName, substitute.getPObjectReference());
            substitute.init();
            // Asked of the embedder rather than the built font: it is the embedder that decided,
            // from the same subset, which of the two builders ran.
            byte subTypeFormat = embedder.requiresCompositeFont() ? Font.CID_FORMAT : Font.SIMPLE_FORMAT;
            return new SubstituteFont(resourceName, substitute.getFont(), subTypeFormat);
        } catch (Exception e) {
            // Deliberately broad: font construction reaches into font parsing, subsetting and the
            // file system, and none of it is worth failing an edit over when refusing is an option.
            logger.log(Level.WARNING, "Could not build a substitute font for an edit", e);
            return null;
        }
    }

    /**
     * A face of the same kind as the original. Read from the original's own name, which carries the
     * style far more reliably than the descriptor flags do in practice.
     */
    private static String faceFor(Font original) {
        String name = original != null && original.getBaseFont() != null
                ? original.getBaseFont().toLowerCase() : "";
        String face = SANS;
        if (name.contains("times") || name.contains("serif") || name.contains("roman")
                || name.contains("georgia") || name.contains("book")) {
            face = SERIF;
        } else if (name.contains("courier") || name.contains("mono") || name.contains("consol")) {
            face = MONO;
        }
        boolean bold = name.contains("bold") || name.contains("black") || name.contains("heavy");
        boolean italic = name.contains("italic") || name.contains("oblique");
        if (bold && italic) {
            return face + " Bold Italic";
        } else if (bold) {
            return face + " Bold";
        } else if (italic) {
            return face + " Italic";
        }
        return face;
    }
}
