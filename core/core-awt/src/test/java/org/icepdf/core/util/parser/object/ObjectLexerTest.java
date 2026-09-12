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
package org.icepdf.core.util.parser.object;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.HexStringObject;
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Token-level tests for the object {@link Lexer}, the reader that turns the bytes of a PDF body
 * into {@code COS}-style objects.
 * <p>
 * Every test drives the lexer over a literal byte string so the expected token is readable next to
 * the input that produced it.  The lexer is the bottom of the parsing stack - every dictionary,
 * every stream length and every cross-reference entry in a document comes through here - so the
 * cases below are deliberately the awkward ones: signed and malformed numbers, escapes inside
 * literal strings, and tokens that end on a delimiter rather than on whitespace.
 */
public class ObjectLexerTest {

    private static Lexer lexerFor(String body) {
        Lexer lexer = new Lexer(new Library());
        lexer.setByteBuffer(ByteBuffer.wrap(body.getBytes(StandardCharsets.ISO_8859_1)));
        return lexer;
    }

    private static Object firstToken(String body) throws IOException {
        return lexerFor(body).nextToken();
    }

    // ------------------------------------------------------------------
    // numbers
    // ------------------------------------------------------------------

    @DisplayName("numbers - integers, reals and both signs")
    @Test
    public void numbers() throws IOException {
        assertEquals(0, firstToken("0 "));
        assertEquals(42, firstToken("42 "));
        assertEquals(-17, ((Number) firstToken("-17 ")).intValue());
        assertEquals(17, ((Number) firstToken("+17 ")).intValue());
        assertEquals(3.5f, ((Number) firstToken("3.5 ")).floatValue(), 0.0001f);
        assertEquals(-0.25f, ((Number) firstToken("-.25 ")).floatValue(), 0.0001f);
    }

    @DisplayName("numbers - a doubled leading sign is tolerated, as Oracle Forms writes it")
    @Test
    public void doubleSignedNumber() throws IOException {
        // "--5" is malformed by the spec but appears in the wild; the lexer reads it as the -5 the
        // writer meant rather than failing the whole object.
        assertEquals(-5, ((Number) firstToken("--5 ")).intValue());
    }

    @DisplayName("numbers - an integer ending on a delimiter is still a whole integer")
    @Test
    public void numberEndingOnDelimiter() throws IOException {
        Lexer lexer = lexerFor("[12/Name]");
        List<?> array = (List<?>) lexer.nextToken();
        assertEquals(12, ((Number) array.get(0)).intValue());
        assertEquals(new Name("Name"), array.get(1));
    }

    // ------------------------------------------------------------------
    // names, booleans, null
    // ------------------------------------------------------------------

    @DisplayName("names - terminated by whitespace, by a delimiter and at end of buffer")
    @Test
    public void names() throws IOException {
        assertEquals(new Name("Type"), firstToken("/Type "));
        assertEquals(new Name("Type"), firstToken("/Type/Page"));
        assertEquals(new Name("Type"), firstToken("/Type"));
        // an empty name is legal
        assertEquals(new Name(""), firstToken("/ "));
    }

    @DisplayName("booleans and null")
    @Test
    public void booleansAndNull() throws IOException {
        assertEquals(Boolean.TRUE, firstToken("true "));
        assertEquals(Boolean.FALSE, firstToken("false "));
        assertEquals("null", firstToken("null "));
    }

    // ------------------------------------------------------------------
    // strings
    // ------------------------------------------------------------------

    @DisplayName("literal strings - plain text")
    @Test
    public void literalString() throws IOException {
        StringObject string = (StringObject) firstToken("(Hello World) ");
        assertInstanceOf(LiteralStringObject.class, string);
        assertEquals("Hello World", string.getLiteralString());
    }

    @DisplayName("literal strings - the named escape sequences")
    @Test
    public void literalStringEscapes() throws IOException {
        StringObject string = (StringObject) firstToken("(a\\nb\\rc\\td\\be\\ff) ");
        assertEquals("a\nb\rc\td\be\ff", string.getLiteralString());
    }

    @DisplayName("literal strings - escaped and balanced parentheses")
    @Test
    public void literalStringParentheses() throws IOException {
        assertEquals("a(b)c", ((StringObject) firstToken("(a\\(b\\)c) ")).getLiteralString());
        // balanced pairs need no escape at all
        assertEquals("a(b)c", ((StringObject) firstToken("(a(b)c) ")).getLiteralString());
    }

    @DisplayName("literal strings - a backslash escapes itself")
    @Test
    public void literalStringBackslash() throws IOException {
        assertEquals("a\\b", ((StringObject) firstToken("(a\\\\b) ")).getLiteralString());
    }

    @DisplayName("literal strings - octal escapes of one, two and three digits")
    @Test
    public void literalStringOctal() throws IOException {
        assertEquals("A", ((StringObject) firstToken("(\\101) ")).getLiteralString());
        // \053 is '+', and the trailing 3 is a literal digit: the spec caps an octal at 3 digits.
        assertEquals("+3", ((StringObject) firstToken("(\\0533) ")).getLiteralString());
    }

    @DisplayName("literal strings - a UTF-16BE byte order mark switches to two-byte reads")
    @Test
    public void literalStringUtf16() throws IOException {
        // FF FE is the mark this lexer keys on, and it is matched against the raw bytes, so it has
        // to be written as bytes and not as the octal escapes that would spell the same characters.
        StringObject string = (StringObject) firstToken("(\u00ff\u00fe\u0000H\u0000i) ");
        assertEquals("Hi", string.getLiteralString());
    }

    @DisplayName("hex strings - decoded to the bytes they spell")
    @Test
    public void hexString() throws IOException {
        StringObject string = (StringObject) firstToken("<48656C6C6F> ");
        assertInstanceOf(HexStringObject.class, string);
        assertEquals("Hello", string.getLiteralString());
    }

    @DisplayName("hex strings - an empty one yields an empty string")
    @Test
    public void emptyHexString() throws IOException {
        assertEquals("", ((StringObject) firstToken("<> ")).getLiteralString());
    }

    @DisplayName("strings - read under a reference still decode to their own bytes")
    @Test
    public void stringsReadUnderAReference() throws IOException {
        // An encrypted document keys each string's cipher on the object that contains it, so the
        // lexer is handed the reference as it reads.  With no security manager in play the string
        // is unchanged by it, which is what an unencrypted document must see.
        Reference reference = new Reference(7, 0);
        assertEquals("secret",
                ((StringObject) lexerFor("(secret) ").nextToken(reference)).getLiteralString());
        assertEquals("ABC",
                ((StringObject) lexerFor("<414243> ").nextToken(reference)).getLiteralString());
    }

    // ------------------------------------------------------------------
    // composite objects
    // ------------------------------------------------------------------

    @DisplayName("arrays - mixed members, nesting and the empty array")
    @Test
    public void arrays() throws IOException {
        List<?> array = (List<?>) firstToken("[1 2.5 /Name (text) true] ");
        assertEquals(5, array.size());
        assertEquals(1, ((Number) array.get(0)).intValue());
        assertEquals(2.5f, ((Number) array.get(1)).floatValue(), 0.0001f);
        assertEquals(new Name("Name"), array.get(2));
        assertEquals("text", ((StringObject) array.get(3)).getLiteralString());
        assertEquals(Boolean.TRUE, array.get(4));

        assertTrue(((List<?>) firstToken("[] ")).isEmpty());

        List<?> nested = (List<?>) firstToken("[[1 2] [3 4]] ");
        assertEquals(2, nested.size());
        assertEquals(2, ((List<?>) nested.get(0)).size());
    }

    @DisplayName("arrays - of indirect references")
    @Test
    public void arrayOfReferences() throws IOException {
        List<?> array = (List<?>) firstToken("[3 0 R 4 0 R] ");
        assertEquals(2, array.size());
        assertEquals(new Reference(3, 0), array.get(0));
        assertEquals(new Reference(4, 0), array.get(1));
    }

    @DisplayName("references - an object number, a generation and R")
    @Test
    public void reference() throws IOException {
        assertEquals(new Reference(12, 0), firstToken("12 0 R "));
        assertEquals(new Reference(12, 3), firstToken("12 3 R "));
        // two numbers that are not followed by R stay two numbers
        Lexer lexer = lexerFor("12 3 ");
        assertEquals(12, ((Number) lexer.nextToken()).intValue());
        assertEquals(3, ((Number) lexer.nextToken()).intValue());
    }

    @DisplayName("dictionaries - keys, values and nesting")
    @Test
    public void dictionary() throws IOException {
        Object token = firstToken("<< /Type /Page /MediaBox [0 0 612 792] /Rotate 90 >> ");
        DictionaryEntries entries = entriesOf(token);
        assertEquals(new Name("Page"), entries.get(new Name("Type")));
        assertEquals(4, ((List<?>) entries.get(new Name("MediaBox"))).size());
        assertEquals(90, ((Number) entries.get(new Name("Rotate"))).intValue());
    }

    @DisplayName("dictionaries - an empty one has no entries")
    @Test
    public void emptyDictionary() throws IOException {
        assertTrue(entriesOf(firstToken("<<>> ")).isEmpty());
    }

    @DisplayName("dictionaries - a nested dictionary value is read whole")
    @Test
    public void nestedDictionary() throws IOException {
        DictionaryEntries entries = entriesOf(firstToken("<< /A << /B 2 >> /C 3 >> "));
        assertEquals(2, ((Number) entriesOf(entries.get(new Name("A"))).get(new Name("B"))).intValue());
        assertEquals(3, ((Number) entries.get(new Name("C"))).intValue());
    }

    // ------------------------------------------------------------------
    // comments, operands, exhaustion
    // ------------------------------------------------------------------

    @DisplayName("comments - skipped, and the token after one is returned instead")
    @Test
    public void comment() throws IOException {
        assertEquals(new Name("Type"), firstToken("% a comment\n/Type "));
    }

    @DisplayName("operands - obj, endobj and stream are recognised keywords")
    @Test
    public void operands() throws IOException {
        assertEquals(OperandNames.OP_obj, firstToken("obj "));
        assertEquals(OperandNames.OP_endobj, firstToken("endobj "));
        assertEquals(OperandNames.OP_stream, firstToken("stream\n"));
        assertEquals(OperandNames.OP_endstream, firstToken("endstream "));
    }

    @DisplayName("operands - the single letter keywords of a cross-reference table")
    @Test
    public void crossReferenceKeywords() throws IOException {
        // An xref subsection line is "offset generation n" for an in-use entry, "f" for a free one.
        Lexer lexer = lexerFor("0000000017 00000 n \n0000000000 65535 f \n");
        assertEquals(17, ((Number) lexer.nextToken()).intValue());
        assertEquals(0, ((Number) lexer.nextToken()).intValue());
        assertEquals(OperandNames.OP_n, lexer.nextToken());
        assertEquals(0, ((Number) lexer.nextToken()).intValue());
        assertEquals(65535, ((Number) lexer.nextToken()).intValue());
        assertEquals(OperandNames.OP_f, lexer.nextToken());
    }

    @DisplayName("exhaustion - a spent buffer returns null rather than looping")
    @Test
    public void exhaustion() throws IOException {
        Lexer lexer = lexerFor("/Only ");
        assertEquals(new Name("Only"), lexer.nextToken());
        assertNull(lexer.nextToken());
        assertNull(lexer.nextToken());
    }

    @DisplayName("exhaustion - whitespace alone yields nothing")
    @Test
    public void whitespaceOnly() throws IOException {
        assertNull(firstToken("   \r\n\t  "));
    }

    @DisplayName("no buffer - a lexer that was never given bytes fails loudly")
    @Test
    public void noByteBuffer() {
        Lexer lexer = new Lexer(new Library());
        assertThrows(IOException.class, lexer::nextToken);
    }

    @DisplayName("whitespace - a token run together with the next is still split correctly")
    @Test
    public void tokensWithoutSeparatingWhitespace() throws IOException {
        // Real writers omit whitespace wherever a delimiter already separates two tokens.
        Lexer lexer = lexerFor("/Key(value)/Next");
        assertEquals(new Name("Key"), lexer.nextToken());
        assertEquals("value", ((StringObject) lexer.nextToken()).getLiteralString());
        assertEquals(new Name("Next"), lexer.nextToken());
    }

    @DisplayName("skipWhiteSpace - advances past leading blanks only")
    @Test
    public void skipWhiteSpace() throws IOException {
        Lexer lexer = lexerFor("\r\n\t   /Type ");
        lexer.skipWhiteSpace();
        assertEquals(new Name("Type"), lexer.nextToken());
    }

    /**
     * A dictionary token comes back as whatever {@link ObjectFactory} made of the entries, so the
     * entries themselves are what a test can compare.
     */
    private static DictionaryEntries entriesOf(Object token) {
        assertNotNull(token, "expected a dictionary token");
        if (token instanceof DictionaryEntries) {
            return (DictionaryEntries) token;
        }
        if (token instanceof org.icepdf.core.pobjects.Dictionary) {
            return ((org.icepdf.core.pobjects.Dictionary) token).getEntries();
        }
        assertFalse(true, "unexpected dictionary token type " + token.getClass());
        return null;
    }
}
