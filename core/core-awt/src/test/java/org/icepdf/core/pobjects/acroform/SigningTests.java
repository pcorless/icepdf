package org.icepdf.core.pobjects.acroform;

import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.acroform.signature.Pkcs7Generator;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.util.updater.ObjectUpdateTests;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.*;

import static org.junit.jupiter.api.Assertions.fail;

public class SigningTests {
    @DisplayName("signatures - should create signed document")
    @Test
    public void testXrefTableFullUpdate() {
        try {
            Document document = new Document();
            InputStream fileUrl = ObjectUpdateTests.class.getResourceAsStream("/signing/test_print.pdf");
            document.setInputStream(fileUrl, "test_print.pdf");

            // Creat signature annotation
            SignatureWidgetAnnotation signatureAnnotation = (SignatureWidgetAnnotation)
                    AnnotationFactory.buildWidgetAnnotation(
                            document.getPageTree().getLibrary(),
                            FieldDictionaryFactory.TYPE_SIGNATURE,
                            new Rectangle(100, 250, 100, 50));

            // update dictionary,  already in StateManager
            SignatureDictionary signatureDictionary = SignatureDictionary.getInstance(signatureAnnotation);
            signatureDictionary.setName("Tester McTest");
            signatureDictionary.setLocation("Springfield USA");
            signatureDictionary.setReason("Make sure stuff didn't change");
            signatureDictionary.setDate("D:20240405082733+02'00'");

            // todo: add, signatureAnnotation.buildDefaultAppearance
            // signatureAnnotation.buildDefaultAppearance();

            // todo simple self signed keystore if no keystore is specified

            Pkcs7Generator generator = new Pkcs7Generator(signatureDictionary, keystore, "alias", "test");
            CMSSignedDataGenerator gen = generator.createGenerator("password");
            document.getCatalog().setSignedDataGenerator(gen);

            File out = new File("./src/test/out/SigningTest_signed_document.pdf");
            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192)) {
                document.saveToOutputStream(stream, WriteMode.INCREMENT_UPDATE);
            }

            // open the signed document
            Document modifiedDocument = new Document();
            modifiedDocument.setFile(out.getAbsolutePath());

        } catch (PDFSecurityException | IOException | InterruptedException e) {
            // make sure we have no io errors.
            fail("should not be any exceptions");
        }
    }
}
