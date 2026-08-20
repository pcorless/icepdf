package org.icepdf.fx.ri.viewer.commands.document;

import javafx.stage.Window;
import org.icepdf.fx.ri.ui.dialogs.SearchDialog;
import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.Command;

/**
 * Command to open the search dialog.
 */
public class SearchCommand implements Command {

    private final ViewerModel model;
    private final Window owner;
    private SearchDialog searchDialog;

    public SearchCommand(ViewerModel model, Window owner) {
        this.model = model;
        this.owner = owner;
    }

    @Override
    public void execute() {
        // Reuse existing search dialog if open
        if (searchDialog == null || !searchDialog.isShowing()) {
            searchDialog = new SearchDialog(model, owner);
        }

        if (!searchDialog.isShowing()) {
            searchDialog.show();
        } else {
            searchDialog.toFront();
            searchDialog.requestFocus();
        }
    }
}

