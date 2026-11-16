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
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.structure.CrossReference;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-691: a full save of a document where a dictionary points at a cross-reference stream or an
 * object stream. The writer builds its own, so the old ones are file structure, not content.
 * <p>
 * A cross-reference stream reached this way stopped the write with "Unknown value", and an object
 * stream was copied whole, with the old versions of the objects in it.
 */
public class FileStructureReferenceTest {

    private static final Reference STRUCTURE = new Reference(7, 0);
    private static final String OLD_TEXT = "old version";

    @DisplayName("a dictionary pointing at a cross-reference stream no longer stops the write")
    @ParameterizedTest(name = "object streams {0}")
    @ValueSource(booleans = {true, false})
    public void crossReferenceStream(boolean objectStreams) throws Exception {
        boolean compressXref = FullUpdater.isCompressXrefTable();
        try {
            FullUpdater.setCompressXrefTable(objectStreams);
            FullUpdater.setUseObjectStreams(objectStreams);
            byte[] written = save(fixture(stream("/Type /XRef /Size 8 /W [1 1 1]", "\u0001\u0000\u0000")));

            assertWrittenAsNull(written);
        } finally {
            FullUpdater.setCompressXrefTable(compressXref);
            FullUpdater.setUseObjectStreams(true);
        }
    }

    @DisplayName("a dictionary pointing at an object stream doesn't copy it")
    @Test
    public void objectStream() throws Exception {
        String objects = "<< /Old (" + OLD_TEXT + ") >>";
        byte[] written = save(fixture(stream("/Type /ObjStm /N 1 /First 4", "20 0 " + objects)));

        assertFalse(new String(written, StandardCharsets.ISO_8859_1).contains(OLD_TEXT));
        assertWrittenAsNull(written);
    }

    @DisplayName("the document still opens and draws its page")
    @Test
    public void stillOpens() throws Exception {
        byte[] written = save(fixture(stream("/Type /XRef /Size 8 /W [1 1 1]", "\u0001\u0000\u0000")));

        Document document = open(written);
        try {
            assertEquals(1, document.getNumberOfPages());
            document.getPageTree().getPage(0).init();
        } finally {
            document.dispose();
        }
    }

    private static void assertWrittenAsNull(byte[] written) throws Exception {
        Document document = open(written);
        try {
            Library library = document.getCatalog().getLibrary();
            // the parser reads a null object as the string "null"
            Object structure = library.getObject(STRUCTURE);
            assertTrue(structure == null || "null".equals(structure), "the reference should read as no value");
            for (CrossReference crossReference : library.getCrossReferenceRoot().getCrossReferences()) {
                for (Reference reference : crossReference.getEntries().keySet()) {
                    Object object = library.getObject(reference);
                    if (object instanceof Stream) {
                        byte[] bytes = ((Stream) object).getDecodedStreamBytes();
                        assertFalse(bytes != null && new String(bytes, StandardCharsets.ISO_8859_1).contains(OLD_TEXT),
                                "the old object stream was copied");
                    }
                }
            }
        } finally {
            document.dispose();
        }
    }

    private static byte[] save(byte[] pdf) throws Exception {
        Document document = open(pdf);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            return out.toByteArray();
        } finally {
            document.dispose();
        }
    }

    private static Document open(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setByteArray(pdf, 0, pdf.length, "structure.pdf");
        return document;
    }

    private static String stream(String entries, String content) {
        return "<< " + entries + " /Length " + content.length() + " >>\nstream\n" + content + "\nendstream";
    }

    /** A page drawing a form whose /Resources is object 7, the stream given. */
    private static byte[] fixture(String seventh) {
        String pageContent = "q /Fm1 Do Q";
        String form = "0 0 m 10 10 l S";
        List<String> objects = new ArrayList<>(List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /XObject << /Fm1 5 0 R >> >> "
                        + "/Contents 4 0 R >>",
                "<< /Length " + pageContent.length() + " >>\nstream\n" + pageContent + "\nendstream",
                "<< /Type /XObject /Subtype /Form /BBox [0 0 20 20] /Resources 7 0 R /Length " + form.length()
                        + " >>\nstream\n" + form + "\nendstream",
                "<< /Unused true >>",
                seventh));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.7\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            table.append(String.format("%010d 00000 n \n", offset));
        }
        write(out, table + "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref
                + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
    }
}
