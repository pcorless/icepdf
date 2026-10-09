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
package org.icepdf.core.pobjects.actions;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A text or choice field reset without the Swing viewer lasts: saved and reopened, the field holds
 * its default and its appearance shows it.  Before, reset() only changed the in-memory value - the
 * Swing listener rebuilt and recorded the appearance - so a headless reset saved nothing.
 */
public class FieldResetPersistenceTest {

    private static final Path ALL_FIELDS = Paths.get("src/test/resources/acroform/all_fields.pdf");

    @TempDir
    Path temp;

    private static AbstractWidgetAnnotation<?> widget(Page page, String name) {
        for (Annotation a : page.getAnnotations()) {
            if (a instanceof AbstractWidgetAnnotation
                    && name.equals(((AbstractWidgetAnnotation<?>) a).getFieldDictionary().getFullyQualifiedFieldName())) {
                return (AbstractWidgetAnnotation<?>) a;
            }
        }
        throw new AssertionError("no field " + name);
    }

    private static String appearanceText(AbstractWidgetAnnotation<?> widget) {
        Stream stream = widget.getAppearanceStream();
        return stream != null ? new String(stream.getDecodedStreamBytes(), StandardCharsets.ISO_8859_1) : "";
    }

    private Path save(Document document, String name) throws Exception {
        Path target = temp.resolve(name + ".pdf");
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
            document.saveToOutputStream(out, WriteMode.INCREMENT_UPDATE);
        }
        return target;
    }

    @DisplayName("text and choice fields reset headless, saved and reopened: defaults kept and shown")
    @Test
    void resetSurvivesSave() throws Exception {
        Document document = new Document();
        document.setFile(ALL_FIELDS.toString());
        Page page = document.getPageTree().getPage(0);
        page.init();
        AbstractWidgetAnnotation<?> name = widget(page, "name");
        AbstractWidgetAnnotation<?> country = widget(page, "country");
        assertEquals("Ada", name.getFieldDictionary().getFieldValue(), "fixture value");
        assertEquals("France", country.getFieldDictionary().getFieldValue(), "fixture value");
        name.reset();
        country.reset();
        Path saved = save(document, "reset");
        document.dispose();

        Document reopened = new Document();
        reopened.setFile(saved.toString());
        try {
            Page page2 = reopened.getPageTree().getPage(0);
            page2.init();
            AbstractWidgetAnnotation<?> name2 = widget(page2, "name");
            AbstractWidgetAnnotation<?> country2 = widget(page2, "country");
            assertEquals("", name2.getFieldDictionary().getFieldValue(), "text reset to its empty /DV");
            assertEquals("Canada", country2.getFieldDictionary().getFieldValue(), "choice reset to /DV");
            assertFalse(appearanceText(name2).contains("(Ada)"), "the text appearance no longer shows Ada");
            assertTrue(appearanceText(country2).contains("Canada"), "the choice appearance shows Canada");
            assertFalse(appearanceText(country2).contains("France"), appearanceText(country2));
        } finally {
            reopened.dispose();
        }
    }

    @DisplayName("ResetForm action without a viewer: the reset is written")
    @Test
    void resetFormActionSurvivesSave() throws Exception {
        Document document = new Document();
        document.setFile(ALL_FIELDS.toString());
        document.getPageTree().getPage(0).init();
        org.icepdf.core.pobjects.DictionaryEntries entries = new org.icepdf.core.pobjects.DictionaryEntries();
        entries.put(Action.ACTION_TYPE_KEY, Action.ACTION_TYPE_RESET_SUBMIT);
        entries.put(FormAction.FLAGS_KEY, 0);
        new ResetFormAction(document.getCatalog().getLibrary(), entries).executeFormAction(0, 0);
        Path saved = save(document, "reset-action");
        document.dispose();

        Document reopened = new Document();
        reopened.setFile(saved.toString());
        try {
            Page page = reopened.getPageTree().getPage(0);
            page.init();
            assertEquals("Canada", widget(page, "country").getFieldDictionary().getFieldValue());
            assertEquals("", widget(page, "name").getFieldDictionary().getFieldValue());
        } finally {
            reopened.dispose();
        }
    }

    @DisplayName("a text widget that is a kid of its field: the field's /V is reset")
    @Test
    void kidWidgetResetsTheField() throws Exception {
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [4 0 R] "
                + "/DR << /Font << /Helv 6 0 R >> >> /DA (/Helv 12 Tf 0 g) >> >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Annots [5 0 R] >>");
        objects.add("<< /FT /Tx /T (Name) /V (Changed) /DV (Default) /DA (/Helv 12 Tf 0 g) /Kids [5 0 R] >>");
        objects.add("<< /Type /Annot /Subtype /Widget /Parent 4 0 R /Rect [10 10 200 40] /F 4 /P 3 0 R >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        StringBuilder out = new StringBuilder("%PDF-1.7\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.length());
            out.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = out.length();
        out.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) out.append(String.format("%010d 00000 n \n", offset));
        out.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        Path source = temp.resolve("kid.pdf");
        Files.write(source, out.toString().getBytes(StandardCharsets.ISO_8859_1));

        Document document = new Document();
        document.setFile(source.toString());
        Page page = document.getPageTree().getPage(0);
        page.init();
        AbstractWidgetAnnotation<?> kid = (AbstractWidgetAnnotation<?>) page.getAnnotations().get(0);
        kid.reset();
        Path saved = save(document, "kid-reset");
        document.dispose();

        Document reopened = new Document();
        reopened.setFile(saved.toString());
        try {
            FieldDictionary field = (FieldDictionary) reopened.getCatalog().getInteractiveForm().getFields().get(0);
            assertEquals("Default", field.getFieldValue(), "the field (parent) holds the reset value");
        } finally {
            reopened.dispose();
        }
    }
}
