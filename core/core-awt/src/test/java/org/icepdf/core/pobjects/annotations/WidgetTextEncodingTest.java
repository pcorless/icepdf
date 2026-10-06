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
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.geom.AffineTransform;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The character codes a form field's generated appearance is written with.
 * <p>
 * How wide a character code is belongs to the font: a simple font's codes are one byte and are
 * whatever its {@code /Encoding} says, a composite font's are two. The widgets wrote every character
 * as four hex digits whatever the font was, which is right only for the composite case. The usual
 * {@code /DA} font is {@code /Helv}, a simple one, and a field showing "Ab" was written
 * {@code <00410062>} - four codes, {@code 00 41 00 62}, a .notdef in front of every character.
 * <p>
 * Nobody had complained, which is worth knowing about the failure mode: it draws, it just draws a
 * blank box before each letter and reports the wrong text to anything that extracts it.
 */
public class WidgetTextEncodingTest {

    /**
     * The field's value in both fixtures.
     */
    private static final String VALUE = "Ab";

    /**
     * {@code /Helv}, a Type1 font with WinAnsiEncoding - one byte to a code, and A is 0x41.
     */
    @DisplayName("a field drawn with a simple font is written in one-byte codes")
    @Test
    public void simpleFontFieldUsesOneByteCodes() throws Exception {
        String contentStream = appearanceOf("text_field.pdf");

        assertTrue(contentStream.contains("(Ab)"),
                "A and b are codes 0x41 and 0x62 in this font:\n" + contentStream);
        assertTrue(!contentStream.contains("<0041"),
                "and must not be written as two-byte codes:\n" + contentStream);
    }

    /**
     * The round trip, which is what a reader and a text extractor both do. Before, this came back
     * with a .notdef between every character rather than the two the field holds.
     */
    @DisplayName("a field drawn with a simple font reads back as its value")
    @Test
    public void simpleFontFieldRoundTrips() throws Exception {
        assertEquals(VALUE, extractedTextOf("text_field.pdf"),
                "the appearance should show exactly the field's value");
    }

    /**
     * The control. A composite font's codes really are two bytes, so the same text has to be written
     * the other way - otherwise "one-byte codes" would just be the new unconditional answer, and the
     * bug would have moved rather than gone.
     */
    @DisplayName("a field drawn with a composite font is still written in two-byte codes")
    @Test
    public void compositeFontFieldUsesTwoByteCodes() throws Exception {
        String contentStream = appearanceOf("text_field_composite.pdf");

        assertTrue(contentStream.contains("<00410062>"),
                "a composite font takes two bytes to a code:\n" + contentStream);
    }

    // -- helpers ---------------------------------------------------------------------------------

    /**
     * Regenerates the field's appearance and returns the content stream written for it.
     */
    private String appearanceOf(String fixture) throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/acroform/" + fixture).toString());
        try {
            return new String(appearanceForm(document).getDecodedStreamBytes(),
                    StandardCharsets.ISO_8859_1);
        } finally {
            document.dispose();
        }
    }

    private String extractedTextOf(String fixture) throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/acroform/" + fixture).toString());
        try {
            Form appearance = appearanceForm(document);
            appearance.init();
            StringBuilder text = new StringBuilder();
            for (LineText line : appearance.getShapes().getPageText().getPageLines()) {
                for (WordText word : line.getWords()) {
                    text.append(word.getText());
                }
            }
            return text.toString().trim();
        } finally {
            document.dispose();
        }
    }

    private Form appearanceForm(Document document) throws Exception {
        Page page = document.getPageTree().getPage(0);
        page.init();
        TextWidgetAnnotation widget = (TextWidgetAnnotation) page.getAnnotations().get(0);
        widget.resetAppearanceStream(new AffineTransform());
        return (Form) widget.getAppearanceStream();
    }
}
