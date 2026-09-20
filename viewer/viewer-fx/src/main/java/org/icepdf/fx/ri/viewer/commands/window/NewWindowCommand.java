package org.icepdf.fx.ri.viewer.commands.window;

import javafx.stage.Stage;
import org.icepdf.core.pobjects.Document;
import org.icepdf.fx.ri.viewer.ViewerStageManager;
import org.icepdf.fx.ri.viewer.commands.Command;

/**
 * Command to create a new viewer window.
 */
public class NewWindowCommand implements Command {

    private final Document document;

    public NewWindowCommand() {
        this.document = null;
    }

    public NewWindowCommand(Document document) {
        this.document = document;
    }

    @Override
    public void execute() {
        ViewerStageManager stageManager = ViewerStageManager.getInstance();
        Stage newStage = stageManager.createViewerStage(document);
        newStage.show();
    }
}

