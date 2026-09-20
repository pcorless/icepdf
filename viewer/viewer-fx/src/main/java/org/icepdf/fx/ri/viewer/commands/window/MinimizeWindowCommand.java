package org.icepdf.fx.ri.viewer.commands.window;

import javafx.stage.Stage;
import javafx.stage.Window;
import org.icepdf.fx.ri.viewer.commands.Command;

/**
 * Command to minimize the current window.
 */
public class MinimizeWindowCommand implements Command {

    private final Window window;

    public MinimizeWindowCommand(Window window) {
        this.window = window;
    }

    @Override
    public void execute() {
        if (window instanceof Stage) {
            ((Stage) window).setIconified(true);
        }
    }
}

