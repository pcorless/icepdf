package org.icepdf.fx.ri.viewer;

import javafx.beans.property.*;
import org.icepdf.core.pobjects.Document;

public class ViewerModel {

    public final BooleanProperty useSingleViewerStage = new SimpleBooleanProperty(false);

    // document
    public final ObjectProperty<Document> document;

    // todo, pretty sure we don't need this property:  file/url path
    public final StringProperty filePath;

    // toolbar disabled state
    public BooleanProperty toolbarDisabled;

    // zoom factor increment
    public final FloatProperty zoomFactorIncrement = new SimpleFloatProperty(0.1f);

    public ViewerModel() {
        document = new SimpleObjectProperty<>(null);
        filePath = new SimpleStringProperty(null);
        toolbarDisabled = new SimpleBooleanProperty(true);
    }
}
