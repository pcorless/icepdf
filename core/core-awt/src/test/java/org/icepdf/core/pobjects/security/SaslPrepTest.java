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
package org.icepdf.core.pobjects.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests SASLprep (RFC 4013), the preparation a revision 6 password goes through before it is hashed.
 * <p>
 * The writer and the reader have to arrive at the same bytes from the same typed password, so the
 * mapping has to be exact: a password holding a non-breaking space, a soft hyphen, or a character
 * with more than one Unicode spelling must reduce to a single canonical form.  When it does not,
 * the correct password is rejected and the document cannot be opened at all.  The cases below are
 * the ones RFC 4013 gives as its examples.
 * <p>
 * Characters are written as escapes throughout, because the point of most of them is the code
 * point rather than the glyph, and several are invisible.
 */
public class SaslPrepTest {

    // ------------------------------------------------------------------
    // the RFC 4013 examples
    // ------------------------------------------------------------------

    @DisplayName("a soft hyphen is mapped to nothing")
    @Test
    public void softHyphenIsRemoved() {
        // RFC 4013: I<U+00AD>X becomes IX
        assertEquals("IX", SaslPrep.saslPrepQuery("I\u00adX"));
    }

    @DisplayName("an ASCII password is left exactly as it is")
    @Test
    public void asciiIsUnchanged() {
        assertEquals("user", SaslPrep.saslPrepQuery("user"));
        assertEquals("USER", SaslPrep.saslPrepQuery("USER"));
        assertEquals("", SaslPrep.saslPrepQuery(""));
    }

    @DisplayName("compatibility characters are normalised to their canonical form")
    @Test
    public void normalisation() {
        // RFC 4013: the feminine ordinal (U+00AA) normalises to "a", and the Roman numeral nine
        // (U+2168) to "IX"
        assertEquals("a", SaslPrep.saslPrepQuery("\u00aa"));
        assertEquals("IX", SaslPrep.saslPrepQuery("\u2168"));
    }

    @DisplayName("a non-ASCII space is mapped to an ordinary space")
    @Test
    public void nonAsciiSpaceIsMapped() {
        // A password typed with a non-breaking space has to hash as if it held a plain one.
        assertEquals("a b", SaslPrep.saslPrepQuery("a\u00a0b"));
        assertEquals("a b", SaslPrep.saslPrepQuery("a\u2000b"));
        assertEquals("a b", SaslPrep.saslPrepQuery("a\u3000b"));
    }

    @DisplayName("an accented character reduces to one spelling however it was typed")
    @Test
    public void composedAndDecomposed() {
        // e-acute as one character (U+00E9), and as an e followed by a combining acute (U+0301),
        // have to agree or the same password typed on two keyboards hashes differently.
        assertEquals(SaslPrep.saslPrepQuery("\u00e9"), SaslPrep.saslPrepQuery("e\u0301"));
    }

    // ------------------------------------------------------------------
    // prohibited input
    // ------------------------------------------------------------------

    @DisplayName("a control character is refused")
    @Test
    public void prohibitedControl() {
        // RFC 4013: <U+0007> is prohibited
        assertThrows(IllegalArgumentException.class, () -> SaslPrep.saslPrepQuery("\u0007"));
    }

    @DisplayName("the prohibited classes are recognised by code point")
    @Test
    public void prohibitedCodePoints() {
        assertTrue(SaslPrep.prohibited(0x0007), "ASCII control");
        assertTrue(SaslPrep.prohibited(0x00A0), "non-ASCII space");
        assertTrue(SaslPrep.prohibited(0x0080), "non-ASCII control");
        assertTrue(SaslPrep.prohibited(0xE000), "private use");
        assertTrue(SaslPrep.prohibited(0xFFFE), "non-character");
        assertTrue(SaslPrep.prohibited(0xD800), "surrogate");
        assertTrue(SaslPrep.prohibited(0x2FF0), "inappropriate for plain text");
        assertTrue(SaslPrep.prohibited(0xE0001), "tagging");

        assertFalse(SaslPrep.prohibited('a'));
        assertFalse(SaslPrep.prohibited(' '), "an ordinary space is allowed");
        assertFalse(SaslPrep.prohibited(0x00E9), "an accented letter is allowed");
    }

    // ------------------------------------------------------------------
    // bidirectional text
    // ------------------------------------------------------------------

    @DisplayName("mixing right-to-left and left-to-right text is refused")
    @Test
    public void mixedDirectionality() {
        // RFC 4013 requirement 2: <U+0627><U+0031> is prohibited
        assertThrows(IllegalArgumentException.class,
                () -> SaslPrep.saslPrepQuery("\u0627" + "1"));
    }

    @DisplayName("right-to-left text that starts and ends right-to-left is allowed")
    @Test
    public void wellFormedRightToLeft() {
        // RFC 4013 requirement 3: <U+0627><U+0031><U+0628> is allowed
        String text = "\u0627" + "1" + "\u0628";
        assertEquals(text, SaslPrep.saslPrepQuery(text));
    }

    // ------------------------------------------------------------------
    // query and stored profiles
    // ------------------------------------------------------------------

    @DisplayName("both profiles agree on ordinary text")
    @Test
    public void profilesAgree() {
        assertEquals(SaslPrep.saslPrepQuery("password"), SaslPrep.saslPrepStored("password"));
        assertEquals("IX", SaslPrep.saslPrepStored("I\u00adX"));
    }
}
