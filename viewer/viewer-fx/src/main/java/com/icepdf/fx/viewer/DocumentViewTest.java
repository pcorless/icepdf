package com.icepdf.fx.viewer;

import com.icepdf.fx.scene.control.DocumentView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.icepdf.core.pobjects.Document;

/**
 *
 */
public class DocumentViewTest extends Application {

    public static void main(String[] args) throws Exception {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {

        Document document = new Document();

        document.setFile("d:\\pdf-qa\\pdf_reference_addendum_redaction.pdf");

        Scene myScene = new Scene(new DocumentView(document));
        primaryStage.setScene(myScene);

        primaryStage.setWidth(600);
        primaryStage.setHeight(400);
        primaryStage.show();
    }
}