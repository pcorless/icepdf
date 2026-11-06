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
package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Form values held on a parent field, with the widgets that draw them as its kids. This is how a
 * field shown in more than one place is stored, and the widgets draw the value from an appearance
 * stream of their own, so masking the parent alone would leave the old text on the page.
 */
public class ParentFieldRedactionTest {

    private static final int FIRST_KID = 7;
    private static final int SECOND_KID = 8;
    private static final int GRANDCHILD = 12;
    private static final int UNTYPED_KID = 14;
    private static final int KID_WITH_OWN_VALUE = 16;
    private static final int UNTYPED_MERGED_FIELD = 18;
    private static final int UNTYPED_GRANDCHILD = 20;

    @DisplayName("the value and default value on a parent field are masked")
    @Test
    public void parentValuesAreMasked() throws Exception {
        Saved saved = redact("bravo");

        assertEquals(Collections.emptyList(), saved.warnings);
        assertEquals("**** report", saved.string(6, FieldDictionary.V_KEY));
        assertEquals("**** report", saved.string(6, FieldDictionary.DV_KEY));
    }

    @DisplayName("every widget inheriting the value stops drawing the term")
    @Test
    public void inheritingWidgetsAreRegenerated() throws Exception {
        Saved saved = redact("bravo");

        for (int widget : new int[]{FIRST_KID, SECOND_KID, GRANDCHILD, UNTYPED_KID, UNTYPED_GRANDCHILD}) {
            String appearance = saved.appearance(widget);
            assertFalse(appearance.contains("bravo"), "widget " + widget + " still draws: " + appearance);
            assertTrue(appearance.contains("report"), "widget " + widget + " lost the rest: " + appearance);
        }
    }

    @DisplayName("a field merged with a widget lacking /Type /Annot stops drawing the term")
    @Test
    public void untypedMergedFieldIsRegenerated() throws Exception {
        Saved saved = redact("bravo");

        assertEquals("**** merged", saved.string(UNTYPED_MERGED_FIELD, FieldDictionary.V_KEY));
        String appearance = saved.appearance(UNTYPED_MERGED_FIELD);
        assertFalse(appearance.contains("bravo"), appearance);
        assertTrue(appearance.contains("merged"), appearance);
    }

    @DisplayName("a kid with its own value isn't regenerated for its parent's")
    @Test
    public void kidWithItsOwnValueIsLeftAlone() throws Exception {
        Saved saved = redact("bravo");

        assertEquals(appearanceText("own value"), saved.appearance(KID_WITH_OWN_VALUE));
    }

    @DisplayName("a kid's own value is masked and regenerated")
    @Test
    public void kidsOwnValueIsMasked() throws Exception {
        Saved saved = redact("own");

        assertEquals("**** value", saved.string(KID_WITH_OWN_VALUE, FieldDictionary.V_KEY));
        assertFalse(saved.appearance(KID_WITH_OWN_VALUE).contains("own"));
        assertEquals(appearanceText("bravo report"), saved.appearance(FIRST_KID), "the parent has no match");
    }

    @DisplayName("a form with no match has nothing rewritten and nothing to warn about")
    @Test
    public void noMatchChangesNothing() throws Exception {
        Saved saved = redact("zulu");

        assertEquals(Collections.emptyList(), saved.warnings);
        assertEquals("bravo report", saved.string(6, FieldDictionary.V_KEY));
        assertEquals(appearanceText("bravo report"), saved.appearance(FIRST_KID));
    }

    // -- helpers ---------------------------------------------------------------------------------

    private Saved redact(String term) throws Exception {
        Document document = new Document();
        byte[] pdf = fixture();
        document.setInputStream(new ByteArrayInputStream(pdf), "parent-field.pdf");
        try {
            Redactor.configure(document, RedactionRequest.ofTerms(
                    Collections.singletonList(new SearchTerm(term, false, false, false))));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.writeToOutputStream(out, WriteMode.FULL_UPDATE);
            List<String> warnings = new ArrayList<>();
            document.getRedactionReport().getWarnings().forEach(w -> warnings.add(w.toString()));
            return new Saved(out.toByteArray(), warnings);
        } finally {
            document.dispose();
        }
    }

    private static final class Saved {
        private final byte[] pdf;
        private final List<String> warnings;

        Saved(byte[] pdf, List<String> warnings) {
            this.pdf = pdf;
            this.warnings = warnings;
        }

        private Document open() throws Exception {
            Document document = new Document();
            document.setInputStream(new ByteArrayInputStream(pdf), "saved.pdf");
            return document;
        }

        String string(int object, Name key) throws Exception {
            Document document = open();
            try {
                Library library = document.getCatalog().getLibrary();
                Object value = library.getObject(entriesOf(library, object), key);
                return value instanceof StringObject
                        ? Utils.convertStringObject(library, (StringObject) value) : null;
            } finally {
                document.dispose();
            }
        }

        String appearance(int widget) throws Exception {
            Document document = open();
            try {
                Library library = document.getCatalog().getLibrary();
                DictionaryEntries widgetEntries = entriesOf(library, widget);
                Object appearances = library.getObject(widgetEntries, Annotation.APPEARANCE_STREAM_KEY);
                Object normal = library.getObject((DictionaryEntries) appearances, new Name("N"));
                return new String(((Stream) normal).getDecodedStreamBytes(), StandardCharsets.ISO_8859_1).trim();
            } finally {
                document.dispose();
            }
        }

        private static DictionaryEntries entriesOf(Library library, int object) {
            Object resolved = library.getObject(new Reference(object, 0));
            if (resolved instanceof org.icepdf.core.pobjects.Dictionary) {
                return ((org.icepdf.core.pobjects.Dictionary) resolved).getEntries();
            }
            return (DictionaryEntries) resolved;
        }
    }

    private static String appearanceText(String text) {
        return "/Tx BMC q BT /Helv 12 Tf 2 5 Td (" + text + ") Tj ET Q EMC";
    }

    private static String widget(String y, int parent, int appearance, String extra) {
        return "<< " + extra + " /Subtype /Widget /Rect [72 " + y + " 300 " + (Integer.parseInt(y) + 20)
                + "] /F 4 /P 4 0 R /DA (/Helv 12 Tf 0 g) /Parent " + parent + " 0 R /AP << /N " + appearance + " 0 R >> >>";
    }

    private static String stream(String text) {
        String content = appearanceText(text);
        return "<< /Type /XObject /Subtype /Form /BBox [0 0 228 20] /Resources << /Font << /Helv 3 0 R >> >> /Length "
                + content.length() + " >>\nstream\n" + content + "\nendstream";
    }

    private static byte[] fixture() {
        String annot = "/Type /Annot";
        String[] objects = {
                "<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [6 0 R 18 0 R] /DA (/Helv 12 Tf 0 g) "
                        + "/DR << /Font << /Helv 3 0 R >> >> >> >>",
                "<< /Type /Pages /Kids [4 0 R] /Count 1 >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> "
                        + "/Contents 5 0 R /Annots [7 0 R 8 0 R 12 0 R 14 0 R 16 0 R 18 0 R 20 0 R] >>",
                "<< /Length 0 >>\nstream\n\nendstream",
                // the field: the value is here, the widgets are below it
                "<< /FT /Tx /T (parent) /V (bravo report) /DV (bravo report) "
                        + "/Kids [7 0 R 8 0 R 9 0 R 14 0 R 16 0 R] >>",
                widget("500", 6, 10, annot),
                widget("470", 6, 11, annot),
                // a level of the tree that holds neither a value nor a widget
                "<< /T (middle) /Parent 6 0 R /Kids [12 0 R 20 0 R] >>",
                stream("bravo report"),
                stream("bravo report"),
                widget("440", 9, 13, annot),
                stream("bravo report"),
                // /Type /Annot is optional on a widget
                widget("410", 6, 15, ""),
                stream("bravo report"),
                widget("380", 6, 17, annot + " /V (own value)"),
                stream("own value"),
                "<< /Subtype /Widget /FT /Tx /T (merged) /V (bravo merged) /Rect [72 350 300 370] /F 4 /P 4 0 R "
                        + "/DA (/Helv 12 Tf 0 g) /AP << /N 19 0 R >> >>",
                stream("bravo merged"),
                widget("320", 9, 21, ""),
                stream("bravo report"),
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.7\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.length; i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects[i] + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.length + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            table.append(String.format("%010d 00000 n \n", offset));
        }
        write(out, table + "trailer\n<< /Size " + (objects.length + 1) + " /Root 1 0 R >>\nstartxref\n" + xref
                + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
    }
}
