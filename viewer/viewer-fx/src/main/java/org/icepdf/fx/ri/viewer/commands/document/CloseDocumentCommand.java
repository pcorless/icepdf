package org.icepdf.fx.ri.viewer.commands.document;

import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.Command;

/**
 * Command to close the currently open document.
 */
public class CloseDocumentCommand implements Command {

    private final ViewerModel model;

    public CloseDocumentCommand(ViewerModel model) {
        this.model = model;
    }

    @Override
    public void execute() {
        if (model.document.get() != null) {
            // Dispose the document
            model.document.get().dispose();

            // Clear model state
            model.document.set(null);
            model.filePath.set(null);
            model.documentTitle.set("");
            model.documentSizeBytes.set(0);
            model.currentPage.set(1);
            model.totalPages.set(0);
            model.selectedText.set("");

            // Reset view state
            model.zoomLevel.set(1.0);
            model.rotationAngle.set(0.0);
            model.fitMode.set(ViewerModel.FitMode.NONE);

            model.statusMessage.set("Document closed");
        }
    }
}

