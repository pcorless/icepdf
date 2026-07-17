package org.icepdf.fx.ri.viewer.commands.document;

import javafx.stage.Window;
import org.icepdf.fx.ri.ui.dialogs.DocumentPropertiesDialog;
import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.Command;

/**
 * Command to show document properties dialog.
 */
public class DocumentPropertiesCommand implements Command {

    private final Window window;
    private final ViewerModel model;

    public DocumentPropertiesCommand(Window window, ViewerModel model) {
        this.window = window;
        this.model = model;
    }

    @Override
    public void execute() {
        if (model.document.get() == null) {
            model.statusMessage.set("No document to show properties");
            return;
        }

        DocumentPropertiesDialog dialog = new DocumentPropertiesDialog(model, window);
        dialog.showAndWait();
    }
}

