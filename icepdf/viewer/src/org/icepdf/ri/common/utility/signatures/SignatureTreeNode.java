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
package org.icepdf.ri.common.utility.signatures;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.SignatureFieldDictionary;
import org.icepdf.core.pobjects.acroform.SignatureHandler;
import org.icepdf.core.pobjects.acroform.signature.Validator;
import org.icepdf.core.pobjects.acroform.signature.exceptions.SignatureIntegrityException;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.ri.images.Images;

import javax.security.auth.x500.X500Principal;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.logging.Logger;

/**
 * Represents a signatures in the signature tree.  The node can be expanded to show more details about the
 * signer, validity and certificate details.
 */
@SuppressWarnings("serial")
public class SignatureTreeNode extends DefaultMutableTreeNode {

    private static final Logger logger =
            Logger.getLogger(SignatureTreeNode.class.toString());

    private SignatureWidgetAnnotation signatureWidgetAnnotation;
    private Validator validator;
    private boolean verifyingSignature;
    private Date lastVerified;

    private String location = null;
    private String reason = null;
    private String contact = null;
    private String name = null;
    private String commonName = null;
    private String organization = null;
    private String emailAddress = null;
    private String date = null;

    /**
     * Creates a new instance of an OutlineItemTreeNode
     *
     * @param signatureWidgetAnnotation Contains PDF Outline signatureWidgetAnnotation data
     */
    public SignatureTreeNode(SignatureWidgetAnnotation signatureWidgetAnnotation) {
        super();
        this.signatureWidgetAnnotation = signatureWidgetAnnotation;

        try {
            validateSignatureNode();
        } catch (SignatureIntegrityException e) {
            logger.warning("There was an issue creating a node for the signature: " +
                    signatureWidgetAnnotation.toString());
            // build a user node object to report the error.
            setUserObject("Signer certificate could not be validated " +
                    (commonName != null ? commonName + " " : " ") +
                    (emailAddress != null ? "<" + emailAddress + ">" : ""));
        }
    }

    /**
     * Validates the signatures represented by this tree node.  This method is called by a worker thread
     * and once validation is complete the notes states is updated with a call to {@link #refreshSignerNode()}
     *
     * @return true node displaying various properties of
     * @throws SignatureIntegrityException
     */
    private void validateSignatureNode() throws SignatureIntegrityException {

        SignatureFieldDictionary fieldDictionary = signatureWidgetAnnotation.getFieldDictionary();
        SignatureDictionary signatureDictionary = signatureWidgetAnnotation.getSignatureDictionary();
        if (fieldDictionary != null) {

            // grab some signer properties right from the annotations dictionary.
            name = signatureDictionary.getName();
            location = signatureDictionary.getLocation();
            reason = signatureDictionary.getReason();
            contact = signatureDictionary.getContactInfo();
            date = signatureDictionary.getDate();

            // getting a validator should give us a pointer the to the signer cert if all goes well.
            SignatureHandler signatureHandler = fieldDictionary.getLibrary().getSignatureHandler();
            validator = signatureHandler.validateSignature(fieldDictionary);
            // try and parse out the signer info.
            X509Certificate certificate = validator.getSignerCertificate();
            X500Principal principal = certificate.getIssuerX500Principal();
            X500Name x500name = new X500Name(principal.getName());
            if (x500name.getRDNs() != null) {
                commonName = parseRelativeDistinguishedName(x500name, BCStyle.CN);
                organization = parseRelativeDistinguishedName(x500name, BCStyle.O);
                emailAddress = parseRelativeDistinguishedName(x500name, BCStyle.EmailAddress);
            }
            // Start validation process.
            // todo move this off the awt thread as it will likely take a while.  We'll need to create
            // an executer service to queue up the one or more signatures for validation. which when
            // done will update the node with the retrieved data.
            validator.validate();
            setVerifyingSignature(true);
            lastVerified = new Date();
        }
        // build the tree with a "validating signature message"
        refreshSignerNode();

    }

    /**
     * Builds a rather complicated tree node and child nodes to show various properties of a a signer and the
     * corresponding certificate.  The main purpose is to display to the end user if the certificate is valid and
     * can be trusted as well as showing document permissions and if the document has been modified since it was
     * singed.
     * <p/>
     * - Singed by "signer name"
     * |
     * - Signature is <valid|invalid>
     * |
     * - This version of the document has <not> been altered
     * - Signer's identity is <valid|invalid>
     * - Signature includes an embedded timestamp | Signing is from the clock of the signer's computer.
     * - Permissions
     * |
     * - No changes allowed
     * - Field values can be changed
     * - needs more research
     * - Signature Details
     * |
     * - Reason:
     * - Location:
     * - Certificate Details (clickable, loads certificate dialog)
     * - Last Checked: <verification last run time>
     * - Field Name: <field name> on page X (clickable, takes to page and applies focus).
     *
     * @return true node displaying various properties of
     * @throws SignatureIntegrityException
     */
    public synchronized void refreshSignerNode() {
        DefaultMutableTreeNode rootSignatureNode;
        if (isVerifyingSignature()) {
            // should have enough data to build a out a full signature node.
            setUserObject("Signed by " +
                    (commonName != null ? commonName + " " : " ") +
                    (emailAddress != null ? "<" + emailAddress + ">" : ""));
            // signature validity
            buildSignatureValidity(this);
            // add signature details
            buildSignatureDetails(this);
            // tack on last verified date and link to annotation if present
            buildVerifiedDateAndFieldLink(this);
        } else {
            // build out a simple validating message
            setUserObject("Validating signature " +
                    (commonName != null ? commonName + " " : " ") +
                    (emailAddress != null ? "<" + emailAddress + ">" : ""));

        }
    }

    // set one of the three icon's to represent the validity status of the signature node.
    protected Icon getRootNodeValidityIcon() {
        if (!validator.isDocumentModified() && validator.isCertificateTrusted()) {
            return new ImageIcon(Images.get("signatue_valid.png"));
        } else if (!validator.isDocumentModified()) {
            return new ImageIcon(Images.get("signature_caution.png"));
        } else {
            return new ImageIcon(Images.get("signature_invalid.png"));
        }
    }

    // builds otu the validity tree node.
    private void buildSignatureValidity(DefaultMutableTreeNode root) {
        // figure out the opening messages.
        String validity = "Signature is invalid:";
        if (!validator.isDocumentModified() && !validator.isCertificateTrusted()) {
            validity = "Signature validity is unknown:";
        } else if (!validator.isDocumentModified() && validator.isCertificateTrusted()) {
            validity = "Signature is valid:";
        }
        SigPropertyTreeNode rootValidityDetails = new SigPropertyTreeNode(validity);

        // document modification
        String documentModified = "Document has not been modified since it was signed";
        if (!validator.isDocumentModified()) {
            documentModified = "Document has not been modified since it was certified";
        }
        rootValidityDetails.add(new SigPropertyTreeNode(documentModified));
        // trusted certification
        String certificateTrusted = "Signer's identity is unknown";
        if (validator.isCertificateTrusted()) {
            if (validator.isRevocationCheck()) {
                certificateTrusted = "Signature is valid, but revocation of the signer's identity could not be checked";
            } else {
                certificateTrusted = "Signer's identity is valid";
            }
        }
        rootValidityDetails.add(new SigPropertyTreeNode(certificateTrusted));
        // signature time.
        String signatureTime = "Signing time is from the clock on this signer's computer.";
        if (validator.isSignerTimeValid()) {
            signatureTime = "Signature is LTV enabled";
        }
        rootValidityDetails.add(new SigPropertyTreeNode(signatureTime));
        root.add(rootValidityDetails);
    }

    // builds out the signature details
    private void buildSignatureDetails(DefaultMutableTreeNode root) {
        SigPropertyTreeNode rootSignatureDetails = new SigPropertyTreeNode("Signature Details");
        // try and add the reason
        if (reason != null && reason.length() > 0) {
            rootSignatureDetails.add(new SigPropertyTreeNode("Reason: " + reason));
        }
        // add the location
        if (location != null && location.length() > 0) {
            rootSignatureDetails.add(new SigPropertyTreeNode("Location: " + location));
        }
        // add link for bringing up the certificate details.
        rootSignatureDetails.add(new SigPropertyTreeNode("Certificate Details..."));
        root.add(rootSignatureDetails);
    }

    private void buildVerifiedDateAndFieldLink(DefaultMutableTreeNode root) {
        if (lastVerified != null) {
            SigPropertyTreeNode lastChecked =
                    new SigPropertyTreeNode("Last Checked: " +
                            new PDate(signatureWidgetAnnotation.getLibrary().getSecurityManager(),
                                    PDate.formatDateTime(lastVerified)).toString());
            lastChecked.setAllowsChildren(false);
            root.add(lastChecked);
        }
        // todo add new custom node that can navigate to page annotation is on.
    }

    public synchronized boolean isVerifyingSignature() {
        return verifyingSignature;
    }

    /**
     * Flat to indicated that the validation process has completed and the state variables are in a completed
     * state.  This doesn't mean that the signature is valid just the validation process is complete.
     *
     * @param verifyingSignature true to indicate the validation process is complete, otherwise falls.
     */
    public void setVerifyingSignature(boolean verifyingSignature) {
        this.verifyingSignature = verifyingSignature;
    }

    private String parseRelativeDistinguishedName(X500Name rdName, ASN1ObjectIdentifier commonCode) {
        RDN[] rdns = rdName.getRDNs(commonCode);
        if (rdns != null && rdns.length > 0 && rdns[0].getFirst() != null) {
            return rdns[0].getFirst().getValue().toString();
        }
        return null;
    }

    public SignatureWidgetAnnotation getOutlineItem() {
        return signatureWidgetAnnotation;
    }

}
