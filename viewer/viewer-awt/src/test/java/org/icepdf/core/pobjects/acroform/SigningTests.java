package org.icepdf.core.pobjects.acroform;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.acroform.signature.handlers.BasicSignatureAppearanceCallback;
import org.icepdf.core.pobjects.acroform.signature.handlers.Pkcs12SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.handlers.SimpleCallbackHandler;
import org.icepdf.core.pobjects.acroform.signature.utils.SignatureUtilities;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.WriteMode;
import org.icepdf.ri.util.FontPropertiesManager;
import org.icepdf.ri.util.ViewerPropertiesManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.fail;

public class SigningTests {


    private static ResourceBundle messageBundle =
            ResourceBundle.getBundle(ViewerPropertiesManager.DEFAULT_MESSAGE_BUNDLE);

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
                            new Rectangle(100, 250, 375, 125));
            document.getPageTree().getPage(0).addAnnotation(signatureAnnotation, true);

            // Add the signatureWidget to catalog
            InteractiveForm interactiveForm = document.getCatalog().getOrCreateInteractiveForm();
            interactiveForm.addField(signatureAnnotation);

            // set up signer dictionary
            SignatureDictionary signatureDictionary = SignatureDictionary.getInstance(signatureAnnotation);
            signatureDictionary.setSignerHandler(pkcs12SignerHandler);

            // assign cert metadata to dictionary
            updateSignatureDictionary(signatureDictionary, pkcs12SignerHandler.getCertificate());

            // build basic appearance
            BasicSignatureAppearanceCallback signatureAppearance =
                    new BasicSignatureAppearanceCallback(
                            "Mayor",
                            "Diamond Joe Quimby",
                            createTestSignatureBufferedImage(),
                            messageBundle);
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

    /**
     * Populate signature dictionary with values from the certificate
     *
     * @param signatureDictionary dictionary to populate
     * @param certificate         cert to extract values from
     */
    public void updateSignatureDictionary(SignatureDictionary signatureDictionary, X509Certificate certificate) {
        X500Principal principal = certificate.getSubjectX500Principal();
        X500Name x500name = new X500Name(principal.getName());
        // Set up dictionary using certificate values.
        // https://javadoc.io/static/org.bouncycastle/bcprov-jdk15on/1.70/org/bouncycastle/asn1/x500/style/BCStyle.html
        if (x500name.getRDNs() != null) {
            String commonName = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.CN);
            if (commonName != null) {
                signatureDictionary.setName(commonName);
            }
            String email = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.EmailAddress);
            if (email != null) {
                signatureDictionary.setContactInfo(email);
            }
            ArrayList<String> location = new ArrayList<>(2);
            String state = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.ST);
            if (state != null) {
                location.add(state);
            }
            String country = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.C);
            if (country != null) {
                location.add(country);
            }
            if (!location.isEmpty()) {
                signatureDictionary.setLocation(String.join(", ", location));
            }
        } else {
            throw new IllegalStateException("Certificate has no DRNs data");
        }
        signatureDictionary.setReason("Approval"); // Approval or certification but technically can be anything
        signatureDictionary.setDate(PDate.formatDateTime(new Date()));
    }

    private BufferedImage createTestSignatureBufferedImage() {
        BufferedImage image = new BufferedImage(150, 50, BufferedImage.TYPE_INT_ARGB);
        Graphics2D imageGraphics = image.createGraphics();
        imageGraphics.fillRect(25, 25, 100, 25);
        imageGraphics.dispose();
        return image;
    }


}
