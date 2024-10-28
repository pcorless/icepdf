package org.icepdf.fx.ri.views;

import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import org.icepdf.core.pobjects.Document;

public class DocumentViewPane extends Region {

    private FloatProperty scale = new SimpleFloatProperty(1.0f);
    private FloatProperty rotation = new SimpleFloatProperty(0.0f);
    private IntegerProperty currentPageIndex = new SimpleIntegerProperty(0);


    public DocumentViewPane(Document document) {
        createLayout(document);
    }

    private void createLayout(Document document) {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.prefWidthProperty().bind(this.widthProperty());
        scrollPane.prefHeightProperty().bind(this.heightProperty());

        TilePane tilePane = new TilePane();
        tilePane.setPrefColumns(1);
        tilePane.setVgap(25);
        tilePane.setHgap(25);

        // create a page view for each page
        for (int i = 0; i < 250; i++) {
            PageViewWidget pageViewPane = new PageViewWidget(i, scale, rotation);
            pageViewPane.setBorder(new Border(new BorderStroke(null, BorderStrokeStyle.SOLID, null,
                    new BorderWidths(1))));
            tilePane.getChildren().add(pageViewPane);
        }
        scrollPane.setContent(tilePane);
        getChildren().add(scrollPane);
    }

    public FloatProperty scaleProperty() {
        return scale;
    }

}
