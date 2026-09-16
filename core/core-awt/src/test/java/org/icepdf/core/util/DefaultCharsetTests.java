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
package org.icepdf.core.util;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.annotations.AppearanceState;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Bytes that must not be run through the platform's default charset on their way in or out.
 * <p>
 * A PDF is bytes, not text.  Turning a run of them into a String and back is only lossless if the
 * charset maps the 256 byte values one to one, which ISO-8859-1 does and the default on nearly
 * every machine now - UTF-8 - does not: a byte above 127 that is not valid UTF-8 decodes to the
 * replacement character and comes back as a question mark.
 * <p>
 * These assert the invariant rather than the charset, so they say the same thing on a machine whose
 * default happens to be Latin-1 - where this was never broken, which is part of why it was not
 * noticed.  On any UTF-8 machine, which is to say all of them, they fail if the conversions go back
 * to using the default.
 */
public class DefaultCharsetTests {

    /** A content stream fragment holding a literal string with a byte above 127 in it. */
    private static final byte[] HIGH_BYTE_CONTENT =
            {'(', (byte) 0xE9, 't', (byte) 0xE8, ')', ' ', 'T', 'j'};

    @DisplayName("the header's binary marker is four bytes, whatever the platform encodes with")
    @Test
    public void headerMarkerIsFourBytes() throws Exception {
        // The four bytes above 127 that tell anything transferring the file that it is binary
        // (PDF 32000-1 7.5.2).  Written from a string literal it was eight bytes on a UTF-8 machine
        // and four on a Latin-1 one, so the same source produced different files.
        Document document = new Document();
        try (InputStream stream =
                     DefaultCharsetTests.class.getResourceAsStream("/updater/DSCP73_om_en.pdf")) {
            assertNotNull(stream);
            document.setInputStream(stream, "DSCP73_om_en.pdf");
        }
        File out = new File("./src/test/out/DefaultCharsetTests_header.pdf");
        out.getParentFile().mkdirs();
        try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192)) {
            document.saveToOutputStream(stream, WriteMode.FULL_UPDATE);
        }
        document.dispose();

        byte[] header = Arrays.copyOf(Files.readAllBytes(out.toPath()), 32);
        String text = new String(header, StandardCharsets.ISO_8859_1);
        int marker = text.indexOf("\n%") + 2;
        assertArrayEquals(new byte[]{(byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3},
                Arrays.copyOfRange(header, marker, marker + 4),
                "the binary marker comment should be these four bytes");
        assertEquals('\n', (char) header[marker + 4],
                "and nothing else before the line ends - eight bytes here is the default charset");
    }

    @DisplayName("an appearance stream's bytes survive being read out as a string")
    @Test
    public void appearanceStreamContentIsNotDecodedWithTheDefaultCharset() {
        // The string this hands back is edited and written again with getBytes(ISO-8859-1) - by
        // TextWidgetAnnotation and ChoiceWidgetAnnotation, when a widget's appearance is rebuilt.
        // Read with the default charset the two do not agree, and every byte above 127 in the
        // appearance - an accented character in a field's value - came back as a question mark.
        Library library = new Library();
        Stream stream = new Stream(new DictionaryEntries(), null);
        stream.setPObjectReference(new Reference(1, 0));
        stream.setRawBytes(HIGH_BYTE_CONTENT);

        // an appearance falls back to the annotation's rectangle for its bounding box
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Rect"), List.of(0, 0, 100, 50));

        AppearanceState appearanceState = new AppearanceState(library, entries, stream);

        assertArrayEquals(HIGH_BYTE_CONTENT,
                appearanceState.getOriginalContentStream().getBytes(StandardCharsets.ISO_8859_1),
                "the content stream should come back as the bytes that went in");
    }

    @DisplayName("the platform default is not assumed to be one byte per character")
    @Test
    public void defaultCharsetIsReportedForContext() {
        // Not an assertion about the machine - it is allowed to be anything.  This is here so that a
        // failure above is read correctly: on a Latin-1 machine these conversions are lossless and
        // the tests above pass whether the code is right or not.
        Charset charset = Charset.defaultCharset();
        assertNotNull(charset);
        System.out.println("default charset for this run: " + charset
                + (StandardCharsets.ISO_8859_1.equals(charset)
                ? " (byte for byte - the charset tests above cannot fail here)" : ""));
    }
}
