package org.icepdf.fx.ri.viewer.commands.document;

import javafx.print.PrinterJob;
import javafx.stage.Window;
import org.icepdf.fx.ri.ui.dialogs.PrintDialog;
import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.Command;

import java.util.Optional;

/**
 * Command to print the current document.
 */
public class PrintDocumentCommand implements Command {

    private final Window window;
    private final ViewerModel model;

    public PrintDocumentCommand(Window window, ViewerModel model) {
        this.window = window;
        this.model = model;
    }

    @Override
    public void execute() {
        if (model.document.get() == null) {
            model.statusMessage.set("No document to print");
            return;
        }

        PrintDialog dialog = new PrintDialog(model, window);
        Optional<PrinterJob> result = dialog.showAndWait();

        result.ifPresent(printerJob -> {
            // TODO: Implement actual printing logic
            // This would involve rendering pages and sending to printer
            model.statusMessage.set("Printing to " + printerJob.getPrinter().getName() + "...");

            // For now, just show a message
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.INFORMATION,
                    "Print job configured. Actual printing not yet implemented.",
                    javafx.scene.control.ButtonType.OK
            );
            alert.showAndWait();
        });
    }
}

