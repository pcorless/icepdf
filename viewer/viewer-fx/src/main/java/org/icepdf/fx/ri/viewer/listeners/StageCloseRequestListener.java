package org.icepdf.fx.ri.viewer.listeners;

import javafx.event.EventHandler;
import javafx.stage.WindowEvent;
import org.icepdf.core.pobjects.Document;
import org.icepdf.fx.ri.viewer.ViewerModel;

/**
 * Stage close request listener that will dispose of the document when the stage is closed.
 */
public class StageCloseRequestListener implements EventHandler<WindowEvent> {

    private final ViewerModel model;

    public StageCloseRequestListener(ViewerModel model) {
        this.model = model;
    }

    @Override
    public void handle(WindowEvent event) {
        if (event.getEventType() == WindowEvent.WINDOW_CLOSE_REQUEST) {
            Document document = model.document.get();
            if (document != null) {
                document.dispose();
            }
        }
    }
}
