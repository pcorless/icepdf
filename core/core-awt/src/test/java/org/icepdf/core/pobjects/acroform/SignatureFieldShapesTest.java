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
package org.icepdf.core.pobjects.acroform;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Signature fields in the shapes real files use, beyond a typed field merged with its widget:
 * a merged dictionary without /Type /Annot (optional on a widget), a field kept apart from its widget
 * kid, and a form without /Fields (the widget only on the page).  getSignatureFields() used to list
 * none of them, so no viewer showed or validated those signatures.
 */
public class SignatureFieldShapesTest {

    @TempDir
    Path temp;

    /** A one-page PDF from numbered object bodies (object 1 the catalog), with a correct xref. */
    private Path pdf(String name, String... objects) throws Exception {
        StringBuilder out = new StringBuilder("%PDF-1.7\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.length; i++) {
            offsets.add(out.length());
            out.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
        }
        int xref = out.length();
        out.append("xref\n0 ").append(objects.length + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) out.append(String.format("%010d 00000 n \n", offset));
        out.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        Path file = temp.resolve(name + ".pdf");
        Files.write(file, out.toString().getBytes(StandardCharsets.ISO_8859_1));
        return file;
    }

    private static final String PAGES = "<< /Type /Pages /Kids [3 0 R] /Count 1 >>";

    private static List<SignatureWidgetAnnotation> signatureFields(Path file, boolean checkPageIdentity)
            throws Exception {
        Document document = new Document();
        document.setFile(file.toString());
        try {
            InteractiveForm form = document.getCatalog().getInteractiveForm();
            List<SignatureWidgetAnnotation> fields = form != null ? form.getSignatureFields() : List.of();
            if (checkPageIdentity && !fields.isEmpty()) {
                Page page = document.getPageTree().getPage(0);
                page.init();
                Annotation onPage = page.getAnnotations().get(0);
                assertSame(fields.get(0), onPage, "the form's widget is the page's widget");
            }
            return fields;
        } finally {
            document.dispose();
        }
    }

    @DisplayName("a merged field + widget without /Type /Annot is a signature field")
    @Test
    void untypedMergedWidget() throws Exception {
        Path file = pdf("untyped",
                "<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [4 0 R] /SigFlags 3 >> >>",
                PAGES,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Annots [4 0 R] >>",
                "<< /Subtype /Widget /FT /Sig /T (Signature1) /Rect [10 10 110 60] /F 4 /P 3 0 R >>");
        List<SignatureWidgetAnnotation> fields = signatureFields(file, true);
        assertEquals(1, fields.size());
        assertEquals("Signature1", fields.get(0).getFieldDictionary().getPartialFieldName());
    }

    @DisplayName("a signature field kept apart from its widget kid lists the widget")
    @Test
    void separateWidgetKid() throws Exception {
        Path file = pdf("kid",
                "<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [4 0 R] /SigFlags 3 >> >>",
                PAGES,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Annots [5 0 R] >>",
                "<< /FT /Sig /T (Approval) /Kids [5 0 R] >>",
                "<< /Type /Annot /Subtype /Widget /Parent 4 0 R /Rect [10 10 110 60] /F 4 /P 3 0 R >>");
        List<SignatureWidgetAnnotation> fields = signatureFields(file, false);
        assertEquals(1, fields.size());
        assertEquals("Approval", fields.get(0).getFieldDictionary().getFullyQualifiedFieldName());
    }

    @DisplayName("the same, with an untyped kid widget")
    @Test
    void separateUntypedWidgetKid() throws Exception {
        Path file = pdf("untyped-kid",
                "<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [4 0 R] /SigFlags 3 >> >>",
                PAGES,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Annots [5 0 R] >>",
                "<< /FT /Sig /T (Approval) /Kids [5 0 R] >>",
                "<< /Subtype /Widget /Parent 4 0 R /Rect [10 10 110 60] /F 4 /P 3 0 R >>");
        assertEquals(1, signatureFields(file, false).size());
    }

    @DisplayName("a form without /Fields: the signature widget is found on the page")
    @Test
    void noFieldsArray() throws Exception {
        Path file = pdf("no-fields",
                "<< /Type /Catalog /Pages 2 0 R /AcroForm << /SigFlags 3 >> >>",
                PAGES,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Annots [4 0 R] >>",
                "<< /Subtype /Widget /FT /Sig /T (Signature1) /Rect [10 10 110 60] /F 4 /P 3 0 R >>");
        List<SignatureWidgetAnnotation> fields = signatureFields(file, true);
        assertEquals(1, fields.size());
    }

    @DisplayName("a typed merged widget, the shape that always worked, still lists once")
    @Test
    void typedMergedWidget() throws Exception {
        Path file = pdf("typed",
                "<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [4 0 R] /SigFlags 3 >> >>",
                PAGES,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Annots [4 0 R] >>",
                "<< /Type /Annot /Subtype /Widget /FT /Sig /T (Signature1) /Rect [10 10 110 60] /F 4 /P 3 0 R >>");
        assertEquals(1, signatureFields(file, false).size());
    }

    @DisplayName("an untyped text field merged with its widget is listed as the widget too")
    @Test
    void untypedTextWidget() throws Exception {
        Path file = pdf("text",
                "<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [4 0 R] >> >>",
                PAGES,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Annots [4 0 R] >>",
                "<< /Subtype /Widget /FT /Tx /T (Name) /V (Ada) /Rect [10 10 110 30] /F 4 /P 3 0 R >>");
        Document document = new Document();
        document.setFile(file.toString());
        try {
            Object field = document.getCatalog().getInteractiveForm().getFields().get(0);
            assertTrue(field instanceof org.icepdf.core.pobjects.annotations.TextWidgetAnnotation,
                    field.getClass().getSimpleName());
            assertEquals("Ada", ((org.icepdf.core.pobjects.annotations.TextWidgetAnnotation) field)
                    .getFieldDictionary().getFieldValue());
        } finally {
            document.dispose();
        }
    }

    @DisplayName("SigFlags says signatures exist: a signed widget left out of /Fields is found on the page")
    @Test
    void signedWidgetMissingFromFields() throws Exception {
        Path file = pdf("missing-from-fields",
                "<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [4 0 R] /SigFlags 3 >> >>",
                PAGES,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Annots [4 0 R 5 0 R] >>",
                "<< /Type /Annot /Subtype /Widget /FT /Sig /T (Listed) /Rect [10 10 90 40] /F 4 /P 3 0 R >>",
                "<< /Type /Annot /Subtype /Widget /FT /Sig /T (OnPageOnly) /Rect [10 60 90 90] /F 4 /P 3 0 R >>");
        List<SignatureWidgetAnnotation> fields = signatureFields(file, false);
        assertEquals(2, fields.size(), "the listed field and the page-only widget");
    }

    @DisplayName("no SigFlags: a form with fields isn't scanned page by page")
    @Test
    void noSigFlagsNoPageScan() throws Exception {
        Path file = pdf("no-sigflags",
                "<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [4 0 R] >> >>",
                PAGES,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Annots [4 0 R 5 0 R] >>",
                "<< /Type /Annot /Subtype /Widget /FT /Sig /T (Listed) /Rect [10 10 90 40] /F 4 /P 3 0 R >>",
                "<< /Type /Annot /Subtype /Widget /FT /Sig /T (OnPageOnly) /Rect [10 60 90 90] /F 4 /P 3 0 R >>");
        assertEquals(1, signatureFields(file, false).size());
    }

    @DisplayName("/V is inherited: a kid widget reads its field's value")
    @Test
    void kidInheritsValue() throws Exception {
        Path file = pdf("inherit-value",
                "<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [4 0 R] >> >>",
                PAGES,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Annots [5 0 R] >>",
                "<< /FT /Tx /T (Name) /V (Ada) /Kids [5 0 R] >>",
                "<< /Type /Annot /Subtype /Widget /Parent 4 0 R /Rect [10 10 110 30] /F 4 /P 3 0 R >>");
        Document document = new Document();
        document.setFile(file.toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation<?> kid =
                    (org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation<?>) page.getAnnotations().get(0);
            assertEquals("Ada", kid.getFieldDictionary().getFieldValue(), "was empty: only the kid's own /V was read");
            assertFalse(kid.getFieldDictionary().hasFieldValue(), "the kid itself still has no /V");
        } finally {
            document.dispose();
        }
    }
}
