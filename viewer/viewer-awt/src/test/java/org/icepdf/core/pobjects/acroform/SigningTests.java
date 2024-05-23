package org.icepdf.core.pobjects.acroform;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.acroform.signature.handlers.BasicSignatureAppearanceCallback;
import org.icepdf.core.pobjects.acroform.signature.handlers.Pkcs12SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.handlers.SimpleCallbackHandler;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.WriteMode;
import org.icepdf.ri.util.FontPropertiesManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

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

            // Creat signature annotation
            SignatureWidgetAnnotation signatureAnnotation =
                    (SignatureWidgetAnnotation) AnnotationFactory.buildWidgetAnnotation(
                            document.getPageTree().getLibrary(),
                            FieldDictionaryFactory.TYPE_SIGNATURE,
                            new Rectangle(100, 250, 200, 100));
            document.getPageTree().getPage(0).addAnnotation(signatureAnnotation, true);

            // Add the signatureWidget to catalog
            InteractiveForm interactiveForm = document.getCatalog().getOrCreateInteractiveForm();
            interactiveForm.addField(signatureAnnotation);

            // update dictionary
            SignatureDictionary signatureDictionary = SignatureDictionary.getInstance(signatureAnnotation);
            signatureDictionary.setSignerHandler(pkcs12SignerHandler);
            signatureDictionary.setName("Tester McTest");
            signatureDictionary.setContactInfo("test@test.com");
            signatureDictionary.setLocation("Springfield USA");
            signatureDictionary.setReason("Make sure stuff didn't change");
            signatureDictionary.setDate("D:20240405082733+02'00'");

            // build basic appearance
            BasicSignatureAppearanceCallback signatureAppearance =
                    new BasicSignatureAppearanceCallback("Test Title 2", null);
            signatureAnnotation.setResetAppearanceCallback(signatureAppearance);
            signatureAnnotation.resetNullAppearanceStream();

            // set this signature as the primary certification signer.
            library.addCertificationSigner(signatureDictionary);

            File out = new File("./src/test/out/SigningTest_signed_document.pdf");
            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192)) {
                document.saveToOutputStream(stream, WriteMode.INCREMENT_UPDATE);
            }

            // open the signed document
            Document modifiedDocument = new Document();
            modifiedDocument.setFile(out.getAbsolutePath());

        } catch (Exception e) {
            // make sure we have no io errors.
            e.printStackTrace();
            fail("should not be any exceptions");
        }
    }
}
