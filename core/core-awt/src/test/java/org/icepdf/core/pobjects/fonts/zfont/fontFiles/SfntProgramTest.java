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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Quartz writes an OpenType/CFF program under /FontFile2 with a /CIDFontType2 subtype, keeping
 * only the 'CFF ' and 'cmap' tables.  Recognising that is what lets the embedded glyphs be used
 * instead of a substitute that the Identity-H codes cannot index.
 */
public class SfntProgramTest {

    /** Builds an SFNT with the given sfnt version and tables, laid out after the directory. */
    private static byte[] sfnt(String version, String[] tags, byte[][] data) throws Exception {
        int directory = 12 + tags.length * 16;
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        head.write(version.getBytes(StandardCharsets.ISO_8859_1));
        writeShort(head, tags.length);
        writeShort(head, 0);
        writeShort(head, 0);
        writeShort(head, 0);
        int offset = directory;
        for (int i = 0; i < tags.length; i++) {
            head.write(tags[i].getBytes(StandardCharsets.ISO_8859_1));
            writeInt(head, 0);
            writeInt(head, offset);
            writeInt(head, data[i].length);
            body.write(data[i]);
            offset += data[i].length;
        }
        head.write(body.toByteArray());
        return head.toByteArray();
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xff);
        out.write(value & 0xff);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >> 24) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 8) & 0xff);
        out.write(value & 0xff);
    }

    @DisplayName("an OTTO program's CFF table is lifted out")
    @Test
    public void extractsCffFromOpenType() throws Exception {
        byte[] cff = {1, 0, 4, 4, 9, 9, 9};
        byte[] cmap = {0, 0, 0, 1};
        byte[] font = sfnt("OTTO", new String[]{"CFF ", "cmap"}, new byte[][]{cff, cmap});
        assertArrayEquals(cff, SfntProgram.postScriptOutlines(font));
    }

    @DisplayName("table order does not matter")
    @Test
    public void extractsCffWhenItIsNotFirst() throws Exception {
        byte[] cff = {1, 0, 4, 4, 7};
        byte[] cmap = {0, 0, 0, 1};
        byte[] font = sfnt("OTTO", new String[]{"cmap", "CFF "}, new byte[][]{cmap, cff});
        assertArrayEquals(cff, SfntProgram.postScriptOutlines(font));
    }

    @DisplayName("a genuine TrueType program is left to the TrueType parser")
    @Test
    public void ignoresTrueTypeOutlines() throws Exception {
        byte[] glyf = {1, 2, 3, 4};
        byte[] font = sfnt("true", new String[]{"glyf"}, new byte[][]{glyf});
        assertNull(SfntProgram.postScriptOutlines(font));
    }

    @DisplayName("an OpenType that also carries glyf outlines is left alone")
    @Test
    public void ignoresOpenTypeThatHasGlyf() throws Exception {
        // 'glyf' means there are real TrueType outlines to draw, whatever else the font carries,
        // so it must not be diverted to the CFF path.
        byte[] cff = {1, 0, 4, 4};
        byte[] glyf = {1, 2, 3, 4};
        byte[] font = sfnt("OTTO", new String[]{"CFF ", "glyf"}, new byte[][]{cff, glyf});
        assertNull(SfntProgram.postScriptOutlines(font));
    }

    @DisplayName("a bare CFF program is not an SFNT and is left alone")
    @Test
    public void ignoresBareCff() {
        assertNull(SfntProgram.postScriptOutlines(new byte[]{1, 0, 4, 4, 0, 0, 0, 0, 0, 0, 0, 0}));
    }

    @DisplayName("truncated and empty programs are rejected rather than throwing")
    @Test
    public void rejectsMalformed() throws Exception {
        assertNull(SfntProgram.postScriptOutlines(null));
        assertNull(SfntProgram.postScriptOutlines(new byte[0]));
        assertNull(SfntProgram.postScriptOutlines("OTTO".getBytes(StandardCharsets.ISO_8859_1)));
        // a directory that claims more tables than the bytes can hold
        byte[] font = sfnt("OTTO", new String[]{"CFF "}, new byte[][]{{1, 0, 4, 4}});
        font[5] = 8;
        assertNull(SfntProgram.postScriptOutlines(font));
    }

    @DisplayName("a CFF table pointing outside the program is rejected")
    @Test
    public void rejectsOutOfBoundsTable() throws Exception {
        byte[] font = sfnt("OTTO", new String[]{"CFF "}, new byte[][]{{1, 0, 4, 4}});
        // overwrite the CFF record's length with one that runs past the end
        font[12 + 12] = 0x7f;
        assertNull(SfntProgram.postScriptOutlines(font));
    }
}
