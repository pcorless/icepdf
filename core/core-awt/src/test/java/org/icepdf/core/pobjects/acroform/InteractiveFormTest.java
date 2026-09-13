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
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests adding fields to and removing them from a document's interactive form.
 * <p>
 * A field lives in two places at once: the form's list of field objects, and the {@code /Fields}
 * array of references that is what actually gets written.  Updating one without the other gives a
 * document that looks right in memory and is wrong on disk, or the reverse - so both are checked
 * after every change here.
 * <p>
 * Adding a signature field carries an extra obligation.  {@code /SigFlags} tells a reader that
 * signatures exist and that the file must be updated by appending rather than rewritten, and
 * rewriting is what silently invalidates a signature.
 */
public class InteractiveFormTest {

    private static Document document() throws Exception {
        Document document = new Document();
        document.setInputStream(
                InteractiveFormTest.class.getResourceAsStream("/redaction/simple_tj.pdf"),
                "simple_tj.pdf");
        return document;
    }

    /**
     * The document's form, created if it has none.
     */
    private static InteractiveForm form(Document document) {
        InteractiveForm form = document.getCatalog().getOrCreateInteractiveForm();
        assertNotNull(form, "the catalog should hand back a form to add fields to");
        return form;
    }

    private static SignatureWidgetAnnotation signatureField(Library library) {
        return (SignatureWidgetAnnotation) AnnotationFactory.buildWidgetAnnotation(
                library, FieldDictionaryFactory.TYPE_SIGNATURE, new Rectangle(10, 10, 100, 50));
    }

    /**
     * The /Fields array as it would be written.
     */
    @SuppressWarnings("unchecked")
    private static List<Reference> fieldReferences(InteractiveForm form) {
        return (List<Reference>) form.getLibrary()
                .getObject(form.getEntries(), InteractiveForm.FIELDS_KEY);
    }

    // ------------------------------------------------------------------
    // adding
    // ------------------------------------------------------------------

    @DisplayName("a field is added to the form and to the array that gets written")
    @Test
    public void addField() throws Exception {
        Document document = document();
        try {
            InteractiveForm form = form(document);
            SignatureWidgetAnnotation field = signatureField(document.getCatalog().getLibrary());
            form.addField(field);

            assertTrue(form.getFields().contains(field), "the form should hold the field");
            assertNotNull(fieldReferences(form), "/Fields should exist once a field is added");
            assertTrue(fieldReferences(form).contains(field.getPObjectReference()),
                    "/Fields should name the field that was added");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("a second field joins the first rather than replacing it")
    @Test
    public void addSeveralFields() throws Exception {
        // The first add creates the array and the rest append to it, which are different branches.
        Document document = document();
        try {
            InteractiveForm form = form(document);
            Library library = document.getCatalog().getLibrary();
            SignatureWidgetAnnotation first = signatureField(library);
            SignatureWidgetAnnotation second = signatureField(library);

            form.addField(first);
            int afterFirst = form.getFields().size();
            form.addField(second);

            assertEquals(afterFirst + 1, form.getFields().size());
            assertTrue(fieldReferences(form).contains(first.getPObjectReference()));
            assertTrue(fieldReferences(form).contains(second.getPObjectReference()));
        } finally {
            document.dispose();
        }
    }

    @DisplayName("adding a signature field says in the form that signatures exist")
    @Test
    public void addingASignatureSetsSigFlags() throws Exception {
        // Without these flags a reader may rewrite the file rather than append to it, and a
        // rewrite is what silently invalidates whatever signature was there.
        Document document = document();
        try {
            InteractiveForm form = form(document);
            form.addField(signatureField(document.getCatalog().getLibrary()));

            Object sigFlags = form.getLibrary()
                    .getObject(form.getEntries(), InteractiveForm.SIG_FLAGS_KEY);
            assertNotNull(sigFlags, "a form holding a signature has to carry /SigFlags");
            int flags = ((Number) sigFlags).intValue();
            assertEquals(InteractiveForm.SIG_FLAGS_SIGNATURES_EXIST,
                    flags & InteractiveForm.SIG_FLAGS_SIGNATURES_EXIST,
                    "signatures exist");
            assertEquals(InteractiveForm.SIG_FLAGS_APPEND_ONLY,
                    flags & InteractiveForm.SIG_FLAGS_APPEND_ONLY,
                    "and the file must only be appended to");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("only a widget annotation can be a form field")
    @Test
    public void addFieldRejectsSomethingElse() throws Exception {
        // The /Fields array holds references to widget annotations; anything else would be written
        // out as a field and read back as one by a reader that then cannot use it.
        Document document = document();
        try {
            InteractiveForm form = form(document);
            assertThrows(IllegalStateException.class, () -> form.addField("not an annotation"));
            assertThrows(IllegalStateException.class, () -> form.addField(
                    AnnotationFactory.buildAnnotation(document.getCatalog().getLibrary(),
                            Annotation.SUBTYPE_SQUARE, new Rectangle(0, 0, 10, 10))));
        } finally {
            document.dispose();
        }
    }

    // ------------------------------------------------------------------
    // signature fields among the rest
    // ------------------------------------------------------------------

    @DisplayName("the signature fields are picked out of the form's fields")
    @Test
    public void signatureFields() throws Exception {
        Document document = document();
        try {
            InteractiveForm form = form(document);
            assertFalse(form.isSignatureFields(), "a new form holds no signatures");

            SignatureWidgetAnnotation field = signatureField(document.getCatalog().getLibrary());
            form.addField(field);

            assertTrue(form.isSignatureFields());
            assertEquals(1, form.getSignatureFields().size());
            assertEquals(field, form.getSignatureFields().get(0));
        } finally {
            document.dispose();
        }
    }

    @DisplayName("a form with no fields reports no signatures rather than failing")
    @Test
    public void noFields() throws Exception {
        Document document = document();
        try {
            InteractiveForm form = form(document);
            assertFalse(form.isSignatureFields());
            assertNotNull(form.getSignatureFields());
            assertTrue(form.getSignatureFields().isEmpty());
        } finally {
            document.dispose();
        }
    }

    // ------------------------------------------------------------------
    // removing
    // ------------------------------------------------------------------

    @DisplayName("a removed field is gone from the form and from the array that gets written")
    @Test
    public void removeField() throws Exception {
        Document document = document();
        try {
            InteractiveForm form = form(document);
            SignatureWidgetAnnotation field = signatureField(document.getCatalog().getLibrary());
            form.addField(field);
            form.removeField(field);

            assertFalse(form.getFields().contains(field), "the form should have let the field go");
            assertFalse(fieldReferences(form).contains(field.getPObjectReference()),
                    "/Fields should no longer name it");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("removing one field leaves the others alone")
    @Test
    public void removeOneOfSeveral() throws Exception {
        Document document = document();
        try {
            InteractiveForm form = form(document);
            Library library = document.getCatalog().getLibrary();
            SignatureWidgetAnnotation kept = signatureField(library);
            SignatureWidgetAnnotation removed = signatureField(library);

            form.addField(kept);
            form.addField(removed);
            form.removeField(removed);

            assertTrue(form.getFields().contains(kept));
            assertFalse(form.getFields().contains(removed));
            assertTrue(fieldReferences(form).contains(kept.getPObjectReference()));
            assertFalse(fieldReferences(form).contains(removed.getPObjectReference()));
        } finally {
            document.dispose();
        }
    }

    @DisplayName("removing a field that was never added does nothing")
    @Test
    public void removeUnknownField() throws Exception {
        Document document = document();
        try {
            InteractiveForm form = form(document);
            Library library = document.getCatalog().getLibrary();
            SignatureWidgetAnnotation added = signatureField(library);
            form.addField(added);

            form.removeField(signatureField(library));

            assertTrue(form.getFields().contains(added), "the field that was there should remain");
        } finally {
            document.dispose();
        }
    }
}
