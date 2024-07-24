package org.icepdf.core.pobjects.acroform;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.acroform.signature.handlers.Pkcs12SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.handlers.SimpleCallbackHandler;
import org.icepdf.core.pobjects.acroform.signature.utils.SignatureUtilities;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.SignatureDictionaries;
import org.icepdf.core.util.updater.WriteMode;
import org.icepdf.ri.common.views.annotations.signing.BasicSignatureAppearanceCallback;
import org.icepdf.ri.common.views.annotations.signing.SignatureAppearanceModel;
import org.icepdf.ri.util.FontPropertiesManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.fail;

public class SigningTests {

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    @DisplayName("signatures - should create signed document")
    @Test
    public void testXrefTableFullUpdate() {

        try {
            String keystorePath = "/home/pcorless/dev/cert-test/openssl-keypair/certificate.pfx";
            String password = "changeit";
            String certAlias = "senderKeyPair";

            Pkcs12SignerHandler pkcs12SignerHandler = new Pkcs12SignerHandler(new File(keystorePath), certAlias,
                    new SimpleCallbackHandler(password));

            Document document = new Document();
            InputStream fileUrl = SigningTests.class.getResourceAsStream("/signing/test_print.pdf");
            document.setInputStream(fileUrl, "test_print.pdf");
            Library library = document.getCatalog().getLibrary();
            SignatureDictionaries signatureDictionaries = library.getSignatureDictionaries();

            // Creat signature annotation
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
            signatureDictionaries.addCertifierSignature(signatureDictionary);
            signatureDictionary.setSignerHandler(pkcs12SignerHandler);
            signatureDictionary.setReason("Approval"); // Approval or certification but technically can be anything
            signatureDictionary.setDate(PDate.formatDateTime(new Date()));

            // assign cert metadata to dictionary
            SignatureUtilities.updateSignatureDictionary(signatureDictionary, pkcs12SignerHandler.getCertificate());

            // build basic appearance
            SignatureAppearanceModel signatureAppearanceModel = new SignatureAppearanceModel(
                    createTestSignatureBufferedImage(), Locale.ENGLISH);
            signatureAppearanceModel.setSignatureImageLocation(25, 50);
            signatureAppearanceModel.setColumnLayoutWidth((int) signatureAnnotation.getBbox().getWidth() / 2);
            BasicSignatureAppearanceCallback signatureAppearance =
                    new BasicSignatureAppearanceCallback(signatureAppearanceModel);
            signatureAnnotation.setResetAppearanceCallback(signatureAppearance);
            signatureAnnotation.resetNullAppearanceStream();

            // Most common workflow is to add just one signature as we do here, but it is possible to add multiple
            // signatures via some backend process to create a document with multiple signers/certs.  The following
            // shows how to iterate over each registered signature and move the pointer.
            File out = new File("./src/test/out/SigningTest_signed_document.pdf");
            // todo doing this a lot, should probably be a utility
            ArrayList<SignatureDictionary> signatures = signatureDictionaries.getSignatures();
            for (SignatureDictionary signature : signatures) {
                signatureDictionaries.setCurrentSignatureDictionary(signature);
                try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192)) {
                    document.saveToOutputStream(stream, WriteMode.INCREMENT_UPDATE);
                }
            }
            signatureDictionaries.setCurrentSignatureDictionary(null);
            // open the signed document
            Document modifiedDocument = new Document();
            modifiedDocument.setFile(out.getAbsolutePath());

        } catch (Exception e) {
            // make sure we have no io errors.
            e.printStackTrace();
            fail("should not be any exceptions");
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
