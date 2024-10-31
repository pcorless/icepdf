package org.icepdf.fx.ri.views;

import javafx.beans.property.*;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class PageViewWidget extends Region {

    private FloatProperty scale;
    private FloatProperty rotation;
    private IntegerProperty pageIndex;

    public ObjectProperty<Bounds> viewportBounds;

    private DoubleProperty width;
    private DoubleProperty height;

    private Label pageIndexLabel;
    private Label scaleLabel;
    private Label rotationLabel;

    public PageViewWidget(int pageIndex, FloatProperty scale, FloatProperty rotation, ScrollPane scrollPane) {
        this.pageIndex = new SimpleIntegerProperty(pageIndex);
        this.scale = new SimpleFloatProperty();
        this.scale.bind(scale);
        this.rotation = new SimpleFloatProperty();
        this.rotation.bind(rotation);

        this.viewportBounds = new SimpleObjectProperty<>();

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

//        setOnMouseClicked(event -> {
//            System.out.println("Page " + pageIndex + " " + isNodeIntersectingViewport(scrollPane, this));
//        });
    }

    private boolean isNodeIntersectingViewport(ScrollPane scrollPane, Node node) {
        Bounds viewportBounds = scrollPane.getViewportBounds();
        Bounds nodeBounds = node.localToScene(node.getBoundsInLocal());
        Bounds scrollPaneBounds = scrollPane.localToScene(scrollPane.getBoundsInLocal());

        return nodeBounds.intersects(
                scrollPaneBounds.getMinX() + viewportBounds.getMinX(),
                scrollPaneBounds.getMinY() + viewportBounds.getMinY(),
                viewportBounds.getWidth(),
                viewportBounds.getHeight()
        );
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
