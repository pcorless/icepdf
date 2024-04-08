package org.icepdf.core.pobjects.acroform;

import org.bouncycastle.cms.CMSSignedDataGenerator;
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
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.fail;

public class SigningTests {
    @DisplayName("signatures - should create signed document")
    @Test
    public void testXrefTableFullUpdate() {

        try {
            Document document = new Document();
            InputStream fileUrl = ObjectUpdateTests.class.getResourceAsStream("/signing/test_print.pdf");
            document.setInputStream(fileUrl, "test_print.pdf");


            String password = "changeit";
            String keystorePath = "/home/pcorless/dev/cert-test/keypair/sender_keystore.pfx";
            String certAlias = "senderKeyPair";
            String algorithm = "SHA512WithRSA";

            KeyStore keystore = KeyStore.getInstance("PKCS12");
            keystore.load(new FileInputStream(keystorePath), password.toCharArray());
            X509Certificate certificate = (X509Certificate) keystore.getCertificate(certAlias);
            PrivateKey privateKey = (PrivateKey) keystore.getKey(certAlias, password.toCharArray());

            CMSSignedDataGenerator signedDataGenerator = new Pkcs7Generator()
                    .createSignedDataGenerator(algorithm, new X509Certificate[]{certificate}, privateKey);

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

            // todo: default appearance, uses values from signatureDictionary
            // signatureAnnotation.buildDefaultAppearance();

            signatureDictionary.setSignedDataGenerator(signedDataGenerator);

            File out = new File("./src/test/out/SigningTest_signed_document.pdf");
            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192)) {
                document.saveToOutputStream(stream, WriteMode.INCREMENT_UPDATE);
            }

            // open the signed document
            Document modifiedDocument = new Document();
            modifiedDocument.setFile(out.getAbsolutePath());

        } catch (Exception e) {
            // make sure we have no io errors.
            fail("should not be any exceptions");
        }
    }
}
