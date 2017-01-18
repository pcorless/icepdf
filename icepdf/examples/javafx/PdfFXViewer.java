/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
import javafx.application.Application;
import javafx.embed.swing.SwingNode;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.SwingViewBuilder;
import org.icepdf.ri.util.FontPropertiesManager;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ResourceBundle;

/**
 * Example of integrating ICEpdf into a JavaFX application using Java 8 and SwingNode.
 */
public class PdfFXViewer extends Application {

    private static String pdfPath;

    private SwingController swingController;

    private JComponent viewerPanel;

    public static void main(String[] args) {
        pdfPath = args[0];
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        BorderPane borderPane = new BorderPane();
        Scene scene = new Scene(borderPane);

        // add viewer content pane
        createViewer(borderPane);

        borderPane.setPrefSize(1025, 600);

        createResizeListeners(scene, viewerPanel);

        primaryStage.setOnCloseRequest(we -> SwingUtilities.invokeLater(() -> swingController.dispose()));

        primaryStage.setTitle("JavaFX PDF Viewer Demo");
        primaryStage.setScene(scene);
        primaryStage.sizeToScene();
        primaryStage.centerOnScreen();
        primaryStage.show();

        openDocument(pdfPath);
    }

    private void createResizeListeners(Scene scene, JComponent viewerPanel) {
        scene.widthProperty().addListener((observable, oldValue, newValue) -> {
            SwingUtilities.invokeLater(() -> {
                viewerPanel.setSize(new Dimension(newValue.intValue(), (int) scene.getHeight()));
                viewerPanel.setPreferredSize(new Dimension(newValue.intValue(), (int) scene.getHeight()));
                viewerPanel.repaint();
            });
        });

        scene.heightProperty().addListener((observable, oldValue, newValue) -> {
            SwingUtilities.invokeLater(() -> {
                viewerPanel.setSize(new Dimension((int) scene.getWidth(), newValue.intValue()));
                viewerPanel.setPreferredSize(new Dimension((int) scene.getWidth(), newValue.intValue()));
                viewerPanel.repaint();
            });
        });
    }

    private void createViewer(BorderPane borderPane) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                // create the viewer ri components.
                swingController = new SwingController();
                swingController.setIsEmbeddedComponent(true);
                PropertiesManager properties = new PropertiesManager(System.getProperties(),
                        ResourceBundle.getBundle(PropertiesManager.DEFAULT_MESSAGE_BUNDLE));

                // read/store the font cache.
                ResourceBundle messageBundle = ResourceBundle.getBundle(PropertiesManager.DEFAULT_MESSAGE_BUNDLE);
                new FontPropertiesManager(properties, System.getProperties(), messageBundle);
                properties.set(PropertiesManager.PROPERTY_DEFAULT_ZOOM_LEVEL, "1.25");
                properties.set(PropertiesManager.PROPERTY_SHOW_UTILITY_OPEN, "false");
                properties.set(PropertiesManager.PROPERTY_SHOW_UTILITY_SAVE, "false");
                properties.set(PropertiesManager.PROPERTY_SHOW_UTILITY_PRINT, "false");
                // hide the status bar
                properties.set(PropertiesManager.PROPERTY_SHOW_STATUSBAR, "false");
                // hide a few toolbars, just to show how the prefered size of the viewer changes.
                properties.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_FIT, "false");
                properties.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_ROTATE, "false");
                properties.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_TOOL, "false");
                properties.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_FORMS, "false");

                swingController.getDocumentViewController().setAnnotationCallback(
                        new org.icepdf.ri.common.MyAnnotationCallback(swingController.getDocumentViewController()));

                SwingViewBuilder factory = new SwingViewBuilder(swingController, properties);

                viewerPanel = factory.buildViewerPanel();
                viewerPanel.revalidate();

                SwingNode swingNode = new SwingNode();
                swingNode.setContent(viewerPanel);
                borderPane.setCenter(swingNode);
/*
                // add toolbar to the top.
                FlowPane toolBarFlow = new FlowPane();
                JToolBar mainToolbar = factory.buildCompleteToolBar(true);
                buildJToolBar(toolBarFlow, mainToolbar);
                borderPane.setTop(toolBarFlow);

                // main utility pane and viewer
                SwingNode swingNode = new SwingNode();
                viewerPanel = factory.buildUtilityAndDocumentSplitPane(true);
                swingNode.setContent(viewerPanel);
                borderPane.setCenter(swingNode);

                // the page view menubar
                FlowPane statusBarFlow = new FlowPane();
                buildButton(statusBarFlow, factory.buildPageViewSinglePageNonConToggleButton());
                buildButton(statusBarFlow, factory.buildPageViewSinglePageConToggleButton());
                buildButton(statusBarFlow, factory.buildPageViewFacingPageNonConToggleButton());
                buildButton(statusBarFlow, factory.buildPageViewFacingPageConToggleButton());
                borderPane.setBottom(statusBarFlow);
*/


            });
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private void openDocument(String document) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                swingController.openDocument(document);
                viewerPanel.revalidate();
            }
        });

    }

    private void buildButton(FlowPane flowPane, AbstractButton jButton){
        SwingNode swingNode = new SwingNode();
        swingNode.setContent(jButton);
        flowPane.getChildren().add(swingNode);
    }

    private void buildJToolBar(FlowPane flowPane, JToolBar jToolBar){
        SwingNode swingNode = new SwingNode();
        swingNode.setContent(jToolBar);
        flowPane.getChildren().add(swingNode);
    }

}