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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-636: a full save packs objects into object streams.
 * <p>
 * It used to write every object at the top level, so a document whose small objects - structure
 * elements, annotations, links - had been compressed together in object streams grew by half when
 * saved (the ISO 32000-2 spec: 15.5 MB to 21.8 MB). Every redacted export is a full save.
 */
public class ObjectStreamWriteTest {

    private static final String SMALL = "annotation_popup_full.pdf";
    private static final String PACKED = "CineplexMagazine-2020.pdf";

    private boolean compressXref;

    // other tests turn the compressed xref off and leave it off, and without it nothing is packed.
    @BeforeEach
    void compressedXref() {
        compressXref = FullUpdater.isCompressXrefTable();
        FullUpdater.setCompressXrefTable(true);
    }

    @AfterEach
    void restoreDefaults() {
        FullUpdater.setUseObjectStreams(true);
        FullUpdater.setCompressXrefTable(compressXref);
    }

    private static Document open(String fixture) throws Exception {
        Document document = new Document();
        try (InputStream stream = ObjectStreamWriteTest.class.getResourceAsStream("/updater/" + fixture)) {
            assertNotNull(stream, "missing fixture " + fixture);
            document.setInputStream(stream, fixture);
        }
        return document;
    }

    private static Document open(byte[] bytes) throws Exception {
        Document document = new Document();
        document.setByteArray(bytes, 0, bytes.length, "written");
        return document;
    }

    private static byte[] save(String fixture, boolean objectStreams) throws Exception {
        FullUpdater.setUseObjectStreams(objectStreams);
        Document document = open(fixture);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            return out.toByteArray();
        } finally {
            document.dispose();
        }
    }

    private static boolean hasObjectStream(byte[] pdf) {
        return new String(pdf, StandardCharsets.ISO_8859_1).contains("/ObjStm");
    }

    /** Page count, then each page's annotation count and text: what a reader of the result sees. */
    private static String describe(Document document) throws Exception {
        StringBuilder out = new StringBuilder().append(document.getNumberOfPages()).append('\n');
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            Page page = document.getPageTree().getPage(i);
            page.init();
            out.append(page.getAnnotations() == null ? 0 : page.getAnnotations().size()).append(' ')
                    .append(document.getPageText(i)).append('\n');
        }
        return out.toString();
    }

    @DisplayName("a full save writes object streams, and the result reads back the same")
    @Test
    void packedSaveReadsBack() throws Exception {
        byte[] written = save(SMALL, true);
        assertTrue(hasObjectStream(written), "objects should be packed");
        assertTrue(new String(written, 0, 8, StandardCharsets.ISO_8859_1).compareTo("%PDF-1.5") >= 0,
                "object streams need a 1.5 header: " + new String(written, 0, 8, StandardCharsets.ISO_8859_1));

        Document original = open(SMALL);
        Document reopened = open(written);
        try {
            assertEquals(describe(original), describe(reopened));
            assertEquals(original.getInfo().getTitle(), reopened.getInfo().getTitle());
        } finally {
            original.dispose();
            reopened.dispose();
        }
    }

    /**
     * In an encrypted document the object stream is encrypted as a whole and the strings in it are
     * plain; strings read encrypted from top-level objects have to be deciphered before they are
     * packed, or they come back as cipher text twice over.
     */
    @DisplayName("an encrypted document is packed too, and its strings read back")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"encrypted_objstm_rc4.pdf", "encrypted_objstm_aes128.pdf",
            "encrypted_objstm_aes256.pdf", "encrypted-aes-v2.pdf", "DSCP73_om_en.pdf"})
    void encryptedSaveReadsBack(String fixture) throws Exception {
        byte[] written = save(fixture, true);
        assertTrue(hasObjectStream(written), "objects should be packed");

        Document original = open(fixture);
        Document reopened = open(written);
        try {
            assertNotNull(reopened.getCatalog().getLibrary().getSecurityManager(), "still encrypted");
            assertEquals(describe(original), describe(reopened));
            assertEquals(strings(original), strings(reopened));
        } finally {
            original.dispose();
            reopened.dispose();
        }
    }

    /** The document's text strings that matter to a reader: title, and each field's name and value. */
    private static String strings(Document document) throws Exception {
        StringBuilder out = new StringBuilder(String.valueOf(document.getInfo().getTitle()));
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            Page page = document.getPageTree().getPage(i);
            page.init();
            if (page.getAnnotations() == null) continue;
            for (org.icepdf.core.pobjects.annotations.Annotation annotation : page.getAnnotations()) {
                if (annotation instanceof org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation) {
                    org.icepdf.core.pobjects.acroform.FieldDictionary field =
                            ((org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation<?>) annotation).getFieldDictionary();
                    out.append('|').append(field.getPartialFieldName()).append('=').append(field.getFieldValue());
                }
            }
        }
        return out.toString();
    }

    /** /Size has to be more than every object number, the cross-reference stream's own included. */
    @DisplayName("the cross-reference stream's /Size counts the stream itself")
    @Test
    void sizeCountsTheXrefStream() throws Exception {
        String pdf = new String(save(SMALL, true), StandardCharsets.ISO_8859_1);
        // the last object written is the cross-reference stream
        int objectAt = pdf.lastIndexOf(" 0 obj");
        int number = Integer.parseInt(pdf.substring(pdf.lastIndexOf('\n', objectAt) + 1, objectAt));
        java.util.regex.Matcher size = java.util.regex.Pattern.compile("/Size (\\d+)").matcher(pdf.substring(objectAt));
        assertTrue(pdf.substring(objectAt).contains("/Type /XRef") && size.find(), "no cross-reference stream");
        assertTrue(Integer.parseInt(size.group(1)) > number, "/Size " + size.group(1) + " for a stream numbered " + number);
    }

    @DisplayName("with object streams off, objects are written at the top level as before")
    @Test
    void switchedOff() throws Exception {
        assertFalse(hasObjectStream(save(SMALL, false)));
    }

    /**
     * The fixture's own objects are in object streams; written without them it grew. Packed it should
     * come out no bigger than it went in, give or take what the writer does differently.
     */
    @DisplayName("a document read from object streams is not bloated by saving it")
    @Test
    void sizeKept() throws Exception {
        int original;
        try (InputStream stream = ObjectStreamWriteTest.class.getResourceAsStream("/updater/" + PACKED)) {
            original = stream.readAllBytes().length;
        }
        byte[] packed = save(PACKED, true);
        byte[] unpacked = save(PACKED, false);
        assertTrue(packed.length < unpacked.length,
                "packed " + packed.length + " should be smaller than unpacked " + unpacked.length);
        assertTrue(packed.length <= original * 1.05,
                "packed " + packed.length + " should be close to the original " + original);

        Document reopened = open(packed);
        Document control = open(unpacked);
        try {
            assertEquals(describe(control), describe(reopened));
        } finally {
            reopened.dispose();
            control.dispose();
        }
    }
}
