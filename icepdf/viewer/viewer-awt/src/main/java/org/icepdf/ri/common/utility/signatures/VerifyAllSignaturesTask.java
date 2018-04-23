/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.signature.exceptions.SignatureIntegrityException;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.ri.common.AbstractTask;
import org.icepdf.ri.common.AbstractWorkerPanel;
import org.icepdf.ri.common.views.Controller;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * VerifyAllSignaturesTask is an abstract task for offloading the verification of digital signatures from the AWT thread.
 */
public class VerifyAllSignaturesTask extends AbstractTask<Void, Object> {

    private static final Logger logger = Logger.getLogger(VerifyAllSignaturesTask.class.toString());

    public VerifyAllSignaturesTask(Controller controller, AbstractWorkerPanel workerPanel, ResourceBundle messageBundle) {
        super(controller, workerPanel, messageBundle);
    }

    @Override
    protected Void doInBackground() {
        MessageFormat messageFormat = new MessageFormat(
                messageBundle.getString("viewer.utilityPane.signatures.verify.initializingMessage.label"));
        try {
            Document document = controller.getDocument();
            InteractiveForm interactiveForm = document.getCatalog().getInteractiveForm();
            // checks and flags each annotation to indicate if the signatures cover the whole document
            interactiveForm.isSignaturesCoverDocumentLength();
            final ArrayList<SignatureWidgetAnnotation> signatures = interactiveForm.getSignatureFields();
            boolean unsignedFields = false;
            // build out the tree
            if (signatures.size() > 0) {
                // iterate over the signature in the document.
                for (int i = 0, max = signatures.size(); i < max; i++) {
                    // break if needed
                    if (isCancelled()) {
                        break;
                    }
                    taskStatusMessage = messageFormat.format(new Object[]{i + 1, signatures.size()});

                    SignatureWidgetAnnotation signatureWidgetAnnotation = signatures.get(i);
                    SignatureDictionary signatureDictionary = signatureWidgetAnnotation.getSignatureDictionary();
                    if (signatureDictionary.getEntries().size() > 0) {
                        try {
                            signatureWidgetAnnotation.getSignatureValidator().validate();
                            signatureWidgetAnnotation.getSignatureValidator().isSignaturesCoverDocumentLength();
                            // add a new node to the tree.
                        } catch (SignatureIntegrityException e) {
                            logger.log(Level.WARNING, "Error verifying signature.", e);
                        }
                        // add the node to the signature panel tree but on the awt thread.
                        publish(signatureWidgetAnnotation);
                    } else {
                        // found some unsigned fields.
                        unsignedFields = true;
                    }
                    Thread.yield();
                }
                // build out unsigned fields
                if (unsignedFields) {
                    publish(signatures);
                }
            }
            // update the dialog and end the task
            taskStatusMessage = messageBundle.getString("viewer.utilityPane.signatures.verify.completeMessage.label");
        } catch (Exception e) {
            logger.log(Level.FINER, "Error verifying signatures.", e);
        }
        return null;
    }

    @Override
    protected void process(List<Object> chunks) {
        for (Object chunk : chunks) {
            if (chunk instanceof SignatureWidgetAnnotation) {
                ((SignaturesHandlerPanel) workerPanel).addSignature((SignatureWidgetAnnotation) chunk);
            } else if (chunk instanceof ArrayList) {
                ((SignaturesHandlerPanel) workerPanel).addUnsignedSignatures((ArrayList) chunk);
            }
        }
    }

    @Override
    protected void done() {
        workerPanel.validate();
    }

}
