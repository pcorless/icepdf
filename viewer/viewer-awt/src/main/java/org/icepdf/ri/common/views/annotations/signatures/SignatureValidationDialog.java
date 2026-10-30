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

import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.ri.common.EscapeJDialog;

import javax.swing.*;
import java.awt.*;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * The SignatureValidationDialog shows a summary of the validation status of a signature.  This
 * is very similar to the signature tree view in the Signature utility tab.
 */
public class SignatureValidationDialog extends EscapeJDialog {

    private static final Logger logger =
            Logger.getLogger(SignatureValidationDialog.class.getName());

    private final SignatureValidator signatureValidator;
    protected static ResourceBundle messageBundle;
    protected final SignatureWidgetAnnotation signatureWidgetAnnotation;

    public SignatureValidationDialog(Frame parent, ResourceBundle messageBundle,
                                     SignatureWidgetAnnotation signatureWidgetAnnotation, SignatureValidator signatureValidator) {
        super(parent, true);
        SignatureValidationDialog.messageBundle = messageBundle;
        this.signatureValidator = signatureValidator;
        this.signatureWidgetAnnotation = signatureWidgetAnnotation;
        buildUI();
    }

    protected void buildUI() {
        setTitle(messageBundle.getString("viewer.annotation.signature.validation.dialog.title"));
        setResizable(false);
        if (signatureValidator.isValidated()) {
            showContent(buildContent());
        } else {
            showWhenValidated(SignatureValidationService.ensureValidated(signatureValidator));
        }
    }

    /**
     * Shows progress until the validation completes, then its result.
     *
     * @param validation a running validation of this dialog's signature
     */
    public void showWhenValidated(CompletableFuture<SignatureValidator> validation) {
        showContent(new SignatureValidationProgressPanel(messageBundle, signatureValidator, null, this::close));
        validation.whenComplete((validator, failure) -> {
            if (!isDisplayable()) return;
            showContent(failure == null ? buildContent()
                    : new SignatureValidationProgressPanel(messageBundle, signatureValidator,
                    SignatureValidationService.cause(failure), this::close));
        });
    }

    private void showContent(JComponent content) {
        getContentPane().removeAll();
        getContentPane().add(content);
        pack();
        setLocationRelativeTo(getOwner());
    }

    private void close() {
        setVisible(false);
        dispose();
    }

    protected JPanel buildContent() {
        SignatureValidationStatus signatureValidationStatus =
                new SignatureValidationStatus(messageBundle, signatureWidgetAnnotation, signatureValidator);

        // simple close
        final JButton closeButton = new JButton(messageBundle.getString(
                "viewer.annotation.signature.validation.dialog.close.button.label"));
        closeButton.setMnemonic(messageBundle.getString("viewer.button.cancel.mnemonic").charAt(0));
        closeButton.addActionListener(e -> close());

        // launch properties dialog showing all signature info.
        final JButton propertiesButton = new JButton(messageBundle.getString(
                "viewer.annotation.signature.validation.dialog.signerProperties.button.label"));
        final Dialog parent = this;
        propertiesButton.addActionListener(e ->
                new SignaturePropertiesDialog(parent, messageBundle, signatureWidgetAnnotation).setVisible(true));

        // put it all together.
        SignatureValidationPanel validityPanel =
                new SignatureValidationPanel(signatureValidationStatus, messageBundle, signatureWidgetAnnotation,
                        signatureValidator, true, false);
        GridBagConstraints constraints = validityPanel.getConstraints();

        constraints.insets = new Insets(15, 5, 5, 5);
        constraints.anchor = GridBagConstraints.WEST;
        validityPanel.addGB(propertiesButton, 0, 5, 1, 1);

        // revocation went unchecked (offline, or a server didn't answer): offer a one-off online check.
        if (SignatureValidationStatus.canCheckOnline(signatureValidator)) {
            JButton checkOnlineButton = new JButton(messageBundle.getString(
                    "viewer.annotation.signature.validation.dialog.checkOnline.button.label"));
            checkOnlineButton.addActionListener(e -> showWhenValidated(SignatureValidationService.validate(
                    signatureValidator, SignatureValidationService.options().withOnline(true))));
            constraints.anchor = GridBagConstraints.CENTER;
            validityPanel.addGB(checkOnlineButton, 1, 5, 1, 1);
            constraints.anchor = GridBagConstraints.EAST;
            validityPanel.addGB(closeButton, 2, 5, 1, 1);
        } else {
            constraints.anchor = GridBagConstraints.EAST;
            validityPanel.addGB(closeButton, 1, 5, 1, 1);
        }
        return validityPanel;
    }
}
