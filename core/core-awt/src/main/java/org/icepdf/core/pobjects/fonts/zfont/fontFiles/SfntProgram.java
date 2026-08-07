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
package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import java.util.Arrays;

/**
 * Reads just enough of an SFNT table directory to tell what a font program really is, for the
 * cases where the subtype a producer declared can't be trusted.
 */
public final class SfntProgram {

    /**
     * Table directory tag of an OpenType font whose outlines are PostScript rather than TrueType.
     */
    private static final int OTTO = 0x4F54544F;
    private static final String CFF_TAG = "CFF ";
    private static final String GLYF_TAG = "glyf";

    private static final int DIRECTORY_LENGTH = 12;
    private static final int RECORD_LENGTH = 16;

    private SfntProgram() {
    }

    /**
     * Returns the 'CFF ' table of an OpenType program carrying PostScript outlines, or null when
     * the bytes are anything else.
     * <p>
     * Quartz writes these under /FontFile2 with a /CIDFontType2 subtype, keeping only the 'CFF '
     * and 'cmap' tables, so there is no 'head' or 'glyf' and the program cannot be read as
     * TrueType at all. Lifting the CFF out is what keeps the embedded glyphs: falling back to
     * substitution instead strands the Identity-H codes, which are the subset's own glyph
     * indices and index nothing meaningful in a system font.
     *
     * @param fontBytes decoded font program, may be null
     * @return the CFF table's bytes, or null if this is not an OpenType/CFF program
     */
    public static byte[] postScriptOutlines(byte[] fontBytes) {
        if (fontBytes == null || fontBytes.length < DIRECTORY_LENGTH
                || readInt(fontBytes, 0) != OTTO) {
            return null;
        }
        int numTables = readUnsignedShort(fontBytes, 4);
        int cffOffset = -1;
        int cffLength = -1;
        for (int i = 0; i < numTables; i++) {
            int record = DIRECTORY_LENGTH + i * RECORD_LENGTH;
            if (record + RECORD_LENGTH > fontBytes.length) {
                return null;
            }
            String tag = readTag(fontBytes, record);
            if (GLYF_TAG.equals(tag)) {
                // genuine TrueType outlines are present, leave the program to the TrueType parser
                return null;
            }
            if (CFF_TAG.equals(tag)) {
                cffOffset = readInt(fontBytes, record + 8);
                cffLength = readInt(fontBytes, record + 12);
            }
        }
        if (cffOffset < 0 || cffLength <= 0 || cffOffset > fontBytes.length - cffLength) {
            return null;
        }
        return Arrays.copyOfRange(fontBytes, cffOffset, cffOffset + cffLength);
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    private static int readUnsignedShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static String readTag(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
    }
}
