package org.icepdf.qa.viewer.comparitors;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.icepdf.qa.config.Result;
import org.icepdf.qa.viewer.common.Mediator;
import org.icepdf.qa.viewer.common.PreferencesController;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * Image compare pane allows the easy identification of changed
 */
public class ImageComparePane extends ComparatorPane {

    public static final String SINGLE_COMPARE_VIEW = "Single";
    public static final String SIDE_BY_SIDE_COMPARE_VIEW = "Side-by-side";
    public static final String SCALED_COMPARE_VIEW = "Scaled";

    public static final String DIFFERENCE_BLENDING_MODE = "Difference";
    public static final String GREEN_BLENDING_MODE = "Green Subtraction";
    public static final String MULTIPLY_BLENDING_MODE = "Multiply";

    // toggle value for enabling/disabling the compare effect.
    private boolean enableBlendingMode;

    // image holders.
    private ImageView imageViewA1;
    private ImageView imageViewA2;
    private ImageView imageViewB1;
    private ImageView imageViewB2;

    // view type,  side-by-side, blending mode etc.
    private ChoiceBox<String> viewTypesChoiceBox;
    // effects that can be applied to show differences.
    private ChoiceBox<String> blendingModeChoiceBox;
    private ToggleButton blendingToggleButton;

    private ScrollPane scrollPane;

    public ImageComparePane(Mediator mediator) {
        super(mediator);
        try {
            imageViewA1 = new ImageView();
            imageViewB1 = new ImageView();
            imageViewA2 = new ImageView();
            imageViewB2 = new ImageView();

            ToolBar viewTools = new ToolBar();
            // view type
            Label viewLabel = new Label("View Type");
            viewTypesChoiceBox = new ChoiceBox<>();
            viewTypesChoiceBox.getItems().addAll(SINGLE_COMPARE_VIEW, SIDE_BY_SIDE_COMPARE_VIEW);//, SCALED_COMPARE_VIEW);
            viewTypesChoiceBox.getSelectionModel().select(PreferencesController.getLastUsedImageCompareView());
            viewTypesChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                PreferencesController.saveLastUsedImageCompareView(newValue);
                refreshView();
            });

            // Blending Modes
            Label modeLabel = new Label("Mode:");
            blendingModeChoiceBox = new ChoiceBox<>();
            blendingModeChoiceBox.getItems().addAll(
                    DIFFERENCE_BLENDING_MODE, GREEN_BLENDING_MODE);//, MULTIPLY_BLENDING_MODE);
            blendingModeChoiceBox.getSelectionModel().select(PreferencesController.getLastUsedImageCompareBlendingMode());
            blendingModeChoiceBox.getSelectionModel().selectedItemProperty().addListener((
                    observable, oldValue, newValue) -> {
                PreferencesController.saveLastUsedImageCompareBlendingMode(newValue);
                refreshView();
            });
            enableBlendingMode = PreferencesController.getLastUsedImageCompareDiffEnabled();
            blendingToggleButton = new ToggleButton("On");
            blendingToggleButton.setSelected(enableBlendingMode);
            blendingToggleButton.setOnAction(event -> enableBlendingMode());

            viewTools.getItems().addAll(viewLabel, viewTypesChoiceBox, modeLabel, blendingModeChoiceBox, blendingToggleButton);
            setTop(new VBox(20, viewTools));
            scrollPane = new ScrollPane();
            setCenter(scrollPane);


            enableBlendingMode();


        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void addMouseScrolling(Node node) {
        node.setOnScroll((ScrollEvent event) -> {
            // Adjust the zoom factor as per your requirement
            double zoomFactor = 1.05;
            double deltaY = event.getDeltaY();
            if (deltaY < 0) {
                zoomFactor = 2.0 - zoomFactor;
            }
            node.setScaleX(node.getScaleX() * zoomFactor);
            node.setScaleY(node.getScaleY() * zoomFactor);
        });
    }

    private void enableBlendingMode() {
        if (blendingToggleButton.isSelected()) {
            blendingToggleButton.setText("On");
            enableBlendingMode = true;
        } else {
            blendingToggleButton.setText("Off");
            enableBlendingMode = false;
        }
        refreshView();
        PreferencesController.saveLastUsedImageCompareDiffEnabled(enableBlendingMode);
    }

    public void nextDiffFilter() {
        int size = blendingModeChoiceBox.getItems().size();
        int selectedIndex = blendingModeChoiceBox.getSelectionModel().getSelectedIndex() + 1;
        if (selectedIndex >= size) {
            selectedIndex = 0;
        }
        blendingModeChoiceBox.getSelectionModel().select(selectedIndex);
    }

    public void toggleDiffFilter() {
        blendingToggleButton.setSelected(!blendingToggleButton.isSelected());
        enableBlendingMode();
    }

    public void openResult(Result result) {
        if (result != null) {
            Image image1;
            Image image2;
            // build out the image resources.
            try {
                File file = new File(result.getCaptureNameA());
                File file2 = new File(result.getCaptureNameB());
                image1 = new Image(new FileInputStream(file));
                image2 = new Image(new FileInputStream(file2));
                imageViewA1.setImage(image1);
                imageViewB1.setImage(image2);
                imageViewA2.setImage(image1);
                imageViewB2.setImage(image2);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            imageViewA1.setImage(null);
            imageViewB1.setImage(null);
            imageViewA2.setImage(null);
            imageViewB2.setImage(null);
        }
    }

    public void refreshView() {

        String viewType = viewTypesChoiceBox.getSelectionModel().getSelectedItem();
        String blendingMode = blendingModeChoiceBox.getSelectionModel().getSelectedItem();
        scrollPane.setContent(null);
        clearBlending(imageViewA1, imageViewA2, imageViewB1, imageViewB2);
        if (SINGLE_COMPARE_VIEW.equals(viewType)) {
            applyBlending(imageViewB1, blendingMode);
            StackPane singleViewPane = new StackPane();
            singleViewPane.setPadding(new Insets(25, 25, 25, 25));
            singleViewPane.setEffect(new DropShadow());
            singleViewPane.getChildren().removeAll();
            singleViewPane.getChildren().addAll(imageViewA1, imageViewB1);
            scrollPane.setContent(singleViewPane);
        } else if (SIDE_BY_SIDE_COMPARE_VIEW.equals(viewType)) {
            GridPane sideBySidePane = new GridPane();
            sideBySidePane.setPadding(new Insets(25, 25, 25, 25));
            sideBySidePane.setHgap(25);
            sideBySidePane.setVgap(25);
            StackPane leftImagePane = new StackPane();
            leftImagePane.setEffect(new DropShadow());
            applyBlending(imageViewA1, blendingMode);
            leftImagePane.getChildren().addAll(imageViewB1, imageViewA1);
            StackPane rightImagePane = new StackPane();
            rightImagePane.setEffect(new DropShadow());
            applyBlending(imageViewB2, blendingMode);
            rightImagePane.getChildren().addAll(imageViewA2, imageViewB2);

            sideBySidePane.add(leftImagePane, 0, 0);
            sideBySidePane.add(rightImagePane, 1, 0);

            scrollPane.setContent(sideBySidePane);

        } else if (SCALED_COMPARE_VIEW.equals(viewType)) {
//                gridPane.minWidthProperty().bind(Bindings.createDoubleBinding(() ->
//                        scrollPane.getViewportBounds().getWidth(), scrollPane.viewportBoundsProperty()));
        }

    }

    private void applyBlending(ImageView imageView, String blendingMode) {
        if (enableBlendingMode) {
            if (DIFFERENCE_BLENDING_MODE.equals(blendingMode)) {
                imageView.setBlendMode(BlendMode.DIFFERENCE);
            } else if (GREEN_BLENDING_MODE.equals(blendingMode)) {
                imageView.setBlendMode(BlendMode.GREEN);
            } else if (MULTIPLY_BLENDING_MODE.equals(blendingMode)) {
                imageView.setBlendMode(BlendMode.MULTIPLY);
            }
        } else {
            imageView.setBlendMode(null);
        }
    }

    private void clearBlending(ImageView... imageViews) {
        for (ImageView imageView : imageViews) {
            imageView.setBlendMode(null);
        }
    }

}
