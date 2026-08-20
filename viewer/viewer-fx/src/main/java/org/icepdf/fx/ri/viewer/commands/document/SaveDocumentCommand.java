package org.icepdf.fx.ri.viewer.commands.document;

import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.Command;

import java.io.File;

/**
 * Command to save the current document.
 * Placeholder implementation - to be completed.
 */
public class SaveDocumentCommand implements Command {

    private final Window window;
    private final ViewerModel model;

    public SaveDocumentCommand(Window window, ViewerModel model) {
        this.window = window;
        this.model = model;
    }

    @Override
    public void execute() {
        if (model.document.get() == null) {
            model.statusMessage.set("No document to save");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save PDF Document");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        // Set initial file name from current file path
        if (model.filePath.get() != null) {
            File currentFile = new File(model.filePath.get());
            fileChooser.setInitialDirectory(currentFile.getParentFile());
            fileChooser.setInitialFileName(currentFile.getName());
        }

        File file = fileChooser.showSaveDialog(window);
        if (file != null) {
            // TODO: Implement actual save logic
            model.statusMessage.set("Save functionality not yet implemented: " + file.getName());
        }
    }
}

