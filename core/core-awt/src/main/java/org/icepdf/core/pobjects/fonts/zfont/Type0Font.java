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
package org.icepdf.core.pobjects.fonts.zfont;

import org.apache.fontbox.cmap.CMap;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.FontDescriptor;
import org.icepdf.core.pobjects.fonts.zfont.cmap.CMapFactory;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontType2;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZSimpleFont;
import org.icepdf.core.util.Library;

import java.io.IOException;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Type0Font extends SimpleFont {

    private static final Logger logger =
            Logger.getLogger(SimpleFont.class.getName());

    public static final Name DESCENDANT_FONTS_KEY = new Name("DescendantFonts");

    private CMap cMap;

    /**
     * Creates a new instance of a PDF Font.
     *
     * @param library Library of all objects in PDF
     * @param entries hash of parsed font attributes
     */
    public Type0Font(Library library, DictionaryEntries entries) {
        super(library, entries);
    }

    /**
     * Splits the string into character codes using the encoding CMap's codespace ranges, as required
     * by PDF 32000-1 9.7.6.2.
     * <p>
     * A composite font's code width is a property of its CMap, not of the string data and not of the
     * font's glyph coverage.  {@code Identity-H} declares one codespace range, {@code <0000>} to
     * {@code <FFFF>}, so every code is two bytes; a Shift-JIS CMap such as {@code 90ms-RKSJ-H} mixes
     * one- and two-byte ranges, so the width varies from code to code.  Codes that fall outside every
     * range still consume the width of the range their first byte selects and map to CID&nbsp;0 &mdash;
     * they are never silently narrowed, which would desynchronise the rest of the string.
     *
     * @param bytes the string's raw bytes
     * @return the character codes, one per char
     */
    @Override
    public StringBuilder toCodes(byte[] bytes) {
        if (cMap != null) {
            StringBuilder codes = new StringBuilder(bytes.length);
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            try {
                while (in.available() > 0) {
                    codes.append((char) cMap.readCode(in));
                }
                return codes;
            } catch (IOException e) {
                // The bytes ran out mid-code: the string is truncated, but what came before it is
                // still good, so keep it rather than re-reading the whole string another way.
                logger.log(Level.FINER, "Truncated character code in show-text string", e);
                return codes;
            } catch (RuntimeException e) {
                // A CMap that declares no codespace ranges at all leaves FontBox with nothing to
                // match against and it indexes past the end of its buffer.  Malformed, but it must
                // not take the content stream down with it; fall through to the two-byte default.
                logger.log(Level.WARNING, () -> "Unusable codespace ranges in CMap "
                        + cMap.getName() + ", falling back to two-byte character codes");
            }
        }
        return twoByteCodes(bytes);
    }

    /**
     * The composite-font default: fixed two-byte codes.  Used when there is no CMap, or the CMap
     * cannot say how wide a code is.  Two bytes because that is what every Identity and every
     * CJK-ordering CMap in common use declares, and because a fixed width at least keeps the rest of
     * the string in step, which is what actually matters &mdash; a mis-sized code corrupts one glyph,
     * a mis-*aligned* one corrupts every glyph after it.
     */
    private static StringBuilder twoByteCodes(byte[] bytes) {
        StringBuilder codes = new StringBuilder((bytes.length + 1) / 2);
        for (int i = 0; i < bytes.length; i += 2) {
            int code = (bytes[i] & 0xFF) << 8;
            if (i + 1 < bytes.length) code |= bytes[i + 1] & 0xFF;
            codes.append((char) code);
        }
        return codes;
    }

    @Override
    public synchronized void init() {
        if (inited) {
            return;
        }

        parseToUnicode();
        parseEncoding();
        parseDescendantFont();

        if (font == null) {
            logger.warning("Type0Font: " + library.getName(entries, NAME_KEY) +
                    " could not find descendant font.");
            findFontIfNotEmbedded();
            if (font != null) {
                font = font.deriveFont(encoding, toUnicodeCMap != null ? toUnicodeCMap : font.getToUnicode());
            }
        }
        inited = true;
    }

    protected void parseEncoding() {
        Name name = library.getName(entries, ENCODING_KEY);
        if (name != null) {
            cMap = CMapFactory.getPredefinedCMap(name);
            encoding = Encoding.getInstance((name).getName());
            return;
        }
        Object object = library.getObject(entries, ENCODING_KEY);
        if (object instanceof Stream) {
            try {
                Stream gidMap = (Stream) object;
                Name cmapName = library.getName(gidMap.getEntries(), new Name("CMapName"));
                // update font with oneByte information from the cmap, so far I've only
                // scene this on a handful of CID font but fix encoding issue in each case.
                if (cmapName.equals("OneByteIdentityH")) {
                    subTypeFormat = SIMPLE_FORMAT;
                }
                // todo pull registry info CIDSystemInfo
                cMap = CMapFactory.parseEmbeddedCMap(gidMap);
                cMap.setName(cmapName.getName());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }
    }

    /**
     * Gives the font file a CID&rarr;Unicode route when the PDF supplies no {@code /ToUnicode} CMap.
     * <p>
     * {@code /ToUnicode} is optional, and an embedded CID font routinely omits it &mdash; the glyphs
     * render from the CIDs alone, so nothing about <em>display</em> needs Unicode.  Extraction, copy
     * and search do, and without this they get the raw CID: the anchor case (GH-521) extracted an
     * entire Japanese page as control characters while rendering it perfectly.  PDF 32000-1 9.10.2
     * (b)&ndash;(d) covers exactly this: the descendant font's {@code CIDSystemInfo} names the
     * character collection, and the collection's UCS2 CMap maps its CIDs to Unicode.
     * <p>
     * Only fills the gap &mdash; an explicit {@code /ToUnicode} always wins, as it must, since it is
     * the only thing that can describe a subset font with an {@code Identity} ordering.
     */
    private void applyCidSystemInfoToUnicode(CompositeFont descendantFont) {
        if (toUnicodeCMap != null || !(font instanceof ZSimpleFont)) {
            return;
        }
        CMap ucs2CMap = descendantFont.getUcs2CMap();
        if (ucs2CMap != null) {
            // cMap is the /Encoding CMap, code -> CID; null (Identity) means the code is the CID.
            ((ZSimpleFont) font).setCidToUnicode(cMap, ucs2CMap);
        }
    }

    private void parseDescendantFont() {
        if (entries.containsKey(DESCENDANT_FONTS_KEY)) {
            Object descendant = library.getObject(entries, DESCENDANT_FONTS_KEY);
            if (descendant instanceof List) {
                List descendantFonts = (List) descendant;
                CompositeFont descendantFont = null;
                Object descendantFontObject = descendantFonts.get(0);
                if (descendantFontObject instanceof Reference) {
                    Reference descendantFontReference = (Reference) descendantFontObject;
                    descendantFontObject = library.getObject(descendantFontReference);
                }

                if (descendantFontObject instanceof CompositeFont) {
                    descendantFont = (CompositeFont) descendantFontObject;
                }
                // strange malformed PDFe where the descendant font is a font descriptor,
                else if (descendantFontObject instanceof FontDescriptor) {
                    fontDescriptor = (FontDescriptor) descendantFontObject;
                    parseFontDescriptor();
                }

                if (descendantFont != null) {
                    descendantFont.init();
                    font = descendantFont.getFont();
                    if (font instanceof ZFontType2) {
                        font = ((ZFontType2) font).deriveFont(encoding, cMap, toUnicodeCMap);
                    } else {
                        font = font.deriveFont(encoding, toUnicodeCMap);
                    }
                    applyCidSystemInfoToUnicode(descendantFont);
                    isFontSubstitution = descendantFont.isFontSubstitution() && font != null;
                }
            }
        }
    }

}
