package org.icepdf.fx.ri.viewer;

import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.icepdf.core.pobjects.Document;
import org.icepdf.fx.ri.viewer.listeners.StageCloseRequestListener;

public class ViewerStageManager {

    private static ViewerStageManager singleton;

    private ViewerStageManager() {
    }

    // todo keep track of open stages

    public void createViewer(Stage stage, Document document) {
        FxController controller = new FxController();
        controller.getModel().document.set(document);
        Scene scene = new Scene(controller.getView(), 400, 200);
        stage.setScene(scene);
        stage.setOnCloseRequest(new StageCloseRequestListener(controller.getModel()));
    }

    public Stage createViewerStage(Document document) {
        Stage stage = new Stage();
        setTitleAndIcons(stage);
        createViewer(stage, document);
        return stage;
    }

    public void setTitleAndIcons(Stage stage) {
        stage.setTitle("Icepdf Viewer");
        stage.getIcons().addAll(
                new Image(ViewerStageManager.class.getResourceAsStream(
                        "/org/icepdf/fx/images/icepdf-app-icon-32x32.png")),
                new Image(ViewerStageManager.class.getResourceAsStream(
                        "/org/icepdf/fx/images/icepdf-app-icon-64x64.png")));
    }

    public static ViewerStageManager getInstance() {
        if (singleton == null) {
            singleton = new ViewerStageManager();
        }
        return singleton;
    }
}
