package org.icepdf.fx.ri.viewer.commands.view;

import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.Command;

/**
 * Command to fit the entire page in the viewport.
 */
public class FitPageCommand implements Command {

    private final ViewerModel model;

    public FitPageCommand(ViewerModel model) {
        this.model = model;
    }

    @Override
    public void execute() {
        if (model.document.get() != null) {
            model.fitMode.set(ViewerModel.FitMode.FIT_PAGE);
            // TODO: Calculate actual zoom level based on viewport size and page size
            model.statusMessage.set("Fit Page - calculation not yet implemented");
        }
    }
}

