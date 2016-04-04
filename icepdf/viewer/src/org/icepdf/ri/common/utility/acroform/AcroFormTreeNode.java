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
package org.icepdf.ri.common.utility.acroform;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.annotations.*;
import org.icepdf.ri.common.utility.signatures.SignatureUtilities;

import javax.security.auth.x500.X500Principal;
import javax.swing.tree.DefaultMutableTreeNode;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * AcroformTreeNode is a simple wrapper used to extract the correct DefaultMutableTreeNode label for the the
 * given FieldDictionary implementation.
 */
@SuppressWarnings("serial")
public class AcroFormTreeNode extends DefaultMutableTreeNode {

    private static final Logger logger =
            Logger.getLogger(AcroFormTreeNode.class.toString());

    private AbstractWidgetAnnotation widgetAnnotation;

    public AcroFormTreeNode(FieldDictionary fieldDictionary, ResourceBundle messageBundle) {
        String text = fieldDictionary.getPartialFieldName();
        if (text != null) {
            MessageFormat messageFormat = new MessageFormat(
                    messageBundle.getString("viewer.utilityPane.acroform.tab.tree.fieldGroup.label"));
            setUserObject(messageFormat.format(new Object[]{text}));
        } else {
            setUserObject(messageBundle.getString("viewer.utilityPane.acroform.tab.tree.fieldGroup.empty.label"));
        }
    }

    public AcroFormTreeNode(AbstractWidgetAnnotation widgetAnnotation, ResourceBundle messageBundle) {
        this.widgetAnnotation = widgetAnnotation;
        String message = null;
        // setup label.
        if (widgetAnnotation instanceof TextWidgetAnnotation) {
            message = applyMessage(widgetAnnotation, messageBundle, "viewer.utilityPane.acroform.tab.tree.text.empty.label");
        } else if (widgetAnnotation instanceof ChoiceWidgetAnnotation) {
            message = applyMessage(widgetAnnotation, messageBundle, "viewer.utilityPane.acroform.tab.tree.choice.empty.label");
        } else if (widgetAnnotation instanceof ButtonWidgetAnnotation) {
            message = applyMessage(widgetAnnotation, messageBundle, "viewer.utilityPane.acroform.tab.tree.button.empty.label");
        } else if (widgetAnnotation instanceof SignatureWidgetAnnotation) {
            SignatureWidgetAnnotation signatureWidgetAnnotation = (SignatureWidgetAnnotation) widgetAnnotation;
            // getting a signatureValidator should give us a pointer the to the signer cert if all goes well.
            SignatureValidator signatureValidator = signatureWidgetAnnotation.getSignatureValidator();
            if (signatureValidator != null) {
                // try and parse out the signer info.
                X509Certificate certificate = signatureValidator.getSignerCertificate();
                X500Principal principal = certificate.getIssuerX500Principal();
                X500Name x500name = new X500Name(principal.getName());
                String commonName = "";
                String emailAddress = "";
                if (x500name.getRDNs() != null) {
                    commonName = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.CN);
                    emailAddress = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.EmailAddress);
                }

                MessageFormat formatter = new MessageFormat(messageBundle.getString(
                        "viewer.utilityPane.acroform.tab.tree.signature.label"));
                message = formatter.format(new Object[]{(commonName != null ? commonName + " " : " "),
                        (emailAddress != null ? "<" + emailAddress + ">" : "")});
            } else {
                message = signatureWidgetAnnotation.getFieldDictionary().getPartialFieldName();
            }
        }
        setUserObject(message);
    }

    private String applyMessage(AbstractWidgetAnnotation abstractWidgetAnnotation, ResourceBundle messageBundle,
                                String message) {
        String text = abstractWidgetAnnotation.getFieldDictionary().getPartialFieldName();
        if (text == null || text.length() == 0) {
            text = messageBundle.getString(message);
        }
        return text;
    }

    public AbstractWidgetAnnotation getWidgetAnnotation() {
        return widgetAnnotation;
    }
}
