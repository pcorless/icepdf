package org.icepdf.fx.ri.views;

import javafx.beans.property.*;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class PageViewWidget extends Region {

    private FloatProperty scale;
    private FloatProperty rotation;
    private IntegerProperty pageIndex;

    private DoubleProperty width;
    private DoubleProperty height;

    private Label pageIndexLabel;
    private Label scaleLabel;
    private Label rotationLabel;

    public PageViewWidget(int pageIndex, FloatProperty scale, FloatProperty rotation) {
        this.pageIndex = new SimpleIntegerProperty(pageIndex);
        this.scale = new SimpleFloatProperty();
        this.scale.bind(scale);
        this.rotation = new SimpleFloatProperty();
        this.rotation.bind(rotation);

        width = new SimpleDoubleProperty(75);
        height = new SimpleDoubleProperty(50);

        scale.addListener((observable, oldValue, newValue) -> {
            width.set(75 * newValue.floatValue());
            height.set(50 * newValue.floatValue());
        });

        pageIndexLabel = new Label("Page " + pageIndex);
        scaleLabel = new Label();
        rotationLabel = new Label();
        createLayout();
    }

    private void createLayout() {
        VBox vBox = new VBox();
        scaleLabel.textProperty().bind(scale.asString());
        rotationLabel.textProperty().bind(width.asString());
        vBox.getChildren().addAll(pageIndexLabel, scaleLabel, rotationLabel);
        minWidthProperty().bind(width);
        minHeightProperty().bind(height);
        getChildren().add(vBox);
    }

    public int getPageIndex() {
        return pageIndex.get();
    }

    public float getScale() {
        return scale.get();
    }

    public FloatProperty scaleProperty() {
        return scale;
    }

    public float getRotation() {
        return rotation.get();
    }

    public FloatProperty rotationProperty() {
        return rotation;
    }
}
