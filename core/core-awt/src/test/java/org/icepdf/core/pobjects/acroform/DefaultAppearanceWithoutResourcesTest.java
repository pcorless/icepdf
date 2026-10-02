/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.icepdf.core.pobjects.acroform;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A text field's /DA is parsed against the AcroForm's /DR resources.  A form with no /DR left the parser with no
 * resources, so the Tf operator threw a NullPointerException and every operator after it - the field's colour,
 * typically - was skipped (Confirmation-Letter-Verbal-Order.pdf, 12 times per render).
 */
public class DefaultAppearanceWithoutResourcesTest {

    @DisplayName("a /DA is fully parsed when the AcroForm has no /DR")
    @Test
    public void defaultAppearanceParsesWithoutDefaultResources() throws Exception {
        Document document = new Document();
        try {
            byte[] pdf = formWithoutDefaultResources("/Helv 9 Tf 1 0 0 rg");
            document.setByteArray(pdf, 0, pdf.length, "no-dr.pdf");
            Page page = document.getPageTree().getPage(0);
            page.init();
            Annotation annotation = page.getAnnotations().get(0);
            assertInstanceOf(AbstractWidgetAnnotation.class, annotation);
            FieldDictionary field = ((AbstractWidgetAnnotation<?>) annotation).getFieldDictionary();
            assertInstanceOf(VariableTextFieldDictionary.class, field);

            String appearance = ((VariableTextFieldDictionary) field).generateDefaultAppearance(null, null);

            // the colour comes after Tf: it is only red if parsing got past the font operator
            assertTrue(appearance.startsWith("1.0 0.0 0.0 rg"), appearance);
            assertTrue(appearance.contains(" 9.0 Tf"), appearance);
        } finally {
            document.dispose();
        }
    }

    /** One page with one text field widget; the AcroForm lists the field but has no /DR. */
    private static byte[] formWithoutDefaultResources(String defaultAppearance) {
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [4 0 R] >> >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Annots [4 0 R] >>");
        objects.add("<< /Type /Annot /Subtype /Widget /FT /Tx /T (name) /Rect [100 600 300 620] /P 3 0 R"
                + " /DA (" + defaultAppearance + ") >>");
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) {
            pdf.append(String.format("%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Root 1 0 R /Size ").append(objects.size() + 1)
                .append(" >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }
}
