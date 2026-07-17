package org.icepdf.fx.ri.viewer.commands.window;

import javafx.stage.Window;
import org.icepdf.fx.ri.ui.dialogs.PreferencesDialog;
import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.Command;

/**
 * Command to open the preferences/settings dialog.
 */
public class PreferencesCommand implements Command {

    private final ViewerModel model;
    private final Window owner;

    public PreferencesCommand(ViewerModel model, Window owner) {
        this.model = model;
        this.owner = owner;
    }

    @Override
    public void execute() {
        PreferencesDialog dialog = new PreferencesDialog(model, owner);
        dialog.showAndWait();
    }
}

