package org.icepdf.fx.ri.viewer;

import javafx.application.Application;
import javafx.stage.Stage;
import org.icepdf.core.pobjects.Document;

import java.util.logging.Logger;

public class Launcher extends Application {

    private static final Logger logger = Logger.getLogger(Launcher.class.toString());


    public static void main(String[] args) throws Exception {
        Application.launch(args);
    }


    @Override
    public void start(final Stage primaryStage) throws Exception {

        String filePath = System.getenv().get("-loadfile");
        Document document = null;
        if (filePath != null) {
            document = new Document();
            document.setFile(filePath);
        }


        // read stored system font properties.
//        FontPropertiesManager.getInstance().loadOrReadSystemFonts();

        // setup the viewer ri properties manager

        ViewerStageManager stageManager = ViewerStageManager.getInstance();
        stageManager.createViewer(primaryStage, document);
        stageManager.setTitleAndIcons(primaryStage);

        primaryStage.show();


    }


}