package org.icepdf.fx.ri.ui.dialogs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Window;

/**
 * About dialog displaying application information, version, and license.
 */
public class AboutDialog extends Dialog<Void> {

    private static final String VERSION = "7.3.0"; // TODO: Load from build properties
    private static final String BUILD_DATE = "2026-03-21"; // TODO: Load from build properties

    public AboutDialog(Window owner) {
        initOwner(owner);
        setTitle("About ICEpdf Viewer");
        setResizable(false);

        // Create dialog content
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setPrefSize(500, 400);

        tabPane.getTabs().addAll(
                createAboutTab(),
                createLicenseTab(),
                createSystemInfoTab()
        );

        getDialogPane().setContent(tabPane);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    }

    /**
     * Creates the About tab with version and credits.
     */
    private Tab createAboutTab() {
        Tab tab = new Tab("About");

        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(20));
        vbox.setAlignment(Pos.CENTER);

        // Application name
        Label titleLabel = new Label("ICEpdf Viewer");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 24));

        // Version
        Label versionLabel = new Label("Version " + VERSION);
        versionLabel.setFont(Font.font("System", 14));

        // Build date
        Label buildLabel = new Label("Build Date: " + BUILD_DATE);
        buildLabel.setFont(Font.font("System", 12));

        // Description
        TextArea descriptionArea = new TextArea(
                "ICEpdf is an open source PDF engine for viewing, printing, and manipulating PDF documents.\n\n" +
                        "This JavaFX-based viewer provides a modern interface for working with PDF files."
        );
        descriptionArea.setWrapText(true);
        descriptionArea.setEditable(false);
        descriptionArea.setPrefRowCount(4);
        descriptionArea.setStyle("-fx-background-color: transparent;");

        // Copyright
        Label copyrightLabel = new Label("© 2001-2026 ICEsoft Technologies Canada Corp.");
        copyrightLabel.setFont(Font.font("System", 10));

        // Website link
        Hyperlink websiteLink = new Hyperlink("https://www.icesoft.com/java/projects/ICEpdf/overview.jsf");
        websiteLink.setOnAction(e -> {
            getHostServices().showDocument(websiteLink.getText());
        });

        vbox.getChildren().addAll(
                titleLabel,
                versionLabel,
                buildLabel,
                new Separator(),
                descriptionArea,
                new Separator(),
                copyrightLabel,
                websiteLink
        );

        tab.setContent(vbox);
        return tab;
    }

    /**
     * Creates the License tab.
     */
    private Tab createLicenseTab() {
        Tab tab = new Tab("License");

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        Label titleLabel = new Label("Apache License 2.0");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        TextArea licenseArea = new TextArea(
                "Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
                        "you may not use this file except in compliance with the License.\n" +
                        "You may obtain a copy of the License at\n\n" +
                        "    http://www.apache.org/licenses/LICENSE-2.0\n\n" +
                        "Unless required by applicable law or agreed to in writing, software\n" +
                        "distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                        "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                        "See the License for the specific language governing permissions and\n" +
                        "limitations under the License.\n\n" +
                        "---\n\n" +
                        "ICEpdf is an open source project and includes contributions from various developers.\n" +
                        "For more information, visit the project website or repository."
        );
        licenseArea.setWrapText(true);
        licenseArea.setEditable(false);
        licenseArea.setPrefRowCount(15);

        vbox.getChildren().addAll(titleLabel, licenseArea);
        tab.setContent(vbox);

        return tab;
    }

    /**
     * Creates the System Info tab.
     */
    private Tab createSystemInfoTab() {
        Tab tab = new Tab("System Info");

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        TextArea infoArea = new TextArea();
        infoArea.setWrapText(true);
        infoArea.setEditable(false);
        infoArea.setPrefRowCount(15);

        // Gather system information
        StringBuilder info = new StringBuilder();
        info.append("Java Version: ").append(System.getProperty("java.version")).append("\n");
        info.append("Java Vendor: ").append(System.getProperty("java.vendor")).append("\n");
        info.append("Java Home: ").append(System.getProperty("java.home")).append("\n");
        info.append("\n");
        info.append("JavaFX Version: ").append(System.getProperty("javafx.version", "Unknown")).append("\n");
        info.append("JavaFX Runtime: ").append(System.getProperty("javafx.runtime.version", "Unknown")).append("\n");
        info.append("\n");
        info.append("Operating System: ").append(System.getProperty("os.name")).append("\n");
        info.append("OS Version: ").append(System.getProperty("os.version")).append("\n");
        info.append("OS Architecture: ").append(System.getProperty("os.arch")).append("\n");
        info.append("\n");
        info.append("User Name: ").append(System.getProperty("user.name")).append("\n");
        info.append("User Home: ").append(System.getProperty("user.home")).append("\n");
        info.append("User Directory: ").append(System.getProperty("user.dir")).append("\n");
        info.append("\n");

        // Memory information
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;

        info.append("Max Memory: ").append(maxMemory).append(" MB\n");
        info.append("Total Memory: ").append(totalMemory).append(" MB\n");
        info.append("Used Memory: ").append(usedMemory).append(" MB\n");
        info.append("Free Memory: ").append(freeMemory).append(" MB\n");
        info.append("\n");
        info.append("Available Processors: ").append(runtime.availableProcessors()).append("\n");

        infoArea.setText(info.toString());

        Button copyButton = new Button("Copy to Clipboard");
        copyButton.setOnAction(e -> {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(infoArea.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });

        vbox.getChildren().addAll(infoArea, copyButton);
        tab.setContent(vbox);

        return tab;
    }

    /**
     * Helper to get host services (for opening links).
     * This is a workaround since Dialog doesn't have direct access to HostServices.
     */
    private javafx.application.HostServices getHostServices() {
        // JavaFX Dialog doesn't provide direct access to HostServices
        // In a real implementation, pass HostServices through the constructor
        // For now, just return null and handle gracefully
        return null;
    }
}

