/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common.views.annotations.signatures;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.SignatureFieldDictionary;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.acroform.signature.utils.SignatureUtilities;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.ri.images.IconPack;
import org.icepdf.ri.images.Images;

import javax.security.auth.x500.X500Principal;
import javax.swing.*;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Common panel construct for show validation status of a given signature and validator.
 */
public class SignatureValidationStatus {

    private String validity;
    private String singedBy;
    private String documentModified;
    private String certificateTrusted;
    private String signatureTime;
    private String emailAddress;
    private String organization;
    private String commonName;
    private final Icon validityIcon;

    private final String dictionaryName;
    private final String dictionaryLocation;
    private final String dictionaryReason;
    private final String dictionaryContact;
    private final String dictionaryDate;

    public SignatureValidationStatus(ResourceBundle messageBundle,
                                     SignatureWidgetAnnotation signatureWidgetAnnotation,
                                     SignatureValidator signatureValidator) {

        // build out the string that we need to display
        validity = messageBundle.getString("viewer.annotation.signature.validation.common."
                + validityOf(signatureValidator).key + ".label");

        // signed by
        singedBy = messageBundle.getString("viewer.annotation.signature.validation.common.notAvailable.label");
        validateSignatureNode(signatureWidgetAnnotation, signatureValidator);
        MessageFormat formatter = new MessageFormat(messageBundle.getString(
                "viewer.annotation.signature.validation.common.signedBy.label"));
        singedBy = formatter.format(new Object[]{(commonName != null ? commonName + " " : " "),
                (emailAddress != null ? "<" + emailAddress + ">" : "")});

        // document modification
        documentModified = messageBundle.getString("viewer.annotation.signature.validation.common.doc."
                + documentStateOf(signatureValidator).key + ".label");

        // trusted certification
        certificateTrusted = messageBundle.getString("viewer.annotation.signature.validation.common.identity."
                + identityOf(signatureValidator).key + ".label");

        // signature time.
        signatureTime = "viewer.annotation.signature.validation.common.time.local.label";
        if (signatureValidator.isSignerTimeValid()) {
            signatureTime = "viewer.annotation.signature.validation.common.time.embedded.label";
        }
        signatureTime = messageBundle.getString(signatureTime);

        validityIcon = getLargeValidityIcon(signatureValidator);

        // signature dictionary common names.
        SignatureDictionary signatureDictionary = signatureWidgetAnnotation.getSignatureDictionary();
        // grab some signer properties right from the annotations dictionary.
        dictionaryName = signatureDictionary.getName();
        dictionaryLocation = signatureDictionary.getLocation();
        dictionaryReason = signatureDictionary.getReason();
        dictionaryContact = signatureDictionary.getContactInfo();
        dictionaryDate = signatureDictionary.getDate();
    }

    private void validateSignatureNode(SignatureWidgetAnnotation signatureWidgetAnnotation,
                                       SignatureValidator signatureValidator) {
        SignatureFieldDictionary fieldDictionary = signatureWidgetAnnotation.getFieldDictionary();

        if (fieldDictionary != null) {
            // try and parse out the signer info.
            X509Certificate certificate = signatureValidator.getSignerCertificate();
            X500Principal principal = certificate.getSubjectX500Principal();
            X500Name x500name = new X500Name(principal.getName());
            if (x500name.getRDNs() != null) {
                commonName = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.CN);
                organization = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.O);
                emailAddress = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.EmailAddress);
            }
        }
    }

    // set one of the three icon's to represent the validity status of the signature node.
    protected Icon getLargeValidityIcon(SignatureValidator signatureValidator) {
        return Images.getSingleIcon(validityOf(signatureValidator).icon, IconPack.Variant.NONE, Images.IconSize.HUGE);
    }

    /**
     * A signature's overall validity, shared by the validation dialog, the signature properties and
     * the signatures panel so the icon and the words always agree.
     */
    public enum Validity {
        /** The signed content is intact and the signer's identity is trusted. */
        VALID("valid", "signature_valid"),
        /** The signed content is intact but the signer's identity can't be verified. */
        UNKNOWN("unknown", "signature_caution"),
        /** The signed content was altered, or the signer's certificate was revoked. */
        INVALID("invalid", "signature_invalid");

        /** Message key part and icon name. */
        public final String key;
        public final String icon;

        Validity(String key, String icon) {
            this.key = key;
            this.icon = icon;
        }
    }

    /** What happened to the document after this signature. */
    public enum DocumentState {
        UNMODIFIED("unmodified"),
        /** Later revisions (form fill-in, comments, more signatures); the signed version is intact. */
        SUBSEQUENT_CHANGES("modified"),
        /** The bytes this signature covers changed: the signature is broken. */
        ALTERED("major");

        public final String key;

        DocumentState(String key) {
            this.key = key;
        }
    }

    /** What is known of the signer's identity. */
    public enum Identity {
        VALID("valid"),
        UNKNOWN("unknown"),
        REVOKED("revoked");

        public final String key;

        Identity(String key) {
            this.key = key;
        }
    }

    /**
     * Validity from the signed content and the certificate, never from whether later signatures cover
     * the rest of the file: later revisions (form fill-in, more signatures) don't break an earlier
     * signature, they are reported by {@link #documentStateOf}.
     */
    public static Validity validityOf(SignatureValidator validator) {
        if (validator.isSignedDataModified() || validator.isRevocation()) return Validity.INVALID;
        return validator.isCertificateChainTrusted() ? Validity.VALID : Validity.UNKNOWN;
    }

    public static DocumentState documentStateOf(SignatureValidator validator) {
        if (validator.isSignedDataModified()) return DocumentState.ALTERED;
        return validator.isDocumentDataModified() ? DocumentState.SUBSEQUENT_CHANGES : DocumentState.UNMODIFIED;
    }

    public static Identity identityOf(SignatureValidator validator) {
        if (validator.isRevocation()) return Identity.REVOKED;
        return validator.isCertificateChainTrusted() ? Identity.VALID : Identity.UNKNOWN;
    }

    public Icon getValidityIcon() {
        return validityIcon;
    }

    public String getValidity() {
        return validity;
    }

    public String getSingedBy() {
        return singedBy;
    }

    public String getDocumentModified() {
        return documentModified;
    }

    public String getCertificateTrusted() {
        return certificateTrusted;
    }

    public String getSignatureTime() {
        return signatureTime;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getCommonName() {
        return commonName;
    }

    public String getOrganization() {
        return organization;
    }

    public String getDictionaryName() {
        return dictionaryName;
    }

    public String getDictionaryLocation() {
        return dictionaryLocation;
    }

    public String getDictionaryReason() {
        return dictionaryReason;
    }

    public String getDictionaryContact() {
        return dictionaryContact;
    }

    public String getDictionaryDate() {
        return dictionaryDate;
    }
}
