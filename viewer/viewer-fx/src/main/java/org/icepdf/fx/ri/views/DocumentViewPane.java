package org.icepdf.fx.ri.views;

import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import org.icepdf.core.pobjects.Document;
import org.icepdf.fx.ri.viewer.ViewerModel;

public class DocumentViewPane extends Region {

    private FloatProperty scale = new SimpleFloatProperty(1.0f);
    private FloatProperty rotation = new SimpleFloatProperty(0.0f);
    private IntegerProperty currentPageIndex = new SimpleIntegerProperty(0);

    private VBox pageLayoutPane;
    private ScrollPane scrollPane;

    public DocumentViewPane(ViewerModel model) {
        createLayout(model);
        model.document.addListener((observable, oldValue, newValue) -> {
            pageLayoutPane.getChildren().clear();
            // create a page view for each page
            long start = System.currentTimeMillis();
            Document document = model.document.get();
            if (document != null) {
                for (int i = 0, max = document.getNumberOfPages(); i < max; i++) {
                    PageViewWidget pageViewPane = new PageViewWidget(model, i, scale, rotation, scrollPane);
                    pageViewPane.setBorder(new Border(new BorderStroke(null, BorderStrokeStyle.SOLID, null,
                            new BorderWidths(1))));
                    pageLayoutPane.getChildren().add(pageViewPane);
                    pageViewPane.viewportBounds.bind(scrollPane.viewportBoundsProperty());
                }
            }

            long end = System.currentTimeMillis();
            System.out.printf("Page creation time: %dms%n", end - start);
        });

    }


    private void createLayout(ViewerModel model) {
        scrollPane = new ScrollPane();

        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.prefWidthProperty().bind(this.widthProperty());
        scrollPane.prefHeightProperty().bind(this.heightProperty());


        VBox parent = new VBox();
        parent.setAlignment(Pos.CENTER);

        pageLayoutPane = new VBox();
        pageLayoutPane.setAlignment(Pos.CENTER);
        pageLayoutPane.setSpacing(10);
        pageLayoutPane.setMaxWidth(Region.USE_PREF_SIZE);

        parent.getChildren().add(pageLayoutPane);

        scrollPane.setContent(parent);
        getChildren().add(scrollPane);
    }

    private boolean isNodeIntersectingViewport(ScrollPane scrollPane, Node node) {
        Bounds viewportBounds = scrollPane.getViewportBounds();
        Bounds nodeBounds = node.localToScene(node.getBoundsInLocal());
        Bounds scrollPaneBounds = scrollPane.localToScene(scrollPane.getBoundsInLocal());
        double x = scrollPaneBounds.getMinX() + viewportBounds.getMinX();
        double y = scrollPaneBounds.getMinY() + viewportBounds.getMinY();
        double width = viewportBounds.getWidth();
        double height = viewportBounds.getHeight();
        return nodeBounds.intersects(
                x,
                y,
                width,
                height
        );
    }

    public FloatProperty scaleProperty() {
        return scale;
    }

}
