package org.icepdf.fx.ri.ui.controls;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.icepdf.fx.ri.ui.icons.IconManager;
import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.navigation.FirstPageCommand;
import org.icepdf.fx.ri.viewer.commands.navigation.LastPageCommand;
import org.icepdf.fx.ri.viewer.commands.navigation.NextPageCommand;
import org.icepdf.fx.ri.viewer.commands.navigation.PreviousPageCommand;

/**
 * Page navigation control with first/previous/next/last buttons and page number input.
 * Provides quick navigation between pages with keyboard support.
 */
public class PageNavigationBar extends HBox {

    private final ViewerModel model;
    private final IconManager iconManager;

    private Button firstButton;
    private Button previousButton;
    private TextField pageField;
    private Label totalLabel;
    private Button nextButton;
    private Button lastButton;

    public PageNavigationBar(ViewerModel model) {
        this.model = model;
        this.iconManager = IconManager.getInstance();

        setAlignment(Pos.CENTER_LEFT);
        setSpacing(5);
        setPadding(new Insets(5));

        buildControls();
        setupBindings();
        setupEventHandlers();
    }

    private void buildControls() {
        // First page button
        firstButton = new Button();
        firstButton.setGraphic(iconManager.getFirstPageIcon());
        firstButton.setTooltip(new Tooltip("First Page (Home)"));

        // Previous page button
        previousButton = new Button();
        previousButton.setGraphic(iconManager.getPreviousPageIcon());
        previousButton.setTooltip(new Tooltip("Previous Page (Page Up)"));

        // Page number field
        pageField = new TextField();
        pageField.setPrefColumnCount(5);
        pageField.setPromptText("Page");
        pageField.setTooltip(new Tooltip("Current Page Number (Enter to go)"));
        pageField.setAlignment(Pos.CENTER);

        // Total pages label
        totalLabel = new Label();
        totalLabel.setTooltip(new Tooltip("Total Pages"));

        // Next page button
        nextButton = new Button();
        nextButton.setGraphic(iconManager.getNextPageIcon());
        nextButton.setTooltip(new Tooltip("Next Page (Page Down)"));

        // Last page button
        lastButton = new Button();
        lastButton.setGraphic(iconManager.getLastPageIcon());
        lastButton.setTooltip(new Tooltip("Last Page (End)"));

        getChildren().addAll(
                firstButton,
                previousButton,
                pageField,
                totalLabel,
                nextButton,
                lastButton
        );
    }

    private void setupBindings() {
        // Bind page field text to current page (display only)
        pageField.textProperty().addListener((obs, oldVal, newVal) -> {
            // Only update if not currently editing
            if (!pageField.isFocused()) {
                pageField.setText(String.valueOf(model.currentPage.get()));
            }
        });

        // Update when current page changes
        model.currentPage.addListener((obs, oldVal, newVal) -> {
            if (!pageField.isFocused()) {
                pageField.setText(String.valueOf(newVal));
            }
        });

        // Bind total pages label
        totalLabel.textProperty().bind(
                javafx.beans.binding.Bindings.concat(" / ", model.totalPages.asString())
        );

        // Disable buttons when appropriate
        firstButton.disableProperty().bind(
                model.document.isNull().or(model.currentPage.isEqualTo(1))
        );

        previousButton.disableProperty().bind(
                model.document.isNull().or(model.currentPage.isEqualTo(1))
        );

        nextButton.disableProperty().bind(
                model.document.isNull().or(model.currentPage.greaterThanOrEqualTo(model.totalPages))
        );

        lastButton.disableProperty().bind(
                model.document.isNull().or(model.currentPage.greaterThanOrEqualTo(model.totalPages))
        );

        pageField.disableProperty().bind(model.document.isNull());
    }

    private void setupEventHandlers() {
        // Button actions
        firstButton.setOnAction(e -> new FirstPageCommand(model).execute());
        previousButton.setOnAction(e -> new PreviousPageCommand(model).execute());
        nextButton.setOnAction(e -> new NextPageCommand(model).execute());
        lastButton.setOnAction(e -> new LastPageCommand(model).execute());

        // Page field - go to page on Enter
        pageField.setOnAction(e -> goToEnteredPage());

        // Validate input - only allow numbers
        pageField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                pageField.setText(oldVal);
            }
        });

        // When focus lost, revert to current page if invalid
        pageField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (wasFocused && !isNowFocused) {
                // Lost focus - revert if invalid
                if (pageField.getText().isEmpty()) {
                    pageField.setText(String.valueOf(model.currentPage.get()));
                }
            }
        });
    }

    /**
     * Navigate to the page number entered in the field.
     */
    private void goToEnteredPage() {
        try {
            int pageNum = Integer.parseInt(pageField.getText());
            if (pageNum >= 1 && pageNum <= model.totalPages.get()) {
                model.currentPage.set(pageNum);
                model.statusMessage.set("Navigated to page " + pageNum);
            } else {
                model.statusMessage.set("Invalid page number: " + pageNum);
                pageField.setText(String.valueOf(model.currentPage.get()));
            }
        } catch (NumberFormatException e) {
            model.statusMessage.set("Invalid page number");
            pageField.setText(String.valueOf(model.currentPage.get()));
        }

        // Clear focus from field
        pageField.getParent().requestFocus();
    }

    /**
     * Sets compact mode (smaller buttons, no icons).
     */
    public void setCompactMode(boolean compact) {
        if (compact) {
            firstButton.setText("|◀");
            firstButton.setGraphic(null);
            previousButton.setText("◀");
            previousButton.setGraphic(null);
            nextButton.setText("▶");
            nextButton.setGraphic(null);
            lastButton.setText("▶|");
            lastButton.setGraphic(null);
            pageField.setPrefColumnCount(4);
        } else {
            firstButton.setText("");
            firstButton.setGraphic(iconManager.getFirstPageIcon());
            previousButton.setText("");
            previousButton.setGraphic(iconManager.getPreviousPageIcon());
            nextButton.setText("");
            nextButton.setGraphic(iconManager.getNextPageIcon());
            lastButton.setText("");
            lastButton.setGraphic(iconManager.getLastPageIcon());
            pageField.setPrefColumnCount(5);
        }
    }
}

