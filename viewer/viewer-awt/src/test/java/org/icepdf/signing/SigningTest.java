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
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.acroform.FieldDictionaryFactory;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.acroform.signature.handlers.Pkcs12SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.handlers.SimplePasswordCallbackHandler;
import org.icepdf.core.pobjects.acroform.signature.utils.SignatureUtilities;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.pobjects.security.JceProvider;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.SignatureManager;
import org.icepdf.core.util.updater.WriteMode;
import org.icepdf.ri.common.views.annotations.signing.BasicSignatureAppearanceCallback;
import org.icepdf.ri.common.views.annotations.signing.SignatureAppearanceModelImpl;
import org.icepdf.ri.util.FontPropertiesManager;
import org.icepdf.utils.PDFValidator;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

public class SigningTest {

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    @DisplayName("a signed document validates, and says in its structure that it is signed")
    @Test
    public void testCreateAndValidateSignature() throws Exception {
        File source = new File("src/test/resources/annotation/hello_pdfa1.pdf");
        File outputFile = SigningFixture.of(source)
                .reason("Approval")
                .signatureType(SignatureType.CERTIFIER)
                .withAppearance(createTestSignatureBufferedImage())
                .signTo(new File("./src/test/out/SigningTest_signed_document.pdf"));

        // signatures can be found off the Catalog as InteractiveForms.
        Document modifiedDocument = new Document();
        modifiedDocument.setFile(outputFile.getAbsolutePath());
        try {
            InteractiveForm interactiveForm = modifiedDocument.getCatalog().getInteractiveForm();
            assertNotNull(interactiveForm, "a signed document has an /AcroForm");
            ArrayList<SignatureWidgetAnnotation> signatureFields = interactiveForm.getSignatureFields();
            assertEquals(1, signatureFields.size(), "one signature was added, so one should be found");

            // Asked for its answer, not called for its side effect: this returned false however the
            // byte ranges came out, and the assertions below only passed because the flag it sets on
            // the way past happened to be what they read.
            assertTrue(interactiveForm.isSignaturesCoverDocumentLength(),
                    "the signature should cover the whole document");

            for (SignatureWidgetAnnotation signatureWidgetAnnotation : signatureFields) {
                SignatureValidator signatureValidator = signatureWidgetAnnotation.getSignatureValidator();
                signatureValidator.validate();
                assertTrue(signatureValidator.isSignaturesCoverDocumentLength());
                assertTrue(signatureValidator.isSelfSigned());
                assertFalse(signatureValidator.isCertificateChainTrusted());
                assertFalse(signatureValidator.isDocumentDataModified());

                assertTrue(signatureValidator.isSignerTimeValid());
                assertTrue(signatureValidator.isEmbeddedTimeStamp());
                assertFalse(signatureValidator.isSignedDataModified());
            }
        } finally {
            modifiedDocument.dispose();
        }

        // validate PDF/A-1b compliance of the output file.
        PDFValidator.validatePDFA(new FileInputStream(outputFile));

        assertSignedStructure(outputFile);

        // Signing must not make the document less conformant than it arrived. Checked across
        // every level rather than just the one it targets: PDF/A-1 says nothing about a
        // signature's byte range, and PDF/A-2 says a great deal.
        PDFValidator.assertNoNewFailures(source, outputFile,
                PDFAFlavour.PDFA_1_A, PDFAFlavour.PDFA_1_B,
                PDFAFlavour.PDFA_2_A, PDFAFlavour.PDFA_2_B, PDFAFlavour.PDFA_2_U,
                PDFAFlavour.PDFA_3_B);
    }

    /**
     * Structural checks a PDF/A-1b pass does not make, each of which was wrong at some point.
     * <p>
     * A signed document has to declare that it is signed, and any font added for the signature's
     * appearance has to be complete - a /ToUnicode pointing at an object that was never written is a
     * dangling reference, and veraPDF at 1b does not object to one.
     */
    private void assertSignedStructure(File outputFile) throws Exception {
        String pdf = new String(Files.readAllBytes(outputFile.toPath()), StandardCharsets.ISO_8859_1);

        assertTrue(pdf.matches("(?s).*/SigFlags\\s+3.*"),
                "a document with a signature field should declare /SigFlags 3");

        Matcher toUnicode = Pattern.compile("/ToUnicode\\s+(\\d+) 0 R").matcher(pdf);
        int checked = 0;
        while (toUnicode.find()) {
            String object = toUnicode.group(1);
            assertTrue(Pattern.compile("(?m)^" + object + " 0 obj").matcher(pdf).find()
                            || pdf.contains("\n" + object + " 0 obj"),
                    "/ToUnicode " + object + " 0 R points at an object that was never written");
            checked++;
        }
        assertTrue(checked > 0, "the appearance font should carry a /ToUnicode CMap");

        // A certification signature is one the catalog names in /Perms; the signature reference
        // dictionary alone does not make it one.
        // The digest covers everything but the signature, and the signature is the hex string with
        // its brackets - not the digits inside them.
        Matcher byteRange = Pattern.compile("/ByteRange\\s*\\[\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)")
                .matcher(pdf);
        assertTrue(byteRange.find(), "the signature should carry a /ByteRange");
        // anchored on the signature dictionary: the page has a /Contents of its own, and it comes
        // first in the file.
        int signature = pdf.indexOf("/Type /Sig");
        assertTrue(signature > 0, "the document should contain a signature dictionary");
        int openAngle = pdf.indexOf('<', pdf.indexOf("/Contents", signature));
        int afterClose = pdf.indexOf('>', openAngle) + 1;
        assertEquals(0, Integer.parseInt(byteRange.group(1)), "the first range starts at the file start");
        assertEquals(openAngle, Integer.parseInt(byteRange.group(2)),
                "the first range should end at the signature's opening bracket");
        assertEquals(afterClose, Integer.parseInt(byteRange.group(3)),
                "the second range should start just past the closing bracket");
        assertEquals(pdf.length() - afterClose, Integer.parseInt(byteRange.group(4)),
                "and run to the end of the file");

        Matcher perms = Pattern.compile("/Perms\\s*<<\\s*/DocMDP\\s+(\\d+) 0 R").matcher(pdf);
        assertTrue(perms.find(), "a certification signature should be named in the catalog's /Perms");
        assertTrue(pdf.contains(perms.group(1) + " 0 obj"),
                "/Perms /DocMDP should point at the signature dictionary");
        // DocMDP without TransformParams claims to certify without saying with what permissions.
        Matcher docMdp = Pattern.compile("/TransformMethod\\s*/DocMDP(.{0,200}?)>>", Pattern.DOTALL)
                .matcher(pdf);
        while (docMdp.find()) {
            assertTrue(docMdp.group(1).contains("/TransformParams"),
                    "a DocMDP reference must carry its TransformParams: " + docMdp.group(1));
            // PDF/A-2 6.1.12 forbids these alongside DocMDP, and ISO 32000-2 deprecated them.
            assertTrue(!docMdp.group(1).contains("/Digest"),
                    "a DocMDP reference must not carry digest entries: " + docMdp.group(1));
        }

        Matcher flags = Pattern.compile("/FontDescriptor.{0,400}?/Flags\\s+(\\d+)", Pattern.DOTALL)
                .matcher(pdf);
        while (flags.find()) {
            int value = Integer.parseInt(flags.group(1));
            boolean symbolic = (value & 4) != 0;
            boolean nonSymbolic = (value & 32) != 0;
            assertTrue(symbolic ^ nonSymbolic,
                    "a font descriptor must declare exactly one of Symbolic and Nonsymbolic, got " + value);
        }
    }

    private BufferedImage createTestSignatureBufferedImage() {
        BufferedImage image = new BufferedImage(150, 50, BufferedImage.TYPE_INT_ARGB);
        Graphics2D imageGraphics = image.createGraphics();
        imageGraphics.setStroke(new BasicStroke(2));
        imageGraphics.setColor(new Color(255, 255, 255));
        imageGraphics.fillRect(0, 0, 150, 50);
        imageGraphics.setColor(Color.BLUE);
        imageGraphics.fillRect(0, 0, 100, 25);
        imageGraphics.setColor(Color.RED);
        imageGraphics.drawRect(0, 0, 100, 25);
        imageGraphics.dispose();
        return image;
    }


}
