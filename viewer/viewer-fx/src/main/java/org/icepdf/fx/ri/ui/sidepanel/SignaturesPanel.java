package org.icepdf.fx.ri.ui.sidepanel;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.fx.ri.viewer.ViewerModel;

import java.util.List;
import java.util.logging.Logger;

/**
 * Panel for viewing digital signature information in the PDF document.
 */
public class SignaturesPanel extends VBox {

    private static final Logger logger = Logger.getLogger(SignaturesPanel.class.getName());

    private final ViewerModel model;
    private final ListView<SignatureInfo> signaturesListView;
    private final Button detailsButton;
    private final Label emptyLabel;

    public SignaturesPanel(ViewerModel model) {
        this.model = model;
        this.signaturesListView = new ListView<>();
        this.detailsButton = new Button("View Details");
        this.emptyLabel = new Label("No signatures");

        initializeUI();
        setupBindings();
    }

    private void initializeUI() {
        setSpacing(5);
        setPadding(new Insets(5));

        // Configure ListView
        signaturesListView.setCellFactory(lv -> new SignatureCell());
        signaturesListView.setPlaceholder(emptyLabel);

        VBox.setVgrow(signaturesListView, Priority.ALWAYS);

        // Details button
        detailsButton.setMaxWidth(Double.MAX_VALUE);
        detailsButton.setOnAction(e -> showSignatureDetails());
        detailsButton.disableProperty().bind(
                signaturesListView.getSelectionModel().selectedItemProperty().isNull()
        );

        getChildren().addAll(signaturesListView, detailsButton);
    }

    private void setupBindings() {
        // Listen for document changes
        model.document.addListener((observable, oldDoc, newDoc) -> {
            handleDocumentChange(newDoc);
        });

        // Load signatures if document already exists
        if (model.document.get() != null) {
            handleDocumentChange(model.document.get());
        }
    }

    private void handleDocumentChange(Document newDoc) {
        signaturesListView.getItems().clear();

        if (newDoc != null) {
            try {
                // Get interactive form
                org.icepdf.core.pobjects.Catalog catalog = newDoc.getCatalog();
                if (catalog != null) {
                    InteractiveForm form = catalog.getInteractiveForm();
                    if (form != null) {
                        // Get signature fields
                        List<Object> fields = form.getFields();
                        if (fields != null) {
                            for (Object field : fields) {
                                if (field instanceof SignatureWidgetAnnotation) {
                                    SignatureWidgetAnnotation sigWidget =
                                            (SignatureWidgetAnnotation) field;
                                    SignatureInfo info = createSignatureInfo(sigWidget);
                                    if (info != null) {
                                        signaturesListView.getItems().add(info);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Error loading signatures: " + e.getMessage());
            }
        }
    }

    private SignatureInfo createSignatureInfo(SignatureWidgetAnnotation annotation) {
        try {
            String name = annotation.getName();
            if (name == null || name.trim().isEmpty()) {
                name = "Unnamed Signature";
            }

            // Try to get signer info
            String signer = "Unknown";
            String reason = "";
            String location = "";
            boolean isValid = false;

            try {
                // Check if signature has been applied
                if (annotation.hasSignatureDictionary()) {
                    org.icepdf.core.pobjects.acroform.SignatureDictionary sigDict =
                            annotation.getSignatureDictionary();

                    if (sigDict != null) {
                        // Try to validate signature
                        try {
                            var validator = annotation.getSignatureValidator();
                            if (validator != null) {
                                // Check if signature data hasn't been modified
                                isValid = !validator.isSignedDataModified();
                                // Additional info could be extracted from validator
                                // e.g., validator.isCertificateChainTrusted(), etc.
                            }
                        } catch (Exception e) {
                            logger.fine("Could not validate signature: " + e.getMessage());
                        }
                    }
                } else {
                    // Unsigned signature field
                    logger.fine("Signature field not yet signed");
                }

            } catch (Exception e) {
                logger.fine("Could not extract full signature details: " + e.getMessage());
            }

            return new SignatureInfo(name, signer, reason, location, isValid);

        } catch (Exception e) {
            logger.warning("Error creating signature info: " + e.getMessage());
            return null;
        }
    }

    private void showSignatureDetails() {
        SignatureInfo selected = signaturesListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Signature Details");
        alert.setHeaderText(selected.getName());

        StringBuilder details = new StringBuilder();
        details.append("Signer: ").append(selected.getSigner()).append("\n");
        details.append("Status: ").append(selected.isValid() ? "Valid" : "Invalid/Unknown").append("\n");

        if (selected.getReason() != null && !selected.getReason().isEmpty()) {
            details.append("Reason: ").append(selected.getReason()).append("\n");
        }

        if (selected.getLocation() != null && !selected.getLocation().isEmpty()) {
            details.append("Location: ").append(selected.getLocation()).append("\n");
        }

        alert.setContentText(details.toString());
        alert.showAndWait();
    }

    /**
     * Represents signature information.
     */
    private static class SignatureInfo {
        private final String name;
        private final String signer;
        private final String reason;
        private final String location;
        private final boolean valid;

        public SignatureInfo(String name, String signer, String reason,
                             String location, boolean valid) {
            this.name = name;
            this.signer = signer;
            this.reason = reason;
            this.location = location;
            this.valid = valid;
        }

        public String getName() {
            return name;
        }

        public String getSigner() {
            return signer;
        }

        public String getReason() {
            return reason;
        }

        public String getLocation() {
            return location;
        }

        public boolean isValid() {
            return valid;
        }
    }

    /**
     * Custom ListCell for displaying signature information.
     */
    private class SignatureCell extends ListCell<SignatureInfo> {

        @Override
        protected void updateItem(SignatureInfo item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox content = new VBox(3);

                Label nameLabel = new Label(item.getName());
                nameLabel.setStyle("-fx-font-weight: bold;");

                Label signerLabel = new Label("Signer: " + item.getSigner());
                signerLabel.setStyle("-fx-font-size: 10px;");

                Label statusLabel = new Label(
                        "Status: " + (item.isValid() ? "Valid" : "Invalid/Unknown")
                );
                statusLabel.setStyle("-fx-font-size: 10px;" +
                        (item.isValid() ? "-fx-text-fill: green;" : "-fx-text-fill: gray;"));

                content.getChildren().addAll(nameLabel, signerLabel, statusLabel);
                setGraphic(content);
            }
        }
    }
}

