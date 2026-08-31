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
package org.icepdf.core.pobjects.graphics.commands;

import org.icepdf.core.pobjects.annotations.utils.ContentWriterUtils;
import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.pobjects.fonts.builders.TrueTypeFontEmbedder;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bytes an appearance stream is written with.
 * <p>
 * A content stream is bytes, and the bytes inside a show operator are character codes in the font's
 * encoding - WinAnsiEncoding for the fonts built here, which is Windows-1252. The encoder ended with
 * {@code toString().getBytes()}, the platform default, so on a UTF-8 machine every character above
 * 0x7F was written as two or three codes and drawn as that many wrong glyphs. It also meant the same
 * annotation produced different bytes on different machines.
 */
public class PostScriptEncoderTest {

    @BeforeAll
    public static void init() {
        FontManager.getInstance().initialize();
    }

    /**
     * A left double quotation mark is U+201C, code 0x93 in Windows-1252, and 0xE2 0x80 0x9C in UTF-8.
     * Which of those appears in the stream is the whole test.
     */
    @DisplayName("text is written as character codes, not as UTF-8")
    @Test
    public void textIsWrittenAsWinAnsiCharacterCodes() throws Exception {
        byte[] contentStream = appearanceFor("A\u201CB");
        String text = new String(contentStream, java.nio.charset.StandardCharsets.ISO_8859_1);

        // 0x93 under WinAnsiEncoding, written as an octal escape.  A literal string may carry the
        // byte itself, but escaping it keeps the whole content stream seven-bit, so it survives
        // being handled as text by anything downstream - which is how it came to be written as
        // UTF-8 in the first place.
        assertTrue(text.contains("\\223"),
                "the left double quote should be the single code 0x93:\n" + text);
        assertFalse(indexOf(contentStream, new byte[]{(byte) 0xE2, (byte) 0x80, (byte) 0x9C}) >= 0,
                "and must not be its UTF-8 encoding, which is three character codes");
        for (byte b : contentStream) {
            assertTrue(b >= 0, "the content stream should be seven-bit:\n" + text);
        }
    }

    /**
     * The control: operators are ASCII and are unaffected by the change, so a stream that lost them
     * would fail here rather than quietly passing the assertion above.
     */
    @DisplayName("the operators are still there")
    @Test
    public void operatorsAreUnaffected() throws Exception {
        String contentStream = new String(appearanceFor("A\u201CB"), java.nio.charset.StandardCharsets.ISO_8859_1);

        assertTrue(contentStream.contains("BT"), "should open a text object:\n" + contentStream);
        assertTrue(contentStream.contains("TJ"), "and show the text:\n" + contentStream);
        assertTrue(contentStream.contains("ET"), "and close it:\n" + contentStream);
    }

    private byte[] appearanceFor(String text) throws Exception {
        TrueTypeFontEmbedder embedder = new TrueTypeFontEmbedder(
                FontManager.getInstance().getInstance("Helvetica", 0));
        Shapes shapes = new Shapes();
        ContentWriterUtils.addTextSpritesToShapes(embedder, 0, 0, shapes, 12, 2, Color.BLACK, text);
        return PostScriptEncoder.generatePostScript(shapes.getShapes());
    }

    private int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
