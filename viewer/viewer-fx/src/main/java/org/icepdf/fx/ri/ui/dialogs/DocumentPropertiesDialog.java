package org.icepdf.fx.ri.ui.dialogs;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PInfo;
import org.icepdf.core.pobjects.security.Permissions;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.fx.ri.viewer.ViewerModel;

/**
 * Dialog displaying document properties including metadata, security, and fonts.
 */
public class DocumentPropertiesDialog extends Dialog<Void> {

    private final ViewerModel model;
    private TabPane tabPane;

    public DocumentPropertiesDialog(ViewerModel model, Window owner) {
        this.model = model;

        initOwner(owner);
        setTitle("Document Properties");
        setResizable(true);

        // Create dialog content
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setPrefSize(600, 400);

        // Add tabs
        tabPane.getTabs().addAll(
                createGeneralTab(),
                createSecurityTab(),
                createFontsTab()
        );

        getDialogPane().setContent(tabPane);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    }

    /**
     * Creates the General tab with document metadata.
     */
    private Tab createGeneralTab() {
        Tab tab = new Tab("General");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        Document document = model.document.get();
        if (document != null) {
            PInfo info = document.getInfo();

            int row = 0;

            // File name
            addProperty(grid, row++, "File:", model.filePath.get() != null ?
                    model.filePath.get() : "Unknown");

            // File size
            long sizeBytes = model.documentSizeBytes.get();
            String sizeStr = formatFileSize(sizeBytes);
            addProperty(grid, row++, "File Size:", sizeStr);

            // Title
            String title = info.getTitle();
            addProperty(grid, row++, "Title:", title != null ? title : "(Not set)");

            // Author
            String author = info.getAuthor();
            addProperty(grid, row++, "Author:", author != null ? author : "(Not set)");

            // Subject
            String subject = info.getSubject();
            addProperty(grid, row++, "Subject:", subject != null ? subject : "(Not set)");

            // Keywords
            String keywords = info.getKeywords();
            addProperty(grid, row++, "Keywords:", keywords != null ? keywords : "(Not set)");

            // Creator
            String creator = info.getCreator();
            addProperty(grid, row++, "Creator:", creator != null ? creator : "(Not set)");

            // Producer
            String producer = info.getProducer();
            addProperty(grid, row++, "Producer:", producer != null ? producer : "(Not set)");

            // Creation Date
            String creationDate = info.getCreationDate() != null ?
                    info.getCreationDate().toString() : "(Not set)";
            addProperty(grid, row++, "Creation Date:", creationDate);

            // Modification Date
            String modDate = info.getModDate() != null ?
                    info.getModDate().toString() : "(Not set)";
            addProperty(grid, row++, "Modification Date:", modDate);

            // Page count
            addProperty(grid, row++, "Page Count:", String.valueOf(model.totalPages.get()));
        }

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        tab.setContent(scrollPane);

        return tab;
    }

    /**
     * Creates the Security tab with permissions and encryption info.
     */
    private Tab createSecurityTab() {
        Tab tab = new Tab("Security");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        Document document = model.document.get();
        if (document != null) {
            int row = 0;

            // Encryption - check if SecurityManager exists
            SecurityManager securityManager = document.getSecurityManager();
            boolean isEncrypted = (securityManager != null);
            addProperty(grid, row++, "Encrypted:", isEncrypted ? "Yes" : "No");

            if (isEncrypted) {
                // Security handler
                String securityHandler = securityManager.getClass().getSimpleName();
                addProperty(grid, row++, "Security Handler:", securityHandler);
            }

            // Permissions
            if (securityManager != null && securityManager.getPermissions() != null) {
                Permissions permissions = securityManager.getPermissions();
                addProperty(grid, row++, "Printing:",
                        permissions.getPermissions(Permissions.PRINT_DOCUMENT) ? "Allowed" : "Not Allowed");
                addProperty(grid, row++, "Content Copying:",
                        permissions.getPermissions(Permissions.CONTENT_EXTRACTION) ? "Allowed" : "Not Allowed");
                addProperty(grid, row++, "Document Assembly:",
                        permissions.getPermissions(Permissions.DOCUMENT_ASSEMBLY) ? "Allowed" : "Not Allowed");
                addProperty(grid, row++, "Form Filling:",
                        permissions.getPermissions(Permissions.FORM_FIELD_FILL_SIGNING) ? "Allowed" : "Not Allowed");
            } else {
                addProperty(grid, row++, "Printing:", "Allowed");
                addProperty(grid, row++, "Content Copying:", "Allowed");
                addProperty(grid, row++, "Document Assembly:", "Allowed");
                addProperty(grid, row++, "Form Filling:", "Allowed");
            }
        }

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        tab.setContent(scrollPane);

        return tab;
    }

    /**
     * Creates the Fonts tab with embedded fonts list.
     */
    private Tab createFontsTab() {
        Tab tab = new Tab("Fonts");

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        Label label = new Label("Embedded Fonts:");

        TableView<FontInfo> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Font Name column
        TableColumn<FontInfo, String> nameCol = new TableColumn<>("Font Name");
        nameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        nameCol.setPrefWidth(200);

        // Font Type column
        TableColumn<FontInfo, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cellData -> cellData.getValue().typeProperty());
        typeCol.setPrefWidth(100);

        // Encoding column
        TableColumn<FontInfo, String> encodingCol = new TableColumn<>("Encoding");
        encodingCol.setCellValueFactory(cellData -> cellData.getValue().encodingProperty());
        encodingCol.setPrefWidth(150);

        // Embedded column
        TableColumn<FontInfo, String> embeddedCol = new TableColumn<>("Embedded");
        embeddedCol.setCellValueFactory(cellData -> cellData.getValue().embeddedProperty());
        embeddedCol.setPrefWidth(100);

        table.getColumns().addAll(nameCol, typeCol, encodingCol, embeddedCol);

        // Populate fonts (placeholder - would need to extract from document)
        Document document = model.document.get();
        if (document != null) {
            // TODO: Extract font information from document
            // This would require iterating through pages and extracting font resources
            table.setPlaceholder(new Label("Font information extraction not yet implemented"));
        }

        Button copyButton = new Button("Copy to Clipboard");
        copyButton.setOnAction(e -> copyFontsToClipboard(table));

        vbox.getChildren().addAll(label, table, copyButton);
        tab.setContent(vbox);

        return tab;
    }

    /**
     * Adds a property row to the grid.
     */
    private void addProperty(GridPane grid, int row, String label, String value) {
        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-font-weight: bold;");

        TextField valueField = new TextField(value);
        valueField.setEditable(false);
        valueField.setStyle("-fx-background-color: transparent;");

        grid.add(labelNode, 0, row);
        grid.add(valueField, 1, row);
    }

    /**
     * Formats file size in human-readable format.
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Copies font information to clipboard.
     */
    private void copyFontsToClipboard(TableView<FontInfo> table) {
        StringBuilder sb = new StringBuilder();
        sb.append("Font Name\tType\tEncoding\tEmbedded\n");

        for (FontInfo font : table.getItems()) {
            sb.append(font.getName()).append("\t");
            sb.append(font.getType()).append("\t");
            sb.append(font.getEncoding()).append("\t");
            sb.append(font.getEmbedded()).append("\n");
        }

        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(sb.toString());
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
    }

    /**
     * Helper class to represent font information.
     */
    public static class FontInfo {
        private final javafx.beans.property.SimpleStringProperty name;
        private final javafx.beans.property.SimpleStringProperty type;
        private final javafx.beans.property.SimpleStringProperty encoding;
        private final javafx.beans.property.SimpleStringProperty embedded;

        public FontInfo(String name, String type, String encoding, String embedded) {
            this.name = new javafx.beans.property.SimpleStringProperty(name);
            this.type = new javafx.beans.property.SimpleStringProperty(type);
            this.encoding = new javafx.beans.property.SimpleStringProperty(encoding);
            this.embedded = new javafx.beans.property.SimpleStringProperty(embedded);
        }

        public String getName() {
            return name.get();
        }

        public javafx.beans.property.SimpleStringProperty nameProperty() {
            return name;
        }

        public String getType() {
            return type.get();
        }

        public javafx.beans.property.SimpleStringProperty typeProperty() {
            return type;
        }

        public String getEncoding() {
            return encoding.get();
        }

        public javafx.beans.property.SimpleStringProperty encodingProperty() {
            return encoding;
        }

        public String getEmbedded() {
            return embedded.get();
        }

        public javafx.beans.property.SimpleStringProperty embeddedProperty() {
            return embedded;
        }
    }
}

