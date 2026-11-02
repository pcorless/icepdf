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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidationOptions;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.acroform.signature.exceptions.SignatureIntegrityException;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.ri.common.AbstractTask;
import org.icepdf.ri.common.AbstractWorkerPanel;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.annotations.signatures.SignatureValidationService;

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

    private static final Logger logger = Logger.getLogger(VerifyAllSignaturesTask.class.getName());

    public VerifyAllSignaturesTask(Controller controller, AbstractWorkerPanel workerPanel, ResourceBundle messageBundle) {
        super(controller, workerPanel, messageBundle);
    }

    // a signature whose validation has finished, and whether it failed outright.
    static final class Validated {
        final SignatureWidgetAnnotation annotation;
        final boolean failed;

        Validated(SignatureWidgetAnnotation annotation, boolean failed) {
            this.annotation = annotation;
            this.failed = failed;
        }
    }

    // progress through the signed fields.
    static final class Progress {
        final int done;
        final int total;
        final String message;

        Progress(int done, int total, String message) {
            this.done = done;
            this.total = total;
            this.message = message;
        }
    }

    /**
     * Lists every signed field in the tree at once, marked as validating, then validates them one
     * by one and updates each node as its result comes in.  Validation can wait on the network, so
     * it all happens here, off the AWT thread; the nodes themselves never validate.
     */
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
            List<SignatureWidgetAnnotation> signed = new ArrayList<>();
            boolean unsignedFields = false;
            for (SignatureWidgetAnnotation signatureWidgetAnnotation : signatures) {
                signatureWidgetAnnotation.init();
                SignatureDictionary signatureDictionary = signatureWidgetAnnotation.getSignatureDictionary();
                if (signatureDictionary.getEntries().size() > 0) {
                    signed.add(signatureWidgetAnnotation);
                    // add a node, showing "validating", to the tree on the awt thread.
                    publish(signatureWidgetAnnotation);
                } else {
                    unsignedFields = true;
                }
            }
            // build out unsigned fields
            if (unsignedFields) {
                publish(signatures);
            }

            SignatureValidationOptions options = SignatureValidationService.options();
            for (int i = 0, max = signed.size(); i < max; i++) {
                if (isCancelled()) {
                    break;
                }
                taskStatusMessage = messageFormat.format(new Object[]{i + 1, max});
                publish(new Progress(i, max, taskStatusMessage));
                SignatureWidgetAnnotation signatureWidgetAnnotation = signed.get(i);
                boolean failed = false;
                try {
                    SignatureValidator validator = signatureWidgetAnnotation.getSignatureValidator();
                    if (validator != null) {
                        validator.ensureValidated(options);
                        validator.isSignaturesCoverDocumentLength();
                    }
                } catch (SignatureIntegrityException | RuntimeException e) {
                    logger.log(Level.WARNING, "Error verifying signature.", e);
                    failed = true;
                }
                publish(new Validated(signatureWidgetAnnotation, failed));
            }
            // update the dialog and end the task
            taskStatusMessage = messageBundle.getString("viewer.utilityPane.signatures.verify.completeMessage.label");
            publish(new Progress(signed.size(), signed.size(), taskStatusMessage));
        } catch (Exception e) {
            logger.log(Level.FINER, "Error verifying signatures.", e);
        }
        return null;
    }

    @Override
    protected void process(List<Object> chunks) {
        SignaturesHandlerPanel panel = (SignaturesHandlerPanel) workerPanel;
        for (Object chunk : chunks) {
            if (chunk instanceof SignatureWidgetAnnotation) {
                panel.addSignature((SignatureWidgetAnnotation) chunk);
            } else if (chunk instanceof Validated) {
                Validated validated = (Validated) chunk;
                panel.signatureValidated(validated.annotation, validated.failed);
            } else if (chunk instanceof Progress) {
                Progress progress = (Progress) chunk;
                panel.startProgressControls(progress.total);
                panel.updateProgressControls(progress.done, progress.message);
            } else if (chunk instanceof ArrayList) {
                panel.addUnsignedSignatures((ArrayList) chunk);
            }
        }
    }

    @Override
    protected void done() {
        ((SignaturesHandlerPanel) workerPanel).endProgressControls();
        workerPanel.validate();
    }

}
