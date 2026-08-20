package org.icepdf.fx.ri.viewer.commands.navigation;

import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.Command;

/**
 * Command to navigate to the next page of the document.
 */
public class NextPageCommand implements Command {

    private final ViewerModel model;

    public NextPageCommand(ViewerModel model) {
        this.model = model;
    }

    @Override
    public void execute() {
        if (model.document.get() != null && model.currentPage.get() < model.totalPages.get()) {
            model.currentPage.set(model.currentPage.get() + 1);
            model.statusMessage.set("Page " + model.currentPage.get());
        }
    }
}

