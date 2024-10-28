package org.icepdf.fx.ri.viewer.commands;

import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.ViewerStageManager;

import java.io.File;
import java.io.IOException;

public class OpenFileCommand implements Command {

    private final Window stage;
    private final ViewerModel model;

    public OpenFileCommand(Window parentStage, ViewerModel model) {
        this.model = model;
        this.stage = parentStage;
    }

    @Override
    public void execute() {
        // show file chooser
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Resource File");
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            // set document
            try {
                Document document = new Document();

                if (model.useSingleViewerStage.get()) {
                    model.document.set(document);
                    model.filePath.set(file.getAbsolutePath());
                } else {
                    ViewerStageManager stageManager = ViewerStageManager.getInstance();
                    Stage stage = stageManager.createViewerStage(document);
                    stageManager.setTitleAndIcons(stage);
                    stage.show();
                }

                // todo push of a thread to load the document, use Consumer to make sure UI is updated properly
                document.setFile(file.getAbsolutePath());

            } catch (PDFSecurityException | IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }
}
