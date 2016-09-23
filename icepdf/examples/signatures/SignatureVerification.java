/*
 * Copyright 2006-2016 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

import org.icepdf.core.exceptions.PDFException;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.acroform.signature.exceptions.SignatureIntegrityException;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.ri.util.FontPropertiesManager;
import org.icepdf.ri.util.PropertiesManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ResourceBundle;

/**
 * The <code>SignatureVerification</code> class is an example of how to validate the signatures
 * associated with a document  A file specified at the command line is opened and the signatures are
 * accessed and validated against the current state of the document.
 *
 * @since 6.1
 */
public class SignatureVerification {

    static {
        // read/store the font cache.
        ResourceBundle messageBundle = ResourceBundle.getBundle(
                PropertiesManager.DEFAULT_MESSAGE_BUNDLE);
        PropertiesManager properties = new PropertiesManager(System.getProperties(),
                ResourceBundle.getBundle(PropertiesManager.DEFAULT_MESSAGE_BUNDLE));
        new FontPropertiesManager(properties, System.getProperties(), messageBundle);
    }

    public static void main(String[] args) {
        // Get a file from the command line to open
        String filePath = args[0];
        // start the capture
        new SignatureVerification().validateDocument(filePath);
    }

    public void validateDocument(String filePath) {
        // open the url
        Document document = new Document();

        try {
            // open the file.
            document.setFile(filePath);

            // signatures can be found off the Catalog as InteractiveForms.
            InteractiveForm interactiveForm = document.getCatalog().getInteractiveForm();
            if (interactiveForm != null) {
                ArrayList<SignatureWidgetAnnotation> signatureFields = interactiveForm.getSignatureFields();
                // found some signatures!
                if (signatureFields != null) {
                    // must be called in order to verify signatures cover full length of document.
                    // signatures cover length of document, there could still be an issue with the signature
                    // but we know the signature(s) cover all the bytes in the file.
                    interactiveForm.isSignaturesCoverDocumentLength();
                    // validate each signature.
                    for (SignatureWidgetAnnotation signatureWidgetAnnotation : signatureFields) {
                        SignatureValidator signatureValidator = signatureWidgetAnnotation.getSignatureValidator();
                        try {
                            // annotation summary
                            SignatureVerification.printSignatureSummary(signatureWidgetAnnotation);
                            // validate the signature and certificate.
                            signatureValidator.validate();
                            // print out some important properties of the validator state.
                            SignatureVerification.printValidationSummary(signatureValidator);
                        } catch (SignatureIntegrityException e) {
                            System.out.println("Signature failed to validate: " + signatureValidator.toString());
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (PDFException ex) {
            System.out.println("Error parsing PDF document " + ex);
        } catch (PDFSecurityException ex) {
            System.out.println("Error encryption not supported " + ex);
        } catch (FileNotFoundException ex) {
            System.out.println("Error file not found " + ex);
        } catch (IOException ex) {
            System.out.println("Error handling PDF document " + ex);
        }
        System.out.println();
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
