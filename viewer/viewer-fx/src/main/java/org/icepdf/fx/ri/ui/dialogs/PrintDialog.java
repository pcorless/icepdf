package org.icepdf.fx.ri.ui.dialogs;

import javafx.geometry.Insets;
import javafx.print.*;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.icepdf.fx.ri.viewer.ViewerModel;

/**
 * Print dialog for configuring print settings.
 */
public class PrintDialog extends Dialog<PrinterJob> {

    private final ViewerModel model;
    private PrinterJob printerJob;

    // Controls
    private ComboBox<Printer> printerCombo;
    private RadioButton allPagesRadio;
    private RadioButton currentPageRadio;
    private RadioButton pageRangeRadio;
    private TextField pageRangeField;
    private Spinner<Integer> copiesSpinner;
    private ComboBox<String> pageScalingCombo;
    private CheckBox fitToPageCheckBox;
    private CheckBox centerOnPageCheckBox;
    private ComboBox<String> orientationCombo;

    public PrintDialog(ViewerModel model, Window owner) {
        this.model = model;

        initOwner(owner);
        setTitle("Print");
        setResizable(true);

        // Create printer job
        printerJob = PrinterJob.createPrinterJob();
        if (printerJob == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Unable to create printer job. No printers available.",
                    ButtonType.OK);
            alert.showAndWait();
            return;
        }

        // Create dialog content
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(500);

        content.getChildren().addAll(
                createPrinterSection(),
                new Separator(),
                createPageRangeSection(),
                new Separator(),
                createOptionsSection()
        );

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Set result converter
        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                configurePrinterJob();
                return printerJob;
            }
            return null;
        });
    }

    /**
     * Creates the printer selection section.
     */
    private VBox createPrinterSection() {
        VBox section = new VBox(10);

        Label titleLabel = new Label("Printer");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        // Printer selection
        Label printerLabel = new Label("Name:");
        printerCombo = new ComboBox<>();
        printerCombo.getItems().addAll(Printer.getAllPrinters());
        printerCombo.setValue(Printer.getDefaultPrinter());
        printerCombo.setPrefWidth(300);

        // Printer info
        Label statusLabel = new Label("Status:");
        Label statusValue = new Label("Ready");

        grid.add(printerLabel, 0, 0);
        grid.add(printerCombo, 1, 0);
        grid.add(statusLabel, 0, 1);
        grid.add(statusValue, 1, 1);

        // Properties button
        Button propertiesButton = new Button("Properties...");
        propertiesButton.setOnAction(e -> showPrinterProperties());

        section.getChildren().addAll(titleLabel, grid, propertiesButton);
        return section;
    }

    /**
     * Creates the page range section.
     */
    private VBox createPageRangeSection() {
        VBox section = new VBox(10);

        Label titleLabel = new Label("Page Range");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        ToggleGroup rangeGroup = new ToggleGroup();

        allPagesRadio = new RadioButton("All pages");
        allPagesRadio.setToggleGroup(rangeGroup);
        allPagesRadio.setSelected(true);

        currentPageRadio = new RadioButton("Current page");
        currentPageRadio.setToggleGroup(rangeGroup);

        pageRangeRadio = new RadioButton("Pages:");
        pageRangeRadio.setToggleGroup(rangeGroup);

        pageRangeField = new TextField("1-" + model.totalPages.get());
        pageRangeField.setPrefWidth(150);
        pageRangeField.setDisable(true);

        pageRangeRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            pageRangeField.setDisable(!newVal);
        });

        Label rangeHintLabel = new Label("(e.g., 1-3, 5, 8-10)");
        rangeHintLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(5);
        grid.add(allPagesRadio, 0, 0, 2, 1);
        grid.add(currentPageRadio, 0, 1, 2, 1);
        grid.add(pageRangeRadio, 0, 2);
        grid.add(pageRangeField, 1, 2);
        grid.add(rangeHintLabel, 1, 3);

        section.getChildren().addAll(titleLabel, grid);
        return section;
    }

    /**
     * Creates the print options section.
     */
    private VBox createOptionsSection() {
        VBox section = new VBox(10);

        Label titleLabel = new Label("Options");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        int row = 0;

        // Copies
        Label copiesLabel = new Label("Copies:");
        copiesSpinner = new Spinner<>(1, 999, 1);
        copiesSpinner.setEditable(true);
        copiesSpinner.setPrefWidth(80);

        grid.add(copiesLabel, 0, row);
        grid.add(copiesSpinner, 1, row);
        row++;

        // Page scaling
        Label scalingLabel = new Label("Page Scaling:");
        pageScalingCombo = new ComboBox<>();
        pageScalingCombo.getItems().addAll(
                "None",
                "Fit to Printable Area",
                "Shrink to Printable Area"
        );
        pageScalingCombo.setValue("None");
        pageScalingCombo.setPrefWidth(200);

        grid.add(scalingLabel, 0, row);
        grid.add(pageScalingCombo, 1, row);
        row++;

        // Orientation
        Label orientationLabel = new Label("Orientation:");
        orientationCombo = new ComboBox<>();
        orientationCombo.getItems().addAll("Portrait", "Landscape");
        orientationCombo.setValue("Portrait");
        orientationCombo.setPrefWidth(200);

        grid.add(orientationLabel, 0, row);
        grid.add(orientationCombo, 1, row);
        row++;

        // Checkboxes
        fitToPageCheckBox = new CheckBox("Fit to page");
        centerOnPageCheckBox = new CheckBox("Center on page");
        centerOnPageCheckBox.setSelected(true);

        grid.add(fitToPageCheckBox, 0, row, 2, 1);
        row++;
        grid.add(centerOnPageCheckBox, 0, row, 2, 1);

        section.getChildren().addAll(titleLabel, grid);
        return section;
    }

    /**
     * Configures the printer job with selected settings.
     */
    private void configurePrinterJob() {
        if (printerJob == null) {
            return;
        }

        // Set printer
        Printer selectedPrinter = printerCombo.getValue();
        if (selectedPrinter != null) {
            // Note: JavaFX PrinterJob doesn't allow changing printer after creation
            // This would need to recreate the job
        }

        // Configure page layout
        Printer printer = printerJob.getPrinter();
        PageLayout pageLayout = printer.createPageLayout(
                Paper.A4,
                orientationCombo.getValue().equals("Portrait") ?
                        PageOrientation.PORTRAIT : PageOrientation.LANDSCAPE,
                Printer.MarginType.DEFAULT
        );

        // Set job settings
        JobSettings jobSettings = printerJob.getJobSettings();
        jobSettings.setPageLayout(pageLayout);
        jobSettings.setCopies(copiesSpinner.getValue());

        // Set page ranges if specified
        if (pageRangeRadio.isSelected()) {
            String rangeStr = pageRangeField.getText();
            PageRange[] ranges = parsePageRanges(rangeStr);
            if (ranges != null && ranges.length > 0) {
                jobSettings.setPageRanges(ranges);
            }
        } else if (currentPageRadio.isSelected()) {
            int currentPage = model.currentPage.get();
            jobSettings.setPageRanges(new PageRange(currentPage, currentPage));
        }

        // Note: Additional settings like page scaling would need to be handled
        // during the actual printing process
    }

    /**
     * Parses page range string (e.g., "1-3,5,8-10") into PageRange array.
     */
    private PageRange[] parsePageRanges(String rangeStr) {
        try {
            String[] parts = rangeStr.split(",");
            PageRange[] ranges = new PageRange[parts.length];

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.contains("-")) {
                    String[] bounds = part.split("-");
                    int start = Integer.parseInt(bounds[0].trim());
                    int end = Integer.parseInt(bounds[1].trim());
                    ranges[i] = new PageRange(start, end);
                } else {
                    int page = Integer.parseInt(part);
                    ranges[i] = new PageRange(page, page);
                }
            }

            return ranges;
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Invalid page range format: " + rangeStr,
                    ButtonType.OK);
            alert.showAndWait();
            return null;
        }
    }

    /**
     * Shows printer properties dialog.
     */
    private void showPrinterProperties() {
        // JavaFX doesn't provide a built-in printer properties dialog
        // This would need to be implemented or use native dialogs
        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                "Printer properties dialog not yet implemented.",
                ButtonType.OK);
        alert.showAndWait();
    }
}

