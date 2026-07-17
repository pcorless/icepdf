package org.icepdf.fx.ri.viewer.commands.navigation;

import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.Command;

/**
 * Command to navigate to the last page of the document.
 */
public class LastPageCommand implements Command {

    private final ViewerModel model;

    public LastPageCommand(ViewerModel model) {
        this.model = model;
    }

    @Override
    public void execute() {
        if (model.document.get() != null && model.totalPages.get() > 0) {
            model.currentPage.set(model.totalPages.get());
            model.statusMessage.set("Last page");
        }
    }
}

