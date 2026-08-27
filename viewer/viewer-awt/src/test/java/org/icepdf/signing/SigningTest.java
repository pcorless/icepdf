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

    @DisplayName("signatures - should create signed document")
    @Test
    public void testCreateAndValidateSignature() {

        try {
            JceProvider.loadProvider();

            String keystorePath = "src/test/resources/signing/certificate.pfx";
            PfxGenerator.createPfx(keystorePath, "changeit", "senderKeyPair");

            String password = "changeit";
            String certAlias = "senderKeyPair";
            String timeStampAuthorityUrl = "http://time.certum.pl";

            Pkcs12SignerHandler pkcs12SignerHandler = new Pkcs12SignerHandler(
                    timeStampAuthorityUrl,
                    new File(keystorePath),
                    certAlias,
                    new SimplePasswordCallbackHandler(password));

            Document document = new Document();
            InputStream fileUrl = SigningTest.class.getResourceAsStream("/annotation/hello_pdfa1.pdf");
            document.setInputStream(fileUrl, "test_print.pdf");
            Library library = document.getCatalog().getLibrary();
            SignatureManager signatureManager = library.getSignatureDictionaries();

            // Create signature annotation
            SignatureWidgetAnnotation signatureAnnotation =
                    (SignatureWidgetAnnotation) AnnotationFactory.buildWidgetAnnotation(
                            document.getPageTree().getLibrary(),
                            FieldDictionaryFactory.TYPE_SIGNATURE,
                            new Rectangle(100, 250, 375, 150));
            document.getPageTree().getPage(0).addAnnotation(signatureAnnotation, true);

            // Add the signatureWidget to catalog
            InteractiveForm interactiveForm = document.getCatalog().getOrCreateInteractiveForm();
            interactiveForm.addField(signatureAnnotation);

            // set up signer dictionary as the primary certification signer.
            SignatureDictionary signatureDictionary =
                    SignatureDictionary.getInstance(signatureAnnotation, SignatureType.CERTIFIER);
            signatureDictionary.setSignerHandler(pkcs12SignerHandler);
            signatureDictionary.setReason("Approval"); // Approval or certification but technically can be anything
            signatureDictionary.setDate(PDate.formatDateTime(new Date()));
            signatureManager.addSignature(signatureDictionary, signatureAnnotation);

            // assign cert metadata to dictionary
            SignatureUtilities.updateSignatureDictionary(signatureDictionary, pkcs12SignerHandler.getCertificate());

            // build basic appearance
            SignatureAppearanceModelImpl signatureAppearanceModel = new SignatureAppearanceModelImpl(library);
            signatureAppearanceModel.setLocale(Locale.ENGLISH);
            signatureAppearanceModel.setName(signatureDictionary.getName());
            signatureAppearanceModel.setContact(signatureDictionary.getContactInfo());
            signatureAppearanceModel.setLocation(signatureDictionary.getLocation());
            signatureAppearanceModel.setSignatureType(signatureDictionary.getReason().equals("Approval") ?
                    SignatureType.SIGNER : SignatureType.CERTIFIER);
            signatureAppearanceModel.setSignatureImage(createTestSignatureBufferedImage());

            BasicSignatureAppearanceCallback signatureAppearance = new BasicSignatureAppearanceCallback();
            signatureAppearance.setSignatureAppearanceModel(signatureAppearanceModel);
            signatureAnnotation.setAppearanceCallback(signatureAppearance);
            signatureAnnotation.resetAppearanceStream(new AffineTransform());
            signatureAnnotation.saveAppearanceStream();

            // Most common workflow is to add just one signature as we do here
            File outputFile = new File("./src/test/out/SigningTest_signed_document.pdf");
            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(outputFile), 8192)) {
                document.saveToOutputStream(stream, WriteMode.INCREMENT_UPDATE);
            }
            // open the signed document
            Document modifiedDocument = new Document();
            modifiedDocument.setFile(outputFile.getAbsolutePath());

            // signatures can be found off the Catalog as InteractiveForms.
            interactiveForm = modifiedDocument.getCatalog().getInteractiveForm();
            if (interactiveForm != null) {
                ArrayList<SignatureWidgetAnnotation> signatureFields = interactiveForm.getSignatureFields();
                interactiveForm.isSignaturesCoverDocumentLength();
                // validate each signature.
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
            }
            modifiedDocument.dispose();

            // validate PDF/A-1b compliance of the output file.
            PDFValidator.validatePDFA(new FileInputStream(outputFile));

            assertSignedStructure(outputFile);

        } catch (Exception e) {
            // make sure we have no io errors.
            e.printStackTrace();
            fail("should not be any exceptions");
        }
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
