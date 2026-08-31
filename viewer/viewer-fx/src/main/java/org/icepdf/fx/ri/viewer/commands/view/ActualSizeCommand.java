package org.icepdf.fx.ri.viewer.commands.view;

import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.Command;

/**
 * Command to set zoom to actual size (100%).
 */
public class ActualSizeCommand implements Command {

    private final ViewerModel model;

    public ActualSizeCommand(ViewerModel model) {
        this.model = model;
    }

    @Override
    public void execute() {
        if (model.document.get() != null) {
            model.zoomLevel.set(1.0);
            model.fitMode.set(ViewerModel.FitMode.ACTUAL_SIZE);
            model.statusMessage.set("Actual Size (100%)");
        }
    }
}

