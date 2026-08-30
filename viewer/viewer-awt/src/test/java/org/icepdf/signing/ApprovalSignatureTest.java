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
package org.icepdf.signing;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.ri.util.FontPropertiesManager;
import org.icepdf.utils.PDFValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.verapdf.pdfa.flavours.PDFAFlavour;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The other kind of signature.
 * <p>
 * Every other test here signs as a {@link SignatureType#CERTIFIER}, and the two kinds differ in
 * exactly the entries this branch changed: a certification is the catalog's {@code /Perms /DocMDP}
 * naming the signature, and a signature reference dictionary saying {@code /DocMDP} is what claims
 * it. An approval signature makes neither claim - it used to write the reference dictionary anyway,
 * with no {@code /TransformParams} to say what it was certifying - and it is the ordinary case, the
 * one a second signer on a document produces.
 */
public class ApprovalSignatureTest {

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    @DisplayName("an approval signature validates and claims to certify nothing")
    @Test
    public void approvalSignatureDoesNotCertify() throws Exception {
        File source = new File("src/test/resources/annotation/hello_pdfa1.pdf");
        File outputFile = SigningFixture.of(source)
                .reason("Approval")
                .signatureType(SignatureType.SIGNER)
                // With an appearance, as a signature on a PDF/A document has to be: PDF/A-1 6.9 and
                // PDF/A-2 6.3.3 require every annotation to carry an /AP /N, and a signature widget
                // is an annotation. Signing without one produces a file that no longer conforms, and
                // nothing on the signing path says so.
                .withAppearance()
                .signTo(new File("./src/test/out/ApprovalSignatureTest_signed_document.pdf"));

        Document signed = new Document();
        signed.setFile(outputFile.getAbsolutePath());
        try {
            InteractiveForm interactiveForm = signed.getCatalog().getInteractiveForm();
            assertNotNull(interactiveForm, "a signed document has an /AcroForm");
            ArrayList<SignatureWidgetAnnotation> signatureFields = interactiveForm.getSignatureFields();
            assertEquals(1, signatureFields.size(), "one signature was added, so one should be found");
            assertTrue(interactiveForm.isSignaturesCoverDocumentLength(),
                    "an approval signature covers the document just as a certification does");

            SignatureValidator validator = signatureFields.get(0).getSignatureValidator();
            validator.validate();
            assertTrue(validator.isSignaturesCoverDocumentLength());
            assertFalse(validator.isDocumentDataModified());
            assertFalse(validator.isSignedDataModified());
        } finally {
            signed.dispose();
        }

        String pdf = new String(Files.readAllBytes(outputFile.toPath()), StandardCharsets.ISO_8859_1);
        // Only a certification signature is named here, and a reader that finds one treats the
        // document as certified - with whatever permissions the reference dictionary carries.
        assertFalse(pdf.contains("/DocMDP"),
                "an approval signature should neither be named in /Perms nor claim /DocMDP");
        // /SigFlags still has to be declared: it says signatures exist and the file must be appended
        // to rather than rewritten, which is true of any signature.
        assertTrue(pdf.matches("(?s).*/SigFlags\\s+3.*"),
                "a document with a signature field should declare /SigFlags 3");

        PDFValidator.assertNoNewFailures(source, outputFile,
                PDFAFlavour.PDFA_1_A, PDFAFlavour.PDFA_1_B,
                PDFAFlavour.PDFA_2_A, PDFAFlavour.PDFA_2_B, PDFAFlavour.PDFA_2_U,
                PDFAFlavour.PDFA_3_B);
    }
}
