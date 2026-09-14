package org.icepdf.fx.ri.viewer.commands.navigation;

import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.Command;

/**
 * Command to navigate to the previous page of the document.
 */
public class PreviousPageCommand implements Command {

    private final ViewerModel model;

    public PreviousPageCommand(ViewerModel model) {
        this.model = model;
    }

    @Override
    public void execute() {
        if (model.document.get() != null && model.currentPage.get() > 1) {
            model.currentPage.set(model.currentPage.get() - 1);
            model.statusMessage.set("Page " + model.currentPage.get());
        }
    }
}

