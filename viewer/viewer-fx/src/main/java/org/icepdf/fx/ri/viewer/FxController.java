package org.icepdf.fx.ri.viewer;

import javafx.scene.layout.Region;
import org.icepdf.fx.ri.viewer.listeners.DocumentChangeListener;

/**
 * Controller for the viewer application.
 */
public class FxController {

    private final ViewerModel model;
    private final Interactor interactor;
    private final ViewBuilder viewBuilder;

    public FxController() {
        this.model = new ViewerModel();
        this.interactor = new Interactor(model); // is this really a mediator?
        this.viewBuilder = new ViewBuilder(model);

        // auto clean up this viewer if the document changes
        this.model.document.addListener(new DocumentChangeListener(model));
    }

    public ViewerModel getModel() {
        return model;
    }

    public Region getView() {
        return viewBuilder.build();
    }
}
