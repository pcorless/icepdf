package org.icepdf.fx.ri.ui.sidepanel;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.NameTree;
import org.icepdf.fx.ri.viewer.ViewerModel;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

/**
 * Panel for viewing and extracting embedded file attachments from the PDF.
 */
public class AttachmentsPanel extends VBox {

    private static final Logger logger = Logger.getLogger(AttachmentsPanel.class.getName());

    private final ViewerModel model;
    private final TableView<AttachmentItem> attachmentsTable;
    private final Button extractButton;
    private final Label emptyLabel;

    public AttachmentsPanel(ViewerModel model) {
        this.model = model;
        this.attachmentsTable = new TableView<>();
        this.extractButton = new Button("Extract...");
        this.emptyLabel = new Label("No attachments");

        initializeUI();
        setupBindings();
    }

    private void initializeUI() {
        setSpacing(5);
        setPadding(new Insets(5));

        // Configure TableView columns
        TableColumn<AttachmentItem, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(150);

        TableColumn<AttachmentItem, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setPrefWidth(80);

        TableColumn<AttachmentItem, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("size"));
        sizeCol.setPrefWidth(80);

        attachmentsTable.getColumns().addAll(nameCol, typeCol, sizeCol);
        attachmentsTable.setPlaceholder(emptyLabel);
        attachmentsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        VBox.setVgrow(attachmentsTable, Priority.ALWAYS);

        // Extract button
        extractButton.setMaxWidth(Double.MAX_VALUE);
        extractButton.setOnAction(e -> extractSelectedAttachment());
        extractButton.disableProperty().bind(
                attachmentsTable.getSelectionModel().selectedItemProperty().isNull()
        );

        getChildren().addAll(attachmentsTable, extractButton);
    }

    private void setupBindings() {
        // Listen for document changes
        model.document.addListener((observable, oldDoc, newDoc) -> {
            handleDocumentChange(newDoc);
        });

        // Load attachments if document already exists
        if (model.document.get() != null) {
            handleDocumentChange(model.document.get());
        }
    }

    private void handleDocumentChange(Document newDoc) {
        attachmentsTable.getItems().clear();

        if (newDoc != null) {
            try {
                // Get embedded files from the document catalog
                org.icepdf.core.pobjects.Catalog catalog = newDoc.getCatalog();
                if (catalog != null) {
                    NameTree embeddedFiles = catalog.getEmbeddedFilesNameTree();
                    if (embeddedFiles != null) {
                        // getNamesAndValues returns a List alternating between name (String) and value (Object)
                        List namesAndValues = embeddedFiles.getNamesAndValues();
                        if (namesAndValues != null && !namesAndValues.isEmpty()) {
                            // Iterate through pairs (name, value)
                            for (int i = 0; i < namesAndValues.size() - 1; i += 2) {
                                Object nameObj = namesAndValues.get(i);
                                Object valueObj = namesAndValues.get(i + 1);

                                if (nameObj instanceof String) {
                                    String name = (String) nameObj;

                                    // Value should be a FileSpec dictionary
                                    if (valueObj instanceof org.icepdf.core.pobjects.FileSpecification) {
                                        org.icepdf.core.pobjects.FileSpecification fileSpec =
                                                (org.icepdf.core.pobjects.FileSpecification) valueObj;
                                        AttachmentItem item = createAttachmentItem(name, fileSpec);
                                        if (item != null) {
                                            attachmentsTable.getItems().add(item);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Error loading attachments: " + e.getMessage());
            }
        }
    }

    private AttachmentItem createAttachmentItem(String name, org.icepdf.core.pobjects.FileSpecification fileSpec) {
        try {
            // Get file type from FileSpec
            String type = "Unknown";
            long size = 0;

            // Try to get embedded file stream for more details
            // Note: FileSpecification API may vary - this is a simplified version

            String sizeStr = formatFileSize(size);

            return new AttachmentItem(name, type, sizeStr, fileSpec);
        } catch (Exception e) {
            logger.warning("Error creating attachment item: " + e.getMessage());
            return null;
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void extractSelectedAttachment() {
        AttachmentItem selected = attachmentsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Attachment");
        fileChooser.setInitialFileName(selected.getName());

        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try {
                // TODO: Extract actual file data from FileSpecification
                // This requires understanding the FileSpecification API
                showAlert(Alert.AlertType.INFORMATION, "Not Implemented",
                        "Attachment extraction not yet fully implemented.");
                logger.info("Would extract attachment to: " + file.getAbsolutePath());
            } catch (Exception e) {
                logger.warning("Error extracting attachment: " + e.getMessage());
                showAlert(Alert.AlertType.ERROR, "Error",
                        "Failed to extract attachment: " + e.getMessage());
            }
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Represents an attachment item in the table.
     */
    public static class AttachmentItem {
        private final String name;
        private final String type;
        private final String size;
        private final org.icepdf.core.pobjects.FileSpecification fileSpec;

        public AttachmentItem(String name, String type, String size,
                              org.icepdf.core.pobjects.FileSpecification fileSpec) {
            this.name = name;
            this.type = type;
            this.size = size;
            this.fileSpec = fileSpec;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getSize() {
            return size;
        }

        public org.icepdf.core.pobjects.FileSpecification getFileSpec() {
            return fileSpec;
        }
    }
}

