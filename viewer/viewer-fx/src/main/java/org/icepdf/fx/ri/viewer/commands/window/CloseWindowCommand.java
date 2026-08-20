package org.icepdf.fx.ri.viewer.commands.window;

import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.Command;

/**
 * Command to close the current window.
 */
public class CloseWindowCommand implements Command {

    private final Window window;
    private final ViewerModel model;

    public CloseWindowCommand(Window window, ViewerModel model) {
        this.window = window;
        this.model = model;
    }

    @Override
    public void execute() {
        // Dispose document if open
        if (model.document.get() != null) {
            model.document.get().dispose();
        }

        // Close the window
        if (window instanceof Stage) {
            ((Stage) window).close();
        } else {
            Platform.exit();
        }
    }
}

