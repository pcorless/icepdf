package org.icepdf.examples.signatures;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.acroform.FieldDictionaryFactory;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.acroform.signature.handlers.Pkcs12SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.handlers.SimpleCallbackHandler;
import org.icepdf.core.pobjects.acroform.signature.utils.SignatureUtilities;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.SignatureDictionaries;
import org.icepdf.core.util.updater.WriteMode;
import org.icepdf.ri.util.FontPropertiesManager;

import java.awt.*;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * The <code>Pkcs12SignatureCreation</code> class is an example of how to sign a document with a digital signatures
 * using PKCS#12 provider.
 *
 * @since 6.3
 */
public class Pkcs12SignatureCreation {

    static {
        // read/store the font cache.
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    public static void main(String[] args) {
        // Get a file from the command line to open
        String filePath = args[0];
        // path to keystore file, keystore-keypair.pfx
        String keyStorePath = args[1];
        // cert alias
        String alias = args[2];
        // keystore password
        String password = args[3];
        Path path = Path.of(filePath);
        // start the capture
        new Pkcs12SignatureCreation().signDocument(path, keyStorePath, alias, password);
    }

    public void signDocument(Path filePath, String keyStorePath, String certAlias, String password) {
        try {

            Pkcs12SignerHandler pkcs12SignerHandler = new Pkcs12SignerHandler(
                    new File(keyStorePath),
                    certAlias,
                    new SimpleCallbackHandler(password));

            Document document = new Document();
            document.setFile(filePath.toFile().getPath());
            Library library = document.getCatalog().getLibrary();
            SignatureDictionaries signatureDictionaries = library.getSignatureDictionaries();

            // Creat signature annotation
            SignatureWidgetAnnotation signatureAnnotation = (SignatureWidgetAnnotation)
                    AnnotationFactory.buildWidgetAnnotation(
                            document.getPageTree().getLibrary(),
                            FieldDictionaryFactory.TYPE_SIGNATURE,
                            new Rectangle(100, 250, 100, 50));
            document.getPageTree().getPage(0).addAnnotation(signatureAnnotation, true);

            // Add the signatureWidget to catalog
            InteractiveForm interactiveForm = document.getCatalog().getOrCreateInteractiveForm();
            interactiveForm.addField(signatureAnnotation);

            // update dictionary
            SignatureDictionary signatureDictionary = SignatureDictionary.getInstance(signatureAnnotation,
                    SignatureType.SIGNER);
            signatureDictionaries.addCertifierSignature(signatureDictionary);
            signatureDictionary.setSignerHandler(pkcs12SignerHandler);
            signatureDictionary.setName("Tester McTest");
            signatureDictionary.setLocation("Springfield");
            signatureDictionary.setReason("Make sure stuff didn't change");
            signatureDictionary.setDate("D:20240423082733+02'00'");

            // assign cert metadata to dictionary
            SignatureUtilities.updateSignatureDictionary(signatureDictionary, pkcs12SignerHandler.getCertificate());

            String absolutePath = filePath.toFile().getPath();
            String signedFileName = absolutePath.replace(".pdf", "_signed.pdf");

            File out = new File(signedFileName);
            ArrayList<SignatureDictionary> signatures = signatureDictionaries.getSignatures();
            for (SignatureDictionary signature : signatures) {
                signatureDictionaries.setCurrentSignatureDictionary(signature);
                try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192)) {
                    document.saveToOutputStream(stream, WriteMode.INCREMENT_UPDATE);
                }
            }
            signatureDictionaries.setCurrentSignatureDictionary(null);
            document.dispose();
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
