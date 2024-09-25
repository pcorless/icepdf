package org.icepdf.examples.signatures;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.acroform.FieldDictionaryFactory;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.acroform.signature.handlers.Pkcs11SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.handlers.SimpleCallbackHandler;
import org.icepdf.core.pobjects.acroform.signature.utils.SignatureUtilities;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.SignatureManager;
import org.icepdf.core.util.updater.WriteMode;
import org.icepdf.ri.common.views.annotations.signing.BasicSignatureAppearanceCallback;
import org.icepdf.ri.common.views.annotations.signing.SignatureAppearanceModelImpl;
import org.icepdf.ri.util.FontPropertiesManager;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Locale;

/**
 * The <code>Pkcs11SignatureCreation</code> class is an example of how to sign a document with a digital signatures
 * using PKCS#11 provider.  More information on the pkcs11 configuration file can be found here,
 * https://docs.oracle.com/en/java/javase/11/security/pkcs11-reference-guide1.html
 *
 * @since 6.3
 */
public class Pkcs11SignatureCreation {

    static {
        // read/store the font cache.
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    public static void main(String[] args) {
        // Get a file from the command line to open
        String filePath = args[0];
        String providerConfig = args[1];
        BigInteger certSerial = convertHexStringToBigInteger(args[2]);
        String password = args[3];
        Path path = Path.of(filePath);
        // start the capture
        new Pkcs11SignatureCreation().signDocument(path, providerConfig, certSerial, password);
    }

    private static BigInteger convertHexStringToBigInteger(String hexStr) {
        hexStr = hexStr.replace(":", "");
        return new BigInteger(hexStr, 16);
    }

    public void signDocument(Path filePath, String providerConfig, BigInteger certSerial, String password) {
        try {

            Pkcs11SignerHandler pkcs11SignerHandler = new Pkcs11SignerHandler(
                    providerConfig,
                    certSerial,
                    new SimpleCallbackHandler(password));

            Document document = new Document();
            document.setFile(filePath.toFile().getPath());
            Library library = document.getCatalog().getLibrary();
            SignatureManager signatureManager = library.getSignatureDictionaries();

            // Creat signature annotation
            SignatureWidgetAnnotation signatureAnnotation = (SignatureWidgetAnnotation)
                    AnnotationFactory.buildWidgetAnnotation(
                            document.getPageTree().getLibrary(),
                            FieldDictionaryFactory.TYPE_SIGNATURE,
                            new Rectangle(100, 250, 300, 150));
            document.getPageTree().getPage(0).addAnnotation(signatureAnnotation, true);

            // Add the signatureWidget to catalog
            InteractiveForm interactiveForm = document.getCatalog().getOrCreateInteractiveForm();
            interactiveForm.addField(signatureAnnotation);

            // update dictionary
            SignatureDictionary signatureDictionary = SignatureDictionary.getInstance(signatureAnnotation,
                    SignatureType.SIGNER);
            signatureDictionary.setSignerHandler(pkcs11SignerHandler);
            signatureDictionary.setName("Tester McTest");
            signatureDictionary.setLocation("Springfield");
            signatureDictionary.setReason("Make sure stuff didn't change");
            signatureDictionary.setDate("D:20240423082733+02'00'");
            signatureManager.addSignature(signatureDictionary, signatureAnnotation);

            // assign cert metadata to dictionary
            SignatureUtilities.updateSignatureDictionary(signatureDictionary, pkcs11SignerHandler.getCertificate());

            // build basic appearance
            SignatureAppearanceModelImpl signatureAppearanceModel = new SignatureAppearanceModelImpl(library);
            signatureAppearanceModel.setLocale(Locale.ENGLISH);
            signatureAppearanceModel.setName(signatureDictionary.getName());
            signatureAppearanceModel.setContact(signatureDictionary.getContactInfo());
            signatureAppearanceModel.setLocation(signatureDictionary.getLocation());
            signatureAppearanceModel.setSignatureType(signatureDictionary.getReason().equals("Approval") ?
                    SignatureType.SIGNER : SignatureType.CERTIFIER);
            signatureAppearanceModel.setSignatureImageVisible(false);

            BasicSignatureAppearanceCallback signatureAppearance = new BasicSignatureAppearanceCallback();
            signatureAppearance.setSignatureAppearanceModel(signatureAppearanceModel);
            signatureAnnotation.setAppearanceCallback(signatureAppearance);
            signatureAnnotation.resetAppearanceStream(new AffineTransform());

            String absolutePath = filePath.toFile().getPath();
            String signedFileName = absolutePath.replace(".pdf", "_signed.pdf");

            File out = new File(signedFileName);
            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192)) {
                document.saveToOutputStream(stream, WriteMode.INCREMENT_UPDATE);
            }

            // open the signed document
            Document modifiedDocument = new Document();
            modifiedDocument.setFile(out.getAbsolutePath());

        } catch (Exception e) {
            // make sure we have no io errors.
            e.printStackTrace();
        }
    }

    private static void printSignatureSummary(SignatureWidgetAnnotation signatureWidgetAnnotation) {
        SignatureDictionary signatureDictionary = signatureWidgetAnnotation.getSignatureDictionary();
        String signingTime = new PDate(signatureWidgetAnnotation.getLibrary().getSecurityManager(),
                signatureDictionary.getDate()).toString();
        System.out.println("General Info:");
        System.out.println("  Signing time: " + signingTime);
        System.out.println("  Reason: " + signatureDictionary.getReason());
        System.out.println("  Location: " + signatureDictionary.getLocation());
    }

    /**
     * Print out some summary data of the validator results.
     *
     * @param signatureValidator validator to show properties data.
     */
    private static void printValidationSummary(SignatureValidator signatureValidator) {
        System.out.println("Singer Info:");
        if (signatureValidator.isCertificateChainTrusted()) {
            System.out.println("   Path validation checks were successful");
        } else {
            System.out.println("   Path validation checks were unsuccessful");
        }
        if (!signatureValidator.isCertificateChainTrusted() || signatureValidator.isRevocation()) {
            System.out.println("   Revocation checking was not performed");
        } else {
            System.out.println("   Signer's certificate is valid and has not been revoked");
        }
        System.out.println("Validity Summary:");
        if (!signatureValidator.isSignedDataModified() && !signatureValidator.isDocumentDataModified()) {
            System.out.println("   Document has not been modified since it was signed");
        } else if (!signatureValidator.isSignedDataModified() && signatureValidator.isDocumentDataModified() && signatureValidator.isSignaturesCoverDocumentLength()) {
            System.out.println("   This version of the document is unaltered but subsequent changes have been made");
        } else if (!signatureValidator.isSignaturesCoverDocumentLength()) {
            System.out.println("   Document has been altered or corrupted sing it was singed");
        }
        if (!signatureValidator.isCertificateDateValid()) {
            System.out.println("   Signers certificate has expired");
        }
        if (signatureValidator.isEmbeddedTimeStamp()) {
            System.out.println("   Signature included an embedded timestamp but it could not be validated");
        } else {
            System.out.println("   Signing time is from the clock on this signer's computer");
        }
        if (signatureValidator.isSelfSigned()) {
            System.out.println("   Document is self signed");
        }
        System.out.println();
    }
}
