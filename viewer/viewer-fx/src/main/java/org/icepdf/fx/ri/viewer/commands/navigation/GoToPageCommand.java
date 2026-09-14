package org.icepdf.fx.ri.viewer.commands.navigation;

import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.Command;

/**
 * Command to navigate to a specific page number.
 */
public class GoToPageCommand implements Command {

    private final ViewerModel model;
    private final int pageNumber;

    public GoToPageCommand(ViewerModel model, int pageNumber) {
        this.model = model;
        this.pageNumber = pageNumber;
    }

    @Override
    public void execute() {
        if (model.document.get() != null &&
                pageNumber >= 1 &&
                pageNumber <= model.totalPages.get()) {
            model.currentPage.set(pageNumber);
            model.statusMessage.set("Page " + pageNumber);
        } else if (model.document.get() != null) {
            model.statusMessage.set("Invalid page number: " + pageNumber +
                    " (valid range: 1-" + model.totalPages.get() + ")");
        }
    }
}

