package org.icepdf.fx.ri.viewer.commands.window;

import javafx.stage.Window;
import org.icepdf.fx.ri.ui.dialogs.AboutDialog;
import org.icepdf.fx.ri.viewer.commands.Command;

/**
 * Command to open the About dialog.
 */
public class AboutCommand implements Command {

    private final Window owner;

    public AboutCommand(Window owner) {
        this.owner = owner;
    }

    @Override
    public void execute() {
        AboutDialog dialog = new AboutDialog(owner);
        dialog.showAndWait();
    }
}

