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
package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.util.updater.callbacks.StringObjectWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Byte-level tests for the text a redaction writes back into a content stream.
 * <p>
 * No PDF, no parser, no page - {@link GlyphRunBuilder} supplies the glyph runs directly, so these
 * pin the writer's output exactly rather than inferring it from a rendered result.
 * <p>
 * The cases under "known defects" were written against the pre-fix writer and each was verified
 * to fail for the reason named in REDACTION-REVIEW-PLAN.md before the fix landed.
 */
public class RedactedStringObjectWriterTest {

    private final RedactedStringObjectWriter writer = new RedactedStringObjectWriter();

    @DisplayName("a run with nothing flagged is not rewritten")
    @Test
    public void noFlaggedGlyphsIsNotRewritten() {
        ArrayList<TextSprite> operators = GlyphRunBuilder.simpleFont(12f)
                .glyphs("Hello")
                .buildOperators();
        assertFalse(StringObjectWriter.containsFlaggedText(operators),
                "nothing is flagged, so the callback should copy the original bytes verbatim");
    }

    @DisplayName("a fully flagged run emits nothing")
    @Test
    public void fullyFlaggedRunEmitsNothing() throws Exception {
        ArrayList<TextSprite> operators = GlyphRunBuilder.simpleFont(12f)
                .glyphs("Secret")
                .flagAll()
                .buildOperators();
        assertEquals("", write(operators),
                "every glyph is redacted, so there is nothing left to show");
    }

    @DisplayName("a leading flagged run is dropped and the remainder repositioned")
    @Test
    public void leadingRunIsDropped() throws Exception {
        GlyphRunBuilder builder = GlyphRunBuilder.simpleFont(12f).glyphs("SECRETvisible").flag(0, 6);
        String out = write(builder.buildOperators());

        assertFalse(out.contains("SECRET"), "the flagged glyphs must not survive");
        assertTrue(out.contains("(visible)"), "the unflagged remainder should be shown: " + out);
        assertBalancedDelimiters(out);
    }

    @DisplayName("a trailing flagged run is dropped")
    @Test
    public void trailingRunIsDropped() throws Exception {
        GlyphRunBuilder builder = GlyphRunBuilder.simpleFont(12f).glyphs("visibleSECRET").flag(7, 13);
        String out = write(builder.buildOperators());

        assertFalse(out.contains("SECRET"), "the flagged glyphs must not survive");
        assertTrue(out.contains("(visible)"), "the unflagged head should be shown: " + out);
        assertBalancedDelimiters(out);
    }

    @DisplayName("a flagged run in the middle splits the string and repositions the tail")
    @Test
    public void middleRunSplitsTheString() throws Exception {
        GlyphRunBuilder builder = GlyphRunBuilder.simpleFont(12f).glyphs("abSECRETyz").flag(2, 8);
        String out = write(builder.buildOperators());

        assertFalse(out.contains("SECRET"), "the flagged glyphs must not survive");
        assertTrue(out.contains("(ab)"), "head should be shown: " + out);
        assertTrue(out.contains("(yz)"), "tail should be shown: " + out);
        assertBalancedDelimiters(out);
    }

    @DisplayName("alternating flags produce one string per surviving run")
    @Test
    public void alternatingFlagsSplitPerRun() throws Exception {
        GlyphRunBuilder builder = GlyphRunBuilder.simpleFont(12f)
                .glyphs("aXbXc").flag(1, 2).flag(3, 4);
        String out = write(builder.buildOperators());

        assertFalse(out.contains("X"), "flagged glyphs must not survive: " + out);
        assertBalancedDelimiters(out);
        assertEquals(3, RedactionFixtures.countOccurrences(out, "("),
                "three surviving runs should give three strings: " + out);
    }

    @DisplayName("the adjustment covers exactly the width of the removed glyphs")
    @Test
    public void adjustmentAccountsForRemovedGlyphs() throws Exception {
        float fontSize = 12f;
        GlyphRunBuilder builder = GlyphRunBuilder.simpleFont(fontSize).glyphs("abSECRETyz").flag(2, 8);
        float advance = builder.advance();
        String out = write(builder.buildOperators());

        // Six glyphs were removed, so the text position has to step over 6 * advance. A TJ element
        // is measured in thousandths of an em and subtracts, hence -1000 * gap / fontSize.
        float gap = 6 * advance;
        float expected = -1000f * gap / fontSize;
        assertTrue(containsNumberNear(out, expected, 0.01f),
                "expected an adjustment of " + expected + " for a gap of " + gap + ", got: " + out);
    }

    // -- byte-level encoding, each of these was a defect ------------------------------------------

    @DisplayName("a CID code needing three hex digits is padded to four")
    @Test
    public void cidCodeInThirdNibbleRangeIsPadded() throws Exception {
        ArrayList<TextSprite> operators = GlyphRunBuilder.cidFont(12f)
                .codes(0x0100, 0x0041)
                .buildOperators();
        String out = write(operators);
        assertTrue(out.contains("<01000041>"),
                "each CID code must occupy exactly four hex digits, got: " + out);
    }

    @DisplayName("codes 128-255 are written as three octal digits")
    @Test
    public void highCodesAreWrittenAsThreeOctalDigits() throws Exception {
        // Pinned because the review first claimed the opposite: codes in this range were said to
        // be written unpadded and to absorb a following digit. They cannot - 128-255 is exactly
        // three octal digits (200-377) - and the escape now pads unconditionally regardless.
        ArrayList<TextSprite> operators = GlyphRunBuilder.simpleFont(12f)
                .codes(0x80, '2', 0xFF)
                .buildOperators();
        String out = write(operators);
        assertTrue(out.contains("\\2002\\377"),
                "expected three-digit octal escapes around the literal '2', got: " + out);
    }

    @DisplayName("a carriage return code is escaped rather than written raw")
    @Test
    public void carriageReturnCodeIsEscaped() throws Exception {
        ArrayList<TextSprite> operators = GlyphRunBuilder.simpleFont(12f)
                .codes(0x0D, 'a')
                .buildOperators();
        String out = write(operators);
        assertFalse(out.contains("\r"),
                "a raw CR in a literal string is normalised to LF on read-back, so it must be " +
                        "escaped, got: " + out.replace("\r", "<CR>"));
    }

    @DisplayName("a tiny offset is not written in scientific notation")
    @Test
    public void tinyOffsetIsNotScientificNotation() throws Exception {
        ArrayList<TextSprite> operators = GlyphRunBuilder.simpleFont(1e-4f)
                .glyphs("aXb").flag(1, 2)
                .buildOperators();
        String out = write(operators);
        assertFalse(out.contains("E") || out.contains("e"),
                "PDF reals have no exponent form, got: " + out);
    }

    @DisplayName("a second sprite opens its own string")
    @Test
    public void secondSpriteOpensItsOwnString() throws Exception {
        ArrayList<TextSprite> operators = new ArrayList<>();
        operators.add(GlyphRunBuilder.simpleFont(12f).glyphs("aXb").flag(1, 2).build());
        operators.add(GlyphRunBuilder.simpleFont(12f).startingAt(100f).glyphs("cd").build());

        String out = write(operators);
        assertBalancedDelimiters(out);
        assertTrue(out.contains("(cd)"), "the second sprite should be a string of its own: " + out);
    }

    // -- helpers ---------------------------------------------------------------------------------

    private String write(ArrayList<TextSprite> operators) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.writeShownText(out, operators);
        return out.toString(StandardCharsets.ISO_8859_1.name());
    }

    private static void assertBalancedDelimiters(String out) {
        assertEquals(RedactionFixtures.countOccurrences(out, "("), RedactionFixtures.countOccurrences(out, ")"),
                "unbalanced literal string delimiters in: " + out);
        assertEquals(RedactionFixtures.countOccurrences(out, "<"), RedactionFixtures.countOccurrences(out, ">"),
                "unbalanced hex string delimiters in: " + out);
    }


    /**
     * True when the output carries a number within {@code tolerance} of {@code expected}. Kept
     * syntax-agnostic on purpose: these tests should survive the move from Td offsets to TJ
     * adjustments, which changes how the number is spelled but not which position it restores.
     */
    private static boolean containsNumberNear(String out, float expected, float tolerance) {
        for (String token : out.split("[^0-9eE.+-]+")) {
            if (token.isEmpty()) continue;
            try {
                if (Math.abs(Float.parseFloat(token) - expected) <= tolerance) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // not a number, keep looking
            }
        }
        return false;
    }
}
