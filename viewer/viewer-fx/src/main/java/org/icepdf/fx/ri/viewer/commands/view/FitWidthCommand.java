package org.icepdf.fx.ri.viewer.commands.view;

import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.Command;

/**
 * Command to fit the document to page width.
 */
public class FitWidthCommand implements Command {

    private final ViewerModel model;

    public FitWidthCommand(ViewerModel model) {
        this.model = model;
    }

    @Override
    public void execute() {
        if (model.document.get() != null) {
            model.fitMode.set(ViewerModel.FitMode.FIT_WIDTH);
            // TODO: Calculate actual zoom level based on viewport width and page width
            model.statusMessage.set("Fit Width - calculation not yet implemented");
        }
    }
}

