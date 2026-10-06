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
package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.HexStringObject;
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.parser.object.Lexer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.geom.AffineTransform;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the serialisation of each PDF value type, by writing one and reading the bytes back.
 * <p>
 * {@code BaseWriter.writeValue} is the one place every value in a saved document passes through, so
 * a fault here is a fault in every file the library writes.  The assertions are on the literal bytes
 * because the syntax is the contract - a name that fails to escape a space, or a string that fails
 * to escape a bracket, produces a file that other readers reject even though this one round-trips it
 * happily.  The round trip through the object lexer is asserted as well, to catch the reverse
 * mistake: an escaping rule the writer and the parser agree on but the specification does not.
 */
public class ObjectWriterTest {

    private static final Reference REFERENCE = new Reference(1, 0);

    /**
     * Serialises one value and returns the bytes written.
     */
    private static String write(Object value) throws IOException {
        BaseWriter writer = new BaseWriter();
        writer.initializeWriters();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CountingOutputStream output = new CountingOutputStream(bytes);
        writer.writeValue(new PObject(value, REFERENCE), output);
        output.flush();
        return bytes.toString(StandardCharsets.ISO_8859_1.name());
    }

    /**
     * Reads a serialised value back with the object lexer, so the writer is checked against the
     * parser that has to consume it.
     */
    private static Object reparse(String written) throws IOException {
        Lexer lexer = new Lexer(new Library());
        lexer.setByteBuffer(ByteBuffer.wrap((written + " ").getBytes(StandardCharsets.ISO_8859_1)));
        return lexer.nextToken();
    }

    // ------------------------------------------------------------------
    // scalars
    // ------------------------------------------------------------------

    @DisplayName("null - a null value is written as the null keyword")
    @Test
    public void nullValue() throws IOException {
        assertEquals("null", write(null).trim());
    }

    @DisplayName("booleans")
    @Test
    public void booleans() throws IOException {
        assertEquals("true", write(Boolean.TRUE).trim());
        assertEquals("false", write(Boolean.FALSE).trim());
    }

    @DisplayName("numbers - integers, longs and reals")
    @Test
    public void numbers() throws IOException {
        assertEquals("42", write(42).trim());
        assertEquals("-7", write(-7).trim());
        assertEquals("0", write(0).trim());
        assertEquals("9999999999", write(9_999_999_999L).trim());
        assertTrue(write(2.5f).trim().startsWith("2.5"), write(2.5f));
        assertTrue(write(-0.25d).trim().startsWith("-0.25"), write(-0.25d));
    }

    @DisplayName("names - written with a leading slash")
    @Test
    public void names() throws IOException {
        assertEquals("/Type", write(new Name("Type")));
        assertEquals(new Name("Type"), reparse(write(new Name("Type"))));
    }

    @DisplayName("names - characters outside the printable range are hex escaped")
    @Test
    public void namesAreEscaped() throws IOException {
        // A raw space or delimiter inside a name would end the token early for any other reader.
        assertEquals("/With#20Space", write(new Name("With Space")));
        assertEquals("/Paren#28", write(new Name("Paren(")));
        assertEquals("/Slash#2F", write(new Name("Slash/")));
        assertEquals("/Bracket#5B#5D", write(new Name("Bracket[]")));
        assertEquals("/Percent#25", write(new Name("Percent%")));
        // the escape's own hex digits have to be right above nine as well as below it
        assertEquals("/Tilde#7F", write(new Name("Tilde\u007f")));
        // A name arrives already decoded - the constructor reads "#23" as the "#" it stands for -
        // so a name holding a literal hash is written back with the escape it came in with.
        assertEquals("Hash#Mark", new Name("Hash#23Mark").getName());
        assertEquals("/Hash#23Mark", write(new Name("Hash#23Mark")));
    }

    @DisplayName("names - an escaped name reads back as the name it started as")
    @Test
    public void nameRoundTrip() throws IOException {
        for (String name : new String[]{"Plain", "With Space", "Paren(", "Slash/", "Bracket[]"}) {
            assertEquals(new Name(name), reparse(write(new Name(name))),
                    "round trip failed for [" + name + "]");
        }
    }

    @DisplayName("references - object number, generation and R")
    @Test
    public void references() throws IOException {
        assertEquals(new Reference(12, 3), reparse(write(new Reference(12, 3))));
    }

    // ------------------------------------------------------------------
    // strings
    // ------------------------------------------------------------------

    @DisplayName("literal strings - wrapped in parentheses")
    @Test
    public void literalStrings() throws IOException {
        assertEquals("(Hello)", write(new LiteralStringObject("Hello")));
    }

    @DisplayName("literal strings - brackets and backslashes are escaped")
    @Test
    public void literalStringEscaping() throws IOException {
        // Unescaped, the closing bracket would end the string three characters early.
        assertEquals("(a\\(b\\)c)", write(new LiteralStringObject("a(b)c")));
        assertEquals("(back\\\\slash)", write(new LiteralStringObject("back\\slash")));
    }

    @DisplayName("literal strings - survive a round trip through the parser")
    @Test
    public void literalStringRoundTrip() throws IOException {
        for (String text : new String[]{"plain", "a(b)c", "back\\slash", ""}) {
            Object reparsed = reparse(write(new LiteralStringObject(text)));
            assertEquals(text, ((org.icepdf.core.pobjects.StringObject) reparsed).getLiteralString(),
                    "round trip failed for [" + text + "]");
        }
    }

    @DisplayName("hex strings - wrapped in angle brackets")
    @Test
    public void hexStrings() throws IOException {
        String written = write(HexStringObject.createHexString("Hi"));
        assertTrue(written.startsWith("<") && written.endsWith(">"), written);
        assertEquals("Hi", ((org.icepdf.core.pobjects.StringObject) reparse(written)).getLiteralString());
    }

    @DisplayName("strings - a plain String value is written as a literal string")
    @Test
    public void plainStringValue() throws IOException {
        assertEquals("(text)", write("text"));
    }

    @DisplayName("strings - the parser's null placeholder is written back as the null keyword")
    @Test
    public void nullPlaceholder() throws IOException {
        // The object lexer returns the string "null" for a null object; writing it as a literal
        // string would turn a null into the four-character text "null" on every save.
        assertEquals("null", write("null").trim());
    }

    // ------------------------------------------------------------------
    // composites
    // ------------------------------------------------------------------

    @DisplayName("arrays - members separated by a single space")
    @Test
    public void arrays() throws IOException {
        assertEquals("[1 2 3]", write(Arrays.asList(1, 2, 3)));
        assertEquals("[]", write(new ArrayList<>()));
        assertEquals("[/A (b) 3]",
                write(Arrays.asList(new Name("A"), new LiteralStringObject("b"), 3)));
    }

    @DisplayName("arrays - nest, and survive a round trip")
    @Test
    public void nestedArrays() throws IOException {
        String written = write(Arrays.asList(Arrays.asList(1, 2), Arrays.asList(3, 4)));
        assertEquals("[[1 2] [3 4]]", written);
        List<?> reparsed = (List<?>) reparse(written);
        assertEquals(2, reparsed.size());
    }

    @DisplayName("dictionaries - written with the double angle brackets")
    @Test
    public void dictionaries() throws IOException {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Type"), new Name("Page"));
        String written = write(entries);
        assertTrue(written.startsWith("<<") && written.endsWith(">>"), written);
        assertTrue(written.contains("/Type"), written);
        assertTrue(written.contains("/Page"), written);
    }

    @DisplayName("dictionaries - an empty one is still well formed")
    @Test
    public void emptyDictionary() throws IOException {
        String written = write(new DictionaryEntries());
        assertTrue(written.startsWith("<<") && written.endsWith(">>"), written);
    }

    @DisplayName("dictionaries - survive a round trip with their values intact")
    @Test
    public void dictionaryRoundTrip() throws IOException {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Count"), 3);
        entries.put(new Name("Kids"), Arrays.asList(new Reference(4, 0), new Reference(5, 0)));
        entries.put(new Name("Title"), new LiteralStringObject("a (title)"));

        Object reparsed = reparse(write(entries));
        DictionaryEntries back = reparsed instanceof DictionaryEntries
                ? (DictionaryEntries) reparsed
                : ((org.icepdf.core.pobjects.Dictionary) reparsed).getEntries();

        assertEquals(3, ((Number) back.get(new Name("Count"))).intValue());
        assertEquals(2, ((List<?>) back.get(new Name("Kids"))).size());
        assertEquals("a (title)",
                ((org.icepdf.core.pobjects.StringObject) back.get(new Name("Title"))).getLiteralString());
    }

    @DisplayName("transforms - written as the six-number matrix array")
    @Test
    public void affineTransforms() throws IOException {
        // A transform is written positionally, and a reader has no way to detect a wrong order:
        // the six numbers are [a b c d e f], which is what AffineTransform(float[]) reads back.
        String written = write(new AffineTransform(1, 2, 3, 4, 5, 6));
        List<?> matrix = (List<?>) reparse(written);
        assertEquals(6, matrix.size());
        assertEquals(1f, ((Number) matrix.get(0)).floatValue(), 0.0001f);
        assertEquals(2f, ((Number) matrix.get(1)).floatValue(), 0.0001f);
        assertEquals(3f, ((Number) matrix.get(2)).floatValue(), 0.0001f);
        assertEquals(4f, ((Number) matrix.get(3)).floatValue(), 0.0001f);
        assertEquals(5f, ((Number) matrix.get(4)).floatValue(), 0.0001f);
        assertEquals(6f, ((Number) matrix.get(5)).floatValue(), 0.0001f);
    }

    @DisplayName("transforms - a fractional matrix keeps its fractions")
    @Test
    public void fractionalTransform() throws IOException {
        // Rounding a scale of a half to a whole number scales everything the matrix places by two.
        List<?> matrix = (List<?>) reparse(write(AffineTransform.getScaleInstance(0.5, 0.25)));
        assertEquals(0.5f, ((Number) matrix.get(0)).floatValue(), 0.0001f);
        assertEquals(0.25f, ((Number) matrix.get(3)).floatValue(), 0.0001f);
    }

    @DisplayName("transforms - a written matrix reads back as the same transform")
    @Test
    public void transformRoundTrip() throws IOException {
        AffineTransform original = new AffineTransform(2, 0.5, -0.5, 2, 72, 720);
        List<?> matrix = (List<?>) reparse(write(original));
        float[] values = new float[6];
        for (int i = 0; i < 6; i++) {
            values[i] = ((Number) matrix.get(i)).floatValue();
        }
        assertEquals(original, new AffineTransform(values));
    }

    // ------------------------------------------------------------------
    // the unwritable
    // ------------------------------------------------------------------

    @DisplayName("a value of a type the writer does not know is refused, not written wrong")
    @Test
    public void unknownValueType() {
        // Silently skipping it would leave a dictionary entry with no value, which corrupts every
        // byte offset after it.
        assertThrows(IllegalArgumentException.class, () -> write(new java.util.Date()));
    }
}
