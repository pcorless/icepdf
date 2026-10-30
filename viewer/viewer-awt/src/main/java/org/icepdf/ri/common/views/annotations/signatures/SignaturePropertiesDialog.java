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
 * The SignaturePropertiesDialog shows a signatures basic information; validation, signer info and a summary panel.
 */
public class SignaturePropertiesDialog extends EscapeJDialog {

    private static final Logger logger =
            Logger.getLogger(SignaturePropertiesDialog.class.getName());

    // layouts constraint
    private GridBagConstraints constraints;

    private final SignatureValidator signatureValidator;
    protected static ResourceBundle messageBundle;
    protected final SignatureWidgetAnnotation signatureWidgetAnnotation;

    public SignaturePropertiesDialog(Dialog parent, ResourceBundle messageBundle,
                                     SignatureWidgetAnnotation signatureWidgetAnnotation) {
        super(parent, true);
        SignaturePropertiesDialog.messageBundle = messageBundle;
        this.signatureValidator = signatureWidgetAnnotation.getSignatureValidator();
        this.signatureWidgetAnnotation = signatureWidgetAnnotation;
        buildUI();
    }

    public SignaturePropertiesDialog(Frame parent, ResourceBundle messageBundle,
                                     SignatureWidgetAnnotation signatureWidgetAnnotation) {
        super(parent, true);
        SignaturePropertiesDialog.messageBundle = messageBundle;
        this.signatureValidator = signatureWidgetAnnotation.getSignatureValidator();
        this.signatureWidgetAnnotation = signatureWidgetAnnotation;
        buildUI();
    }

    private void buildUI() {
        setTitle(messageBundle.getString("viewer.annotation.signature.properties.dialog.title"));
        setResizable(false);
        if (signatureValidator.isValidated()) {
            showContent(buildContent());
        } else {
            // usually the signatures panel has checked it already; if not, check now, off the
            // event thread, and show progress meanwhile.
            showWhenValidated(SignatureValidationService.ensureValidated(signatureValidator));
        }
    }

    private void showWhenValidated(CompletableFuture<SignatureValidator> validation) {
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

    private JPanel buildContent() {

        SignatureValidationStatus signatureValidationStatus =
                new SignatureValidationStatus(messageBundle, signatureWidgetAnnotation, signatureValidator);

        JPanel annotationPanel = new JPanel(new GridBagLayout());

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(5, 10, 10, 10);

        // basic signer information
        SignerSummaryPanel signerSummaryPanel =
                new SignerSummaryPanel(signatureValidationStatus, messageBundle, signatureWidgetAnnotation, signatureValidator, true);
        addGB(annotationPanel, signerSummaryPanel, 0, 0, 3, 1);

        // Validity summary
        SignatureValidationPanel validityPanel =
                new SignatureValidationPanel(signatureValidationStatus, messageBundle, signatureWidgetAnnotation,
                        signatureValidator, false, true);
        addGB(annotationPanel, validityPanel, 0, 1, 3, 1);


        // SignatureSigner info
        SignerInfoPanel signerInfoPanel = new SignerInfoPanel(signatureValidationStatus, messageBundle, signatureWidgetAnnotation, signatureValidator);
        addGB(annotationPanel, signerInfoPanel, 0, 2, 3, 1);

        // close buttons.
        // simple close
        final JButton closeButton = new JButton(messageBundle.getString(
                "viewer.annotation.signature.validation.dialog.close.button.label"));
        closeButton.setMnemonic(messageBundle.getString("viewer.button.cancel.mnemonic").charAt(0));
        closeButton.addActionListener(e -> close());
        final JButton certPropertiesButton = new JButton(messageBundle.getString(
                "viewer.annotation.signature.properties.dialog.showCertificates.label"));
        final JDialog parent = this;
        certPropertiesButton.addActionListener(e -> new CertificatePropertiesDialog(parent, messageBundle,
                signatureValidator.getCertificateChain())
                .setVisible(true));

        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1.0;
        addGB(annotationPanel, certPropertiesButton, 0, 3, 1, 1);

        // revocation went unchecked (offline, or a server didn't answer): offer a one-off online check.
        if (SignatureValidationStatus.canCheckOnline(signatureValidator)) {
            JButton checkOnlineButton = new JButton(messageBundle.getString(
                    "viewer.annotation.signature.validation.dialog.checkOnline.button.label"));
            checkOnlineButton.addActionListener(e -> showWhenValidated(SignatureValidationService.validate(
                    signatureValidator, SignatureValidationService.options().withOnline(true))));
            constraints.anchor = GridBagConstraints.CENTER;
            addGB(annotationPanel, checkOnlineButton, 1, 3, 1, 1);
        }

        constraints.anchor = GridBagConstraints.EAST;
        constraints.weightx = 1.0;
        addGB(annotationPanel, closeButton, 2, 3, 1, 1);

        return annotationPanel;
    }

    private void addGB(JPanel layout, Component component,
                       int x, int y,
                       int rowSpan, int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        layout.add(component, constraints);
    }
}
