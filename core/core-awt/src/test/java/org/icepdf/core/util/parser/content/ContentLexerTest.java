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
package org.icepdf.core.util.parser.content;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Token-level tests for the content-stream {@link Lexer}.
 * <p>
 * Content streams are read far more often than the document body and by a different lexer, with
 * its own number parser and its own operator table.  Operators come back as {@link Operands} codes,
 * and the table that produces them keys on length as well as on the leading characters, so an
 * operator sharing a prefix with a longer one ({@code T*} against {@code TJ}, {@code f} against
 * {@code f*}) is exactly where a mis-read would happen.
 */
public class ContentLexerTest {

    /**
     * A lexer positioned at the start of the given content stream.
     */
    private static Lexer lexerFor(String content) throws IOException {
        Lexer lexer = new Lexer();
        lexer.setContentStream(streamsOf(content), null);
        return lexer;
    }

    private static Stream[] streamsOf(String content) {
        return Stream.fromByteArray(content.getBytes(StandardCharsets.ISO_8859_1),
                new Dictionary(new Library(), new DictionaryEntries()));
    }

    private static Stream streamOf(String content) {
        return streamsOf(content)[0];
    }

    /**
     * Every token in the stream, read until exhaustion.
     */
    private static List<Object> tokens(String content) throws IOException {
        Lexer lexer = lexerFor(content);
        List<Object> tokens = new ArrayList<>();
        Object token;
        while ((token = lexer.next()) != null) {
            tokens.add(token);
        }
        return tokens;
    }

    // ------------------------------------------------------------------
    // operators
    // ------------------------------------------------------------------

    @DisplayName("operators - path construction and painting")
    @Test
    public void pathOperators() throws IOException {
        assertEquals(List.of(0f, 0f, Operands.m,
                        10f, 10f, Operands.l,
                        Operands.h,
                        Operands.S),
                tokens("0 0 m 10 10 l h S"));
    }

    @DisplayName("operators - the starred forms are not confused with their plain ones")
    @Test
    public void starredOperators() throws IOException {
        assertEquals(List.of(Operands.f), tokens("f"));
        assertEquals(List.of(Operands.f_STAR), tokens("f*"));
        assertEquals(List.of(Operands.B), tokens("B"));
        assertEquals(List.of(Operands.B_STAR), tokens("B*"));
        assertEquals(List.of(Operands.W), tokens("W"));
        assertEquals(List.of(Operands.W_STAR), tokens("W*"));
        assertEquals(List.of(Operands.T_STAR), tokens("T*"));
    }

    @DisplayName("operators - case distinguishes stroking from non-stroking colour")
    @Test
    public void colourOperators() throws IOException {
        // Upper case is the stroking colour, lower case the fill; swapping them is a silent
        // rendering fault, so the two have to lex apart.
        assertEquals(List.of(0f, Operands.G), tokens("0 G"));
        assertEquals(List.of(0f, Operands.g), tokens("0 g"));
        assertEquals(List.of(1f, 0f, 0f, Operands.RG), tokens("1 0 0 RG"));
        assertEquals(List.of(1f, 0f, 0f, Operands.rg), tokens("1 0 0 rg"));
        assertEquals(List.of(0f, 0f, 0f, 1f, Operands.K), tokens("0 0 0 1 K"));
        assertEquals(List.of(0f, 0f, 0f, 1f, Operands.k), tokens("0 0 0 1 k"));
        assertEquals(List.of(Operands.SCN), tokens("SCN"));
        assertEquals(List.of(Operands.scn), tokens("scn"));
    }

    @DisplayName("operators - the graphics state stack and the text block")
    @Test
    public void stateOperators() throws IOException {
        assertEquals(List.of(Operands.q, Operands.Q), tokens("q Q"));
        assertEquals(List.of(Operands.BT, Operands.ET), tokens("BT ET"));
        assertEquals(List.of(1f, 0f, 0f, 1f, 72f, 720f, Operands.cm),
                tokens("1 0 0 1 72 720 cm"));
    }

    @DisplayName("operators - an unknown operator becomes the no-op code, not a parse failure")
    @Test
    public void unknownOperator() throws IOException {
        // Producers emit private or mistyped operators; one must not cost the rest of the stream.
        assertEquals(List.of(Operands.q, Operands.OP, Operands.Q), tokens("q zz Q"));
    }

    // ------------------------------------------------------------------
    // operands
    // ------------------------------------------------------------------

    @DisplayName("numbers - signs, reals and a leading dot")
    @Test
    public void numbers() throws IOException {
        assertEquals(List.of(1f, -1f, 0.5f, -0.5f, 1f, Operands.m),
                tokens("1 -1 .5 -.5 +1 m"));
    }

    @DisplayName("names - an operand name, as fed to Tf, gs and Do")
    @Test
    public void names() throws IOException {
        assertEquals(List.of(new Name("F1"), 12f, Operands.Tf), tokens("/F1 12 Tf"));
        assertEquals(List.of(new Name("GS0"), Operands.gs), tokens("/GS0 gs"));
        assertEquals(List.of(new Name("Im1"), Operands.Do), tokens("/Im1 Do"));
    }

    @DisplayName("strings - a literal string operand of Tj")
    @Test
    public void literalString() throws IOException {
        List<Object> tokens = tokens("(Hello) Tj");
        assertEquals("Hello", ((StringObject) tokens.get(0)).getLiteralString());
        assertEquals(Operands.Tj, tokens.get(1));
    }

    @DisplayName("strings - a hex string operand of Tj")
    @Test
    public void hexString() throws IOException {
        List<Object> tokens = tokens("<48656C6C6F> Tj");
        assertEquals("Hello", ((StringObject) tokens.get(0)).getLiteralString());
        assertEquals(Operands.Tj, tokens.get(1));
    }

    @DisplayName("arrays - a TJ array mixes strings with kerning adjustments")
    @Test
    public void textArray() throws IOException {
        List<Object> tokens = tokens("[(A) -250 (B)] TJ");
        List<?> array = assertInstanceOf(List.class, tokens.get(0));
        assertEquals(3, array.size());
        assertEquals("A", ((StringObject) array.get(0)).getLiteralString());
        assertEquals(-250f, ((Number) array.get(1)).floatValue(), 0.0001f);
        assertEquals("B", ((StringObject) array.get(2)).getLiteralString());
        assertEquals(Operands.TJ, tokens.get(1));
    }

    @DisplayName("arrays - the dash pattern of d")
    @Test
    public void dashArray() throws IOException {
        List<Object> tokens = tokens("[3 2] 0 d");
        assertEquals(2, ((List<?>) tokens.get(0)).size());
        assertEquals(0f, ((Number) tokens.get(1)).floatValue(), 0.0001f);
        assertEquals(Operands.d, tokens.get(2));
    }

    @DisplayName("dictionaries - the marked content property list of BDC")
    @Test
    public void dictionaryOperand() throws IOException {
        List<Object> tokens = tokens("/OC << /MCID 0 >> BDC");
        assertEquals(new Name("OC"), tokens.get(0));
        assertInstanceOf(DictionaryEntries.class, tokens.get(1));
        assertEquals(Operands.BDC, tokens.get(2));
    }

    @DisplayName("booleans - read as booleans and not as operators")
    @Test
    public void booleans() throws IOException {
        List<Object> tokens = tokens("true false");
        assertEquals(Boolean.TRUE, tokens.get(0));
        assertEquals(Boolean.FALSE, tokens.get(1));
    }

    @DisplayName("comments - reported as the no-op operand, and the operator after one survives")
    @Test
    public void comment() throws IOException {
        // Unlike the object lexer, this one does not swallow a comment; it hands back the generic
        // OP code, which the content parser treats as a no-op.  What matters is that the operator
        // on the far side of the comment is still read.
        assertEquals(List.of(Operands.q, Operands.OP, Operands.Q), tokens("q % a comment\nQ"));
    }

    // ------------------------------------------------------------------
    // inline images and multiple streams
    // ------------------------------------------------------------------

    @DisplayName("inline image - the bytes between ID and EI come back untouched")
    @Test
    public void inlineImage() throws IOException {
        // The sample data is not content-stream syntax and must be handed back exactly, so the
        // lexer has to stop looking for tokens between ID and EI.
        Lexer lexer = lexerFor("BI /W 2 /H 2 /BPC 8 /CS /G ID ABCD EI Q");
        assertEquals(Operands.BI, lexer.next());
        Object token = lexer.next();
        while (!Integer.valueOf(Operands.ID).equals(token)) {
            token = lexer.next();
        }
        byte[] imageBytes = lexer.getImageBytes();
        assertEquals("ABCD", new String(imageBytes, StandardCharsets.ISO_8859_1));
        // and the stream carries on after the EI
        assertEquals(Operands.Q, lexer.next());
    }

    @DisplayName("multiple streams - a page's content array is read as one continuous stream")
    @Test
    public void multipleStreams() throws IOException {
        // A page may split its content over several streams, and an operator's operands can fall
        // either side of the boundary; the lexer has to join them rather than restart.
        Lexer lexer = new Lexer();
        lexer.setContentStream(new Stream[]{streamOf("q 1 0 0 1 5 5"), streamOf(" cm Q")}, null);
        List<Object> tokens = new ArrayList<>();
        Object token;
        while ((token = lexer.next()) != null) {
            tokens.add(token);
        }
        assertEquals(Operands.q, tokens.get(0));
        assertEquals(Operands.cm, tokens.get(tokens.size() - 2));
        assertEquals(Operands.Q, tokens.get(tokens.size() - 1));
    }

    @DisplayName("multiple streams - a null entry in the content array is skipped, not dereferenced")
    @Test
    public void nullStreamEntry() throws IOException {
        // An unresolved content stream object leaves a null in the array; the page still has to
        // render whatever else it has.
        Lexer lexer = new Lexer();
        lexer.setContentStream(new Stream[]{null, streamOf("q Q")}, null);
        assertEquals(Operands.q, lexer.next());
        assertEquals(Operands.Q, lexer.next());
        assertNull(lexer.next());
    }

    @DisplayName("multiple streams - an array of nothing but nulls leaves the lexer with no bytes")
    @Test
    public void onlyNullStreams() throws IOException {
        Lexer lexer = new Lexer();
        lexer.setContentStream(new Stream[]{null, null}, null);
        assertThrows(IOException.class, lexer::next);
    }

    @DisplayName("no content stream - a lexer that was never given one fails loudly")
    @Test
    public void noContentStream() {
        assertThrows(IOException.class, () -> new Lexer().next());
    }

    @DisplayName("position - getPos advances as tokens are consumed")
    @Test
    public void position() throws IOException {
        Lexer lexer = lexerFor("q Q");
        int start = lexer.getPos();
        lexer.next();
        assertTrue(lexer.getPos() > start, "reading a token should advance the position");
    }
}
