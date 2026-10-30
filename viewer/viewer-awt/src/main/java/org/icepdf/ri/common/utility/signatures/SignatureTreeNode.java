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
package org.icepdf.ri.common.utility.signatures;

import org.icepdf.ri.common.views.annotations.signatures.SignatureValidationService;
import org.icepdf.ri.common.views.annotations.signatures.SignatureValidationStatus;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.SignatureFieldDictionary;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.acroform.signature.exceptions.SignatureIntegrityException;
import org.icepdf.core.pobjects.acroform.signature.utils.SignatureUtilities;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.ri.images.IconPack;
import org.icepdf.ri.images.Images;

import javax.security.auth.x500.X500Principal;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * Represents a signatures in the signature tree.  The node can be expanded to show more details about the
 * signer, validity and certificate details.
 */
@SuppressWarnings("serial")
public class SignatureTreeNode extends DefaultMutableTreeNode {

    private static final Logger logger =
            Logger.getLogger(SignatureTreeNode.class.getName());

    private final ResourceBundle messageBundle;
    private final SignatureWidgetAnnotation signatureWidgetAnnotation;
    private SignatureValidator signatureValidator;
    // a validation of this node's signature is running; the node shows "validating".
    private boolean verifyingSignature;
    // the last validation failed outright; the node shows the error label.
    private boolean validationFailed;

    private String location = null;
    private String reason = null;
    private String commonName = null;
    private String emailAddress = null;

    /**
     * Creates a new instance of an OutlineItemTreeNode.  Validation is not run here: the node shows
     * "validating" until its validator has a result, see {@link #refreshSignerNode()}.
     *
     * @param signatureWidgetAnnotation Contains PDF Outline signatureWidgetAnnotation data
     * @param messageBundle             i18n message bundle
     */
    public SignatureTreeNode(SignatureWidgetAnnotation signatureWidgetAnnotation, ResourceBundle messageBundle) {
        super();
        this.signatureWidgetAnnotation = signatureWidgetAnnotation;
        this.messageBundle = messageBundle;

        try {
            readSigner();
        } catch (RuntimeException e) {
            logger.warning("There was an issue creating a node for the signature: " +
                    signatureWidgetAnnotation.toString());
            validationFailed = true;
        }
        refreshSignerNode();
    }

    // signer details known before validation: the dictionary entries and the signer certificate.
    private void readSigner() {
        SignatureFieldDictionary fieldDictionary = signatureWidgetAnnotation.getFieldDictionary();
        SignatureDictionary signatureDictionary = signatureWidgetAnnotation.getSignatureDictionary();
        if (fieldDictionary != null) {
            location = signatureDictionary.getLocation();
            reason = signatureDictionary.getReason();
            // getting a signatureValidator should give us a pointer the to the signer cert if all goes well.
            signatureValidator = signatureWidgetAnnotation.getSignatureValidator();
            if (signatureValidator == null) {
                validationFailed = true;
                return;
            }
            X509Certificate certificate = signatureValidator.getSignerCertificate();
            X500Principal principal = certificate.getSubjectX500Principal();
            X500Name x500name = new X500Name(principal.getName());
            if (x500name.getRDNs() != null) {
                commonName = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.CN);
                emailAddress = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.EmailAddress);
            }
        }
    }

    /**
     * Runs a fresh validation of the signature this node represents.  It can block on the network,
     * so call it from a worker thread, then {@link #refreshSignerNode()} on the AWT thread.
     *
     * @throws SignatureIntegrityException security exception if validation failed.
     */
    public void validateSignatureNode() throws SignatureIntegrityException {
        if (signatureValidator != null) {
            signatureValidator.validate(SignatureValidationService.options());
        }
    }

    /**
     * The validator of this node's signature.
     *
     * @return validator, or null if the signature couldn't be read
     */
    public SignatureValidator getSignatureValidator() {
        return signatureValidator;
    }

    /**
     * Builds a rather complicated tree node and child nodes to show various properties of a a signer and the
     * corresponding certificate.  The main purpose is to display to the end user if the certificate is valid and
     * can be trusted as well as showing document permissions and if the document has been modified since it was
     * singed.
     * <br>
     * - Singed by "signer name"
     * |
     * - Signature is &lt;valid|invalid&gt;
     * |
     * - This version of the document has &lt;not&gt; been altered
     * - SignatureSigner's identity is &lt;valid|invalid&gt;
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
     * - Last Checked: &lt;verification last run time&gt;
     * - Field Name: &lt;field name&gt; on page X (clickable, takes to page and applies focus).
     */
    public synchronized void refreshSignerNode() {
        if (validationFailed || signatureValidator == null) {
            MessageFormat formatter = new MessageFormat(messageBundle.getString(
                    "viewer.utilityPane.signatures.tab.certTree.error.label"));
            setUserObject(formatter.format(new Object[]{(commonName != null ? commonName + " " : " "),
                    (emailAddress != null ? "<" + emailAddress + ">" : "")}));
            removeAllChildren();
        } else if (isValidated()) {
            // should have enough data to build a out a full signature node.
            MessageFormat formatter = new MessageFormat(messageBundle.getString(
                    "viewer.utilityPane.signatures.tab.certTree.rootSigned.label"));
            setUserObject(formatter.format(new Object[]{(commonName != null ? commonName + " " : " "),
                    (emailAddress != null ? "<" + emailAddress + ">" : "")}));
            removeAllChildren();
            // signature validity
            buildSignatureValidity(this);
            // add signature details
            buildSignatureDetails(this);
            // tack on last verified date and link to annotation if present
            buildVerifiedDateAndFieldLink(this);
        } else {
            // build out a simple validating message
            removeAllChildren();
            MessageFormat formatter = new MessageFormat(messageBundle.getString(
                    "viewer.utilityPane.signatures.tab.certTree.rootValidating.label"));
            setUserObject(formatter.format(new Object[]{(commonName != null ? commonName + " " : " "),
                    (emailAddress != null ? "<" + emailAddress + ">" : "")}));
        }
    }

    // set one of the three icon's to represent the validity status of the signature node.
    protected Icon getRootNodeValidityIcon() {
        // no verdict until there is one: a validator that hasn't run reads as modified.
        if (!isValidated()) return null;
        return Images.getSingleIcon(SignatureValidationStatus.validityOf(signatureValidator).icon,
                IconPack.Variant.NONE, Images.IconSize.MINI);
    }

    // builds otu the validity tree node.
    private void buildSignatureValidity(DefaultMutableTreeNode root) {
        // figure out the opening messages.
        String validity = "viewer.utilityPane.signatures.tab.certTree.cert."
                + SignatureValidationStatus.validityOf(signatureValidator).key + ".label";
        SigPropertyTreeNode rootValidityDetails = new SigPropertyTreeNode(
                messageBundle.getString(validity));

        // document modification
        String documentModified = "viewer.utilityPane.signatures.tab.certTree.doc."
                + SignatureValidationStatus.documentStateOf(signatureValidator).key + ".label";
        rootValidityDetails.add(new SigPropertyTreeNode(messageBundle.getString(documentModified)));
        // trusted certification
        String certificateTrusted = "viewer.utilityPane.signatures.tab.certTree.signature.identity."
                + SignatureValidationStatus.identityOf(signatureValidator).key + ".label";
        rootValidityDetails.add(new SigPropertyTreeNode(messageBundle.getString(certificateTrusted)));
        // signature time.
        String signatureTime = "viewer.utilityPane.signatures.tab.certTree.signature.time.local.label";
        if (signatureValidator.isEmbeddedTimeStamp()) {
            signatureTime = "viewer.utilityPane.signatures.tab.certTree.signature.time.embedded.label";
        }
        rootValidityDetails.add(new SigPropertyTreeNode(messageBundle.getString(signatureTime)));
        root.add(rootValidityDetails);
    }

    // builds out the signature details
    private void buildSignatureDetails(DefaultMutableTreeNode root) {
        SigPropertyTreeNode rootSignatureDetails = new SigPropertyTreeNode(
                messageBundle.getString("viewer.utilityPane.signatures.tab.certTree.signature.details.label"));
        // try and add the reason
        if (reason != null && reason.length() > 0) {
            MessageFormat messageFormat = new MessageFormat(messageBundle.getString(
                    "viewer.utilityPane.signatures.tab.certTree.signature.details.reason.label"));
            rootSignatureDetails.add(new SigPropertyTreeNode(messageFormat.format(new Object[]{reason})));
        }
        // add the location
        if (location != null && location.length() > 0) {
            MessageFormat messageFormat = new MessageFormat(messageBundle.getString(
                    "viewer.utilityPane.signatures.tab.certTree.signature.details.location.label"));
            rootSignatureDetails.add(new SigPropertyTreeNode(messageFormat.format(new Object[]{location})));
        }
        // add link for bringing up the certificate details.
        rootSignatureDetails.add(new SignatureCertTreeNode(
                messageBundle.getString("viewer.utilityPane.signatures.tab.certTree.signature.details.full.label"),
                signatureValidator.getCertificateChain(),
                getRootNodeValidityIcon()));
        root.add(rootSignatureDetails);
    }

    private void buildVerifiedDateAndFieldLink(DefaultMutableTreeNode root) {
        if (signatureValidator != null && signatureValidator.getLastValidated() != null) {
            MessageFormat messageFormat = new MessageFormat(messageBundle.getString(
                    "viewer.utilityPane.signatures.tab.certTree.signature.lastChecked.label"));
            SigPropertyTreeNode lastChecked =
                    new SigPropertyTreeNode(messageFormat.format(new Object[]{
                            new PDate(PDate.formatDateTime(signatureValidator.getLastValidated())).toString()}));
            lastChecked.setAllowsChildren(false);
            root.add(lastChecked);
        }
    }

    /**
     * Whether the node has a validation result to show: one has run and none is running.
     *
     * @return true if the full node can be built
     */
    public synchronized boolean isValidated() {
        return !verifyingSignature && !validationFailed && signatureValidator != null
                && signatureValidator.isValidated();
    }

    public synchronized boolean isVerifyingSignature() {
        return verifyingSignature;
    }

    /**
     * Marks a validation of this node's signature as running (the node shows "validating") or
     * finished.  Call {@link #refreshSignerNode()} after.
     *
     * @param verifyingSignature true while a validation runs
     */
    public synchronized void setVerifyingSignature(boolean verifyingSignature) {
        this.verifyingSignature = verifyingSignature;
    }

    /**
     * Records how the last validation ended.  Call {@link #refreshSignerNode()} after.
     *
     * @param failed true if it failed outright
     */
    public synchronized void setValidationFailed(boolean failed) {
        this.validationFailed = failed;
    }

    public SignatureWidgetAnnotation getOutlineItem() {
        return signatureWidgetAnnotation;
    }

}
