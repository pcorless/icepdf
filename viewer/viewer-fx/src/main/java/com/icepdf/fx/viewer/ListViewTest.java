package com.icepdf.fx.viewer;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

/**
 * Created by pcorl_000 on 2017-03-30.
 */
public class ListViewTest extends Application {

    public static void main(String[] args) throws Exception {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        ListView<String> listView = new ListView<>();
        ObservableList<String> list = FXCollections.observableArrayList();
        for (int i = 0; i < 3; i++) {
            list.add("string cell " + (i + 1));
        }

        listView.setItems(list);

        Scene myScene = new Scene(listView);
        primaryStage.setScene(myScene);

        primaryStage.setWidth(600);
        primaryStage.setHeight(400);
        primaryStage.show();
    }
}
