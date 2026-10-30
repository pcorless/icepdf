/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.ri.common.views.annotations.signatures;

import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;

import javax.swing.*;
import java.awt.*;
import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * The compact card a signature dialog shows while the signature is checked, or when the check
 * failed: a busy bar (or the failure), the signer's name and a close button.
 */
public class SignatureValidationProgressPanel extends JPanel {

    /**
     * @param messageBundle message bundle
     * @param validator     the signature being checked
     * @param failure       null while checking, else why the check failed
     * @param close         closes the dialog
     */
    public SignatureValidationProgressPanel(ResourceBundle messageBundle, SignatureValidator validator,
                                            Throwable failure, Runnable close) {
        super(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.weightx = 1.0;
        constraints.insets = new Insets(4, 0, 4, 0);

        String message;
        if (failure == null) {
            message = messageBundle.getString("viewer.utilityPane.signatures.verify.validating.label");
        } else {
            String reason = failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
            message = new MessageFormat(messageBundle.getString(
                    "viewer.annotation.signature.validation.failed.label")).format(new Object[]{reason});
        }
        add(new JLabel(message), constraints);

        String signer = SignatureValidationStatus.signerName(validator);
        if (!signer.isEmpty()) {
            MessageFormat signedBy = new MessageFormat(messageBundle.getString(
                    "viewer.annotation.signature.properties.dialog.signedBy.label"));
            add(new JLabel(signedBy.format(new Object[]{signer, ""}).trim()), constraints);
        }

        if (failure == null) {
            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            progressBar.setPreferredSize(new Dimension(280, progressBar.getPreferredSize().height));
            add(progressBar, constraints);
        }

        JButton closeButton = new JButton(messageBundle.getString(
                "viewer.annotation.signature.validation.dialog.close.button.label"));
        closeButton.addActionListener(e -> close.run());
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.EAST;
        constraints.insets = new Insets(10, 0, 0, 0);
        add(closeButton, constraints);
    }
}
