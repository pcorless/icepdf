package org.icepdf.fx.ri.views;

import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import org.icepdf.core.pobjects.Document;
import org.icepdf.fx.ri.viewer.ViewerModel;

public class DocumentViewPane extends Region {

    private FloatProperty scale = new SimpleFloatProperty(1.0f);
    private FloatProperty rotation = new SimpleFloatProperty(0.0f);
    private IntegerProperty currentPageIndex = new SimpleIntegerProperty(0);

    private Pane tilePane;
    private ScrollPane scrollPane;

    public DocumentViewPane(ViewerModel model) {
        createLayout(model);
        model.document.addListener((observable, oldValue, newValue) -> {
            tilePane.getChildren().clear();
            // create a page view for each page
            long start = System.currentTimeMillis();
            Document document = model.document.get();
            if (document != null) {
                for (int i = 0, max = document.getNumberOfPages(); i < max; i++) {
                    PageViewWidget pageViewPane = new PageViewWidget(i, scale, rotation, scrollPane);
                    pageViewPane.setBorder(new Border(new BorderStroke(null, BorderStrokeStyle.SOLID, null,
                            new BorderWidths(1))));
                    tilePane.getChildren().add(pageViewPane);
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
        scrollPane.prefWidthProperty().bind(this.widthProperty());
        scrollPane.prefHeightProperty().bind(this.heightProperty());

        tilePane = new VBox();

        scrollPane.vvalueProperty().addListener((observable, oldValue, newValue) -> {
            long start = System.currentTimeMillis();
            for (int i = 0; i < tilePane.getChildren().size(); i++) {
                Node label = (Node) tilePane.getChildren().get(i);
                if (isNodeIntersectingViewport(scrollPane, label)) {
                    System.out.println("Page " + ((PageViewWidget) label).getPageIndex() + " is in the viewport");
                    // todo trigger a page capture
                    //  - page should be doing intersection check
                    //  - any size change would trigger a repaint
                    //  - eventually bring clipped painting
                    //  - try painting to a buffer and then paint that buffer to the screen (from prevoius work
                    //  - try painting to graphics contet too,  maybe it's fast/optimized for reactive painting.
                }
            }
            long end = System.currentTimeMillis();
//            System.out.println("Viewport check time: " + (end - start) + "ms");
//            System.out.println();
        });

        scrollPane.setContent(tilePane);
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
