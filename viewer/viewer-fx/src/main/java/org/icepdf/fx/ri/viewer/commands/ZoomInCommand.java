package org.icepdf.fx.ri.viewer.commands;

import javafx.beans.property.FloatProperty;
import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.views.DocumentViewPane;

public class ZoomInCommand implements Command {
    private final ViewerModel model;
    private final DocumentViewPane documentViewPane;


    public ZoomInCommand(DocumentViewPane documentViewPane, ViewerModel model) {
        this.model = model;
        this.documentViewPane = documentViewPane;
    }

    @Override
    public void execute() {
        // get the current zoom level
        FloatProperty scale = documentViewPane.scaleProperty();
        scale.set(scale.get() - model.zoomFactorIncrement.get());
    }
}
