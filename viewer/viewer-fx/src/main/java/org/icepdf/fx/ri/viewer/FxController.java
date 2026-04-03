package org.icepdf.fx.ri.viewer;

import javafx.scene.layout.Region;
import javafx.stage.Window;
import org.icepdf.fx.ri.viewer.listeners.DocumentChangeListener;

/**
 * Controller for the viewer application.
 */
public class FxController {

    private final ViewerModel model;
    private final Interactor interactor;
    private ViewBuilder viewBuilder;
    private Window window;

    public FxController() {
        this.model = new ViewerModel();
        this.interactor = new Interactor(model);

        // auto clean up this viewer if the document changes
        this.model.document.addListener(new DocumentChangeListener(model));
    }

    public ViewerModel getModel() {
        return model;
    }

    public Region getView(Window window) {
        this.window = window;
        this.viewBuilder = new ViewBuilder(model, interactor, window);
        return viewBuilder.build();
    }

    public Region getView() {
        if (viewBuilder == null) {
            throw new IllegalStateException("Must call getView(Window) first");
        }
        return viewBuilder.build();
    }
}
