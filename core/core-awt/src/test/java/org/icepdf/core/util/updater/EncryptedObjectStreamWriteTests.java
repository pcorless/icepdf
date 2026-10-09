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
package org.icepdf.core.util.updater;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.acroform.VariableTextFieldDictionary;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.TextWidgetAnnotation;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.geom.AffineTransform;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Saving an encrypted document whose objects sit in object streams and whose cross-reference table
 * is a stream - what Acrobat and PDFBox write by default.
 * <p>
 * Two rules of PDF 32000-1 7.5.7 / 7.5.8 / 7.6 meet here.  A cross-reference stream is never
 * encrypted (a reader needs it before it has a key to find anything with), and neither are the
 * strings of its dictionary.  And strings inside an object stream are not encrypted on their own:
 * the object stream is, as a whole.  So when an object comes out of an object stream and is written
 * as an object of its own, its strings have to be encrypted for the first time.
 * <p>
 * The writer got both wrong.  The update's cross-reference stream went out encrypted - readers
 * reported "Unknown compression method in flate stream" and lost every object the update added - and
 * the widget's {@code /DA}, read from an object stream, went out in plain text, which a reader then
 * deciphered into rubbish.  The fixtures are small forms saved by PDFBox with RC4, AES-128 and
 * AES-256.
 */
public class EncryptedObjectStreamWriteTests {

    private static Document open(String fixture) throws Exception {
        Document document = new Document();
        try (InputStream stream = EncryptedObjectStreamWriteTests.class.getResourceAsStream("/updater/" + fixture)) {
            assertNotNull(stream, "missing fixture " + fixture);
            document.setInputStream(stream, fixture);
        }
        assertNotNull(document.getCatalog().getLibrary().getSecurityManager(), fixture + " is expected to be encrypted");
        return document;
    }

    private static TextWidgetAnnotation field(Document document) throws Exception {
        Page page = document.getPageTree().getPage(0);
        page.init();
        for (Annotation annotation : page.getAnnotations()) {
            if (annotation instanceof TextWidgetAnnotation) return (TextWidgetAnnotation) annotation;
        }
        fail("no text field");
        return null;
    }

    private static String decrypted(Library library, Object value) {
        assertTrue(value instanceof StringObject, "a string: " + value);
        SecurityManager securityManager = library.getSecurityManager();
        return Utils.decodeTextString(((StringObject) value).getDecryptedRawBytes(securityManager));
    }

    /** Changes the field's value (a new string and a new appearance stream) and writes the document. */
    private File edit(String fixture, WriteMode writeMode) throws Exception {
        Document document = open(fixture);
        TextWidgetAnnotation widget = field(document);
        assertEquals("/Helv 10 Tf 0 g", decrypted(document.getCatalog().getLibrary(),
                widget.getEntries().get(VariableTextFieldDictionary.DA_KEY)), "the fixture reads");
        widget.getFieldDictionary().setFieldValue("World", widget.getPObjectReference());
        widget.resetAppearanceStream(new AffineTransform());

        File out = new File("./src/test/out/EncryptedObjectStreamWriteTests_" + fixture + "_" + writeMode + ".pdf");
        out.getParentFile().mkdirs();
        try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192)) {
            document.saveToOutputStream(stream, writeMode);
        }
        document.dispose();
        return out;
    }

    @DisplayName("an edited field of an object-stream encrypted document reads back: /DA, value and appearance")
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "encrypted_objstm_rc4.pdf, INCREMENT_UPDATE",
            "encrypted_objstm_rc4.pdf, FULL_UPDATE",
            "encrypted_objstm_aes128.pdf, INCREMENT_UPDATE",
            "encrypted_objstm_aes128.pdf, FULL_UPDATE",
            "encrypted_objstm_aes256.pdf, INCREMENT_UPDATE",
            "encrypted_objstm_aes256.pdf, FULL_UPDATE"})
    public void editedFieldRoundTrips(String fixture, WriteMode writeMode) throws Exception {
        File out = edit(fixture, writeMode);
        Document rewritten = new Document();
        rewritten.setFile(out.getAbsolutePath());
        try {
            Library library = rewritten.getCatalog().getLibrary();
            TextWidgetAnnotation widget = field(rewritten);
            assertEquals("/Helv 10 Tf 0 g", decrypted(library, widget.getEntries().get(VariableTextFieldDictionary.DA_KEY)),
                    "the /DA read from an object stream was encrypted when written as an object of its own");
            assertEquals("World", widget.getFieldDictionary().getFieldValue(), "the new value");
            assertNotNull(widget.getAppearanceStream(), "the appearance the update added is found");
            String appearance = new String(widget.getAppearanceStream().getDecodedStreamBytes(), StandardCharsets.ISO_8859_1);
            assertTrue(appearance.contains("(World)"), "the appearance decrypts and decodes:\n" + appearance);
        } finally {
            rewritten.dispose();
        }
    }

    @DisplayName("the update's cross-reference stream is written unencrypted")
    @ParameterizedTest(name = "{0}")
    @CsvSource({"encrypted_objstm_rc4.pdf", "encrypted_objstm_aes128.pdf", "encrypted_objstm_aes256.pdf"})
    public void crossReferenceStreamIsNotEncrypted(String fixture) throws Exception {
        File out = edit(fixture, WriteMode.INCREMENT_UPDATE);
        byte[] bytes = Files.readAllBytes(out.toPath());
        String file = new String(bytes, StandardCharsets.ISO_8859_1);
        Matcher startxref = Pattern.compile("startxref\\s+(\\d+)").matcher(file);
        int offset = -1;
        while (startxref.find()) offset = Integer.parseInt(startxref.group(1));
        assertTrue(offset > 0);
        String xref = file.substring(offset, Math.min(file.length(), offset + 1000));
        assertTrue(xref.contains("/XRef"), "the update ends in a cross-reference stream:\n" + xref);
        int data = file.indexOf("stream", offset + xref.indexOf(">>")) + "stream".length();
        while (file.charAt(data) == '\r' || file.charAt(data) == '\n') data++;
        Matcher length = Pattern.compile("/Length (\\d+)").matcher(xref);
        assertTrue(length.find());
        Inflater inflater = new Inflater();
        inflater.setInput(bytes, data, Integer.parseInt(length.group(1)));
        byte[] inflated = new byte[4096];
        int n = inflater.inflate(inflated);
        assertTrue(n > 0, "the cross-reference stream inflates as plain Flate data");
        assertEquals(0, n % 16, "whole 4 + 8 + 4 byte entries");
    }
}
