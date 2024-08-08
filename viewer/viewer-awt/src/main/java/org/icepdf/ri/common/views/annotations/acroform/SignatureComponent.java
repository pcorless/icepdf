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
package org.icepdf.ri.common.views.annotations.acroform;


import org.icepdf.core.pobjects.acroform.SignatureFieldDictionary;
import org.icepdf.core.pobjects.acroform.SignatureHandler;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.acroform.signature.exceptions.SignatureIntegrityException;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.annotations.AbstractAnnotationComponent;
import org.icepdf.ri.common.views.annotations.signatures.CertificatePropertiesDialog;
import org.icepdf.ri.common.views.annotations.signatures.SignaturePropertiesDialog;
import org.icepdf.ri.common.views.annotations.signing.SignatureCreationDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.security.KeyStoreException;
import java.util.logging.Logger;

/**
 * UI component that represents an Acroform signature widget in the interactive UI.
 * Focus, mouse, validation and form submission is handled by this class.
 *
 * @since 6.1
 */
public class SignatureComponent extends AbstractAnnotationComponent<SignatureWidgetAnnotation> {

    private static final Logger logger =
            Logger.getLogger(SignatureComponent.class.toString());

    protected final JMenuItem validationMenu;
    protected final JMenuItem signaturePropertiesMenu;
    protected final JMenuItem addSignatureMenu;
    protected final JMenuItem deleteSignatureMenu;
    protected final Controller controller;

    public SignatureComponent(SignatureWidgetAnnotation annotation, DocumentViewController documentViewController,
                              AbstractPageViewComponent pageViewComponent) {
        super(annotation, documentViewController, pageViewComponent);

        controller = documentViewController.getParentController();

        isShowInvisibleBorder = true;
        isResizable = true;
        isMovable = true;

        if (!annotation.allowScreenOrPrintRenderingOrInteraction()) {
            // border state flags.
            isEditable = false;
            isRollover = false;
            isMovable = false;
            isResizable = false;
            isShowInvisibleBorder = false;
        }

        // add context menu for quick access to validating and signature properties.
        contextMenu = new JPopupMenu();

        validationMenu = new JMenuItem(messageBundle.getString(
                "viewer.annotation.signature.menu.showCertificates.label"));
        validationMenu.addActionListener(new CertificatePropertiesActionListener());

        signaturePropertiesMenu = new JMenuItem(messageBundle.getString(
                "viewer.annotation.signature.menu.signatureProperties.label"));
        signaturePropertiesMenu.addActionListener(new SignerPropertiesActionListener());

        addSignatureMenu = new JMenuItem(messageBundle.getString(
                "viewer.annotation.signature.menu.addSignature.label"));
        addSignatureMenu.addActionListener(new NewSignatureActionListener(this));

        deleteSignatureMenu = new JMenuItem(messageBundle.getString(
                "viewer.annotation.signature.menu.deleteSignature.label"));
        deleteSignatureMenu.addActionListener(new DeleteSignatureActionListener(this));

        updateContextMenu();
    }

    protected void updateContextMenu() {
        SignatureFieldDictionary fieldDictionary = annotation.getFieldDictionary();
        if (fieldDictionary != null) {
            SignatureValidator signatureValidator = annotation.getSignatureValidator();
            contextMenu.removeAll();
            if (signatureValidator != null) {
                contextMenu.add(validationMenu);
                contextMenu.add(signaturePropertiesMenu);
            } else {
                contextMenu.add(addSignatureMenu);
                contextMenu.add(deleteSignatureMenu);
            }
        }
    }

    public boolean isActive() {
        return false;
    }

    @Override
    public void resetAppearanceShapes() {

    }

    @Override
    public void paintComponent(Graphics g) {

    }

    /**
     * Utility for showing SignaturePropertiesDialog via a double click or the context menu.
     */
    protected void showSignatureWidgetPropertiesDialog() {
        SignatureFieldDictionary fieldDictionary = annotation.getFieldDictionary();
        if (fieldDictionary != null) {
            SignatureValidator signatureValidator = annotation.getSignatureValidator();
            if (signatureValidator != null) {
                try {
                    signatureValidator.validate();
                    new SignaturePropertiesDialog(controller.getViewerFrame(),
                            messageBundle, annotation).setVisible(true);
                } catch (SignatureIntegrityException e1) {
                    logger.fine("Error validating annotation " + annotation.toString());
                }
            }
        }
    }

    /**
     * Shows the CertificatePropertiesDialog.
     */
    class CertificatePropertiesActionListener implements ActionListener {
        public void actionPerformed(ActionEvent actionEvent) {
            // validate the signature and show the summary dialog.
            SignatureFieldDictionary fieldDictionary = annotation.getFieldDictionary();
            if (fieldDictionary != null) {
                Library library = annotation.getLibrary();
                SignatureHandler signatureHandler = library.getSignatureHandler();
                SignatureValidator signatureValidator = signatureHandler.validateSignature(fieldDictionary);
                if (signatureValidator != null) {
                    try {
                        signatureValidator.validate();
                        new CertificatePropertiesDialog(controller.getViewerFrame(),
                                messageBundle, signatureValidator.getCertificateChain()).setVisible(true);
                    } catch (SignatureIntegrityException e1) {
                        logger.fine("Error validating annotation " + annotation.toString());
                    }
                }
            }
        }
    }

    /**
     * Opens the SignaturePropertiesDialog from a context menu.
     */
    class SignerPropertiesActionListener implements ActionListener {
        public void actionPerformed(ActionEvent actionEvent) {
            showSignatureWidgetPropertiesDialog();
        }
    }

    /**
     * Opens the SignaturePropertiesDialog from a context menu.
     */
    class NewSignatureActionListener implements ActionListener {

        private SignatureComponent signatureComponent;

        public NewSignatureActionListener(SignatureComponent signatureComponent) {
            this.signatureComponent = signatureComponent;
        }

        public void actionPerformed(ActionEvent actionEvent) {
            try {
                new SignatureCreationDialog(controller.getViewerFrame(), messageBundle, signatureComponent).setVisible(true);
            } catch (KeyStoreException e) {
                // todo show authentication failed dialog, could not open keystore
                logger.warning("failed to authenticate keystore");
            }
        }
    }

    class DeleteSignatureActionListener implements ActionListener {
        private SignatureComponent signatureComponent;

        public DeleteSignatureActionListener(SignatureComponent signatureComponent) {
            this.signatureComponent = signatureComponent;
        }

        public void actionPerformed(ActionEvent actionEvent) {
            controller.getDocumentViewController().deleteAnnotation(signatureComponent);
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        super.mouseClicked(e);
        if (e.getClickCount() == 2) {
            // show signature details dialog.
            showSignatureWidgetPropertiesDialog();
        }
        // pick up on the context menu display
        else if (e.getButton() == MouseEvent.BUTTON3 || e.getButton() == MouseEvent.BUTTON2) {
            updateContextMenu();
            contextMenu.show(e.getComponent(), e.getX(), e.getY());
        }
    }

    /**
     * Gets the associated widget annotation.
     *
     * @return SignatureWidgetAnnotation for the instance annotation object.
     */
    private SignatureWidgetAnnotation getSignatureWidgetAnnotation() {
        SignatureWidgetAnnotation widget = null;
        if (annotation instanceof SignatureWidgetAnnotation) {
            widget = annotation;
        } else {
            // corner case for PDF that aren't well-formed
            try {
                widget = new SignatureWidgetAnnotation(null);
                widget.init();
                annotation = widget;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.fine("Signature component annotation instance creation was interrupted");
            }
        }
        return widget;
    }

}
