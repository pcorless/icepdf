package org.icepdf.fx.ri.viewer.commands.navigation;

import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.Command;

/**
 * Command to navigate to the first page of the document.
 */
public class FirstPageCommand implements Command {

    private final ViewerModel model;

    public FirstPageCommand(ViewerModel model) {
        this.model = model;
    }

    @Override
    public void execute() {
        if (model.document.get() != null && model.totalPages.get() > 0) {
            model.currentPage.set(1);
            model.statusMessage.set("First page");
        }
    }
}

