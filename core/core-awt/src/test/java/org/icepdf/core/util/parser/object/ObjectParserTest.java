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

import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link Parser} and {@link ObjectFactory}: reading one indirect object out of a byte
 * buffer, and deciding what Java type the dictionary it read should become.
 * <p>
 * The factory's dispatch is the interesting half.  It keys off {@code /Type} and {@code /Subtype},
 * and a mis-dispatch is invisible at parse time - the object loads, and only fails much later when
 * something asks it to behave like the class it should have been.  Each dispatch branch is
 * therefore asserted against the smallest dictionary that selects it.
 */
public class ObjectParserTest {

    private static ByteBuffer bufferOf(String body) {
        return ByteBuffer.wrap(body.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static PObject parse(String body) throws IOException, ObjectStateException {
        return new Parser(new Library()).getPObject(bufferOf(body), 0);
    }

    // ------------------------------------------------------------------
    // Parser - the indirect object envelope
    // ------------------------------------------------------------------

    @DisplayName("object - number, generation and a dictionary body")
    @Test
    public void dictionaryObject() throws Exception {
        PObject pObject = parse("5 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        assertEquals(new Reference(5, 0), pObject.getReference());
        Catalog catalog = assertInstanceOf(Catalog.class, pObject.getObject());
        assertEquals(new Reference(2, 0), catalog.getEntries().get(new Name("Pages")));
    }

    @DisplayName("object - a bare value body, not a dictionary")
    @Test
    public void scalarObject() throws Exception {
        assertEquals(42, ((Number) parse("1 0 obj\n42\nendobj\n").getObject()).intValue());
        assertEquals("text",
                ((StringObject) parse("1 0 obj\n(text)\nendobj\n").getObject()).getLiteralString());
        assertEquals(3, ((List<?>) parse("1 0 obj\n[1 2 3]\nendobj\n").getObject()).size());
    }

    @DisplayName("object - a non-zero generation is kept")
    @Test
    public void generationNumber() throws Exception {
        assertEquals(new Reference(9, 4), parse("9 4 obj\n<< >>\nendobj\n").getReference());
    }

    @DisplayName("object - a stream body is sliced to its declared /Length")
    @Test
    public void streamObject() throws Exception {
        String body = "3 0 obj\n<< /Length 11 >>\nstream\nhello world\nendstream\nendobj\n";
        Stream stream = assertInstanceOf(Stream.class, parse(body).getObject());
        assertEquals("hello world",
                new String(stream.getDecodedStreamBytes(), StandardCharsets.ISO_8859_1));
    }

    @DisplayName("object - a /Length of zero falls back to scanning for endstream")
    @Test
    public void streamWithZeroLength() throws Exception {
        // Lazy encoders write /Length 0 and leave the data in place; the parser recovers the bytes
        // by looking for the endstream marker instead of trusting the dictionary.
        String body = "3 0 obj\n<< /Length 0 >>\nstream\nthis data is really here\nendstream\nendobj\n";
        Stream stream = assertInstanceOf(Stream.class, parse(body).getObject());
        assertEquals("this data is really here",
                new String(stream.getDecodedStreamBytes(), StandardCharsets.ISO_8859_1).trim());
    }

    @DisplayName("object - a /Length that stops short of endstream is measured to endstream instead")
    @Test
    public void streamWithShortLength() throws Exception {
        // Seen on an incrementally-updated cross-reference stream: /Length 25 over 35 bytes of deflate
        // data, which truncated the xref and sent the whole document into a reindex.
        String body = "3 0 obj\n<< /Length 4 >>\nstream\nthis data is really here\r\nendstream\nendobj\n";
        Stream stream = assertInstanceOf(Stream.class, parse(body).getObject());
        assertEquals("this data is really here",
                new String(stream.getDecodedStreamBytes(), StandardCharsets.ISO_8859_1));
    }

    @DisplayName("object - a /Length that runs past the end of the buffer is measured to endstream instead")
    @Test
    public void streamWithLengthPastEnd() throws Exception {
        String body = "3 0 obj\n<< /Length 999999 >>\nstream\nhello world\nendstream\nendobj\n";
        Stream stream = assertInstanceOf(Stream.class, parse(body).getObject());
        assertEquals("hello world",
                new String(stream.getDecodedStreamBytes(), StandardCharsets.ISO_8859_1));
    }

    @DisplayName("object - a /Length that is right up to the end-of-line before endstream is trusted")
    @Test
    public void streamWithLengthBeforeEol() throws Exception {
        // the stream data itself ends in white space; the declared length keeps it
        String body = "3 0 obj\n<< /Length 7 >>\nstream\nhello \n\r\nendstream\nendobj\n";
        Stream stream = assertInstanceOf(Stream.class, parse(body).getObject());
        assertEquals("hello \n",
                new String(stream.getDecodedStreamBytes(), StandardCharsets.ISO_8859_1));
    }

    @DisplayName("object - a body that is not an object at all is rejected")
    @Test
    public void malformedObject() {
        // No "obj" keyword: the parser must refuse rather than return a half-built object.
        assertThrows(ObjectStateException.class, () -> parse("<< /Type /Catalog >>\nendobj\n"));
        assertThrows(ObjectStateException.class, () -> parse("5 obj\n<< >>\nendobj\n"));
    }

    @DisplayName("compressed object - read from inside an object stream, with no envelope")
    @Test
    public void compressedObject() throws Exception {
        // Objects inside an /ObjStm carry no "n g obj" header; the number comes from the stream's
        // own offset table, so the parser is told it rather than reading it.
        PObject pObject = new Parser(new Library())
                .getCompressedObject(bufferOf("<< /Type /Page >>"), 12, 0);
        assertEquals(new Reference(12, 0), pObject.getReference());
        assertInstanceOf(Page.class, pObject.getObject());
    }

    // ------------------------------------------------------------------
    // ObjectFactory - dictionary dispatch
    // ------------------------------------------------------------------

    @DisplayName("factory - the document structure types")
    @Test
    public void structureTypes() {
        assertInstanceOf(Catalog.class, dictionaryOfType("Catalog"));
        assertInstanceOf(PageTree.class, dictionaryOfType("Pages"));
        assertInstanceOf(Page.class, dictionaryOfType("Page"));
    }

    @DisplayName("factory - an unknown /Type stays a plain entries map")
    @Test
    public void unknownType() {
        assertInstanceOf(DictionaryEntries.class, dictionaryOfType("SomethingElse"));
        // ... and so does a dictionary with no /Type at all.
        assertInstanceOf(DictionaryEntries.class,
                ObjectFactory.getInstance(new Library(), new DictionaryEntries()));
    }

    @DisplayName("factory - /Type /XObject with /Subtype /Image is an image, not a form")
    @Test
    public void imageXObject() {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(Dictionary.TYPE_KEY, new Name("XObject"));
        entries.put(Dictionary.SUBTYPE_KEY, new Name("Image"));
        entries.put(new Name("Width"), 1);
        entries.put(new Name("Height"), 1);
        assertInstanceOf(ImageStream.class, streamObjectFrom(entries));
    }

    @DisplayName("factory - /Type /XObject with /Subtype /Form is a form")
    @Test
    public void formXObject() {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(Dictionary.TYPE_KEY, new Name("XObject"));
        entries.put(Dictionary.SUBTYPE_KEY, new Name("Form"));
        assertInstanceOf(Form.class, streamObjectFrom(entries));
    }

    @DisplayName("factory - a stream with no recognised type is a plain stream")
    @Test
    public void plainStream() {
        assertInstanceOf(Stream.class, streamObjectFrom(new DictionaryEntries()));
    }

    private static Object dictionaryOfType(String type) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(Dictionary.TYPE_KEY, new Name(type));
        return ObjectFactory.getInstance(new Library(), entries);
    }

    private static Object streamObjectFrom(DictionaryEntries entries) {
        return ObjectFactory.getInstance(new Library(), 1, 0, entries,
                ByteBuffer.wrap(new byte[]{0})).getObject();
    }
}
