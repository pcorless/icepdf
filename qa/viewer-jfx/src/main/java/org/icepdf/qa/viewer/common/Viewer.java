package org.icepdf.qa.viewer.common;

import javafx.application.Platform;
import org.icepdf.qa.config.CaptureSet;
import org.icepdf.qa.config.Result;

import javax.swing.*;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * Launches the PDF viewer RI via the class path specified in the capture set and loads the sample file
 * as specified in the Result object.
 */
public class Viewer {

    // font manager reflection
    public static final String MESSAGE_BUNDLE_CLASS = "org.icepdf.ri.resources.MessageBundle";
    public static final String PROPERTIES_MANAGER_CLASS = "org.icepdf.ri.util.PropertiesManager";
    public static final String FONT_PROPERTIES_MANAGER_CLASS = "org.icepdf.ri.util.FontPropertiesManager";

    public static final String SWING_CONTROLLER_CLASS = "org.icepdf.ri.common.SwingController";
    public static final String OPEN_DOCUMENT_METHOD = "openDocument";

    public static final String SWING_VIEW_BUILDER_CLASS = "org.icepdf.ri.common.SwingViewBuilder";
    public static final String BUILD_VIEWER_PANEL_METHOD = "buildViewerFrame";

    public static void launchViewer(Result result, CaptureSet captureSet) {
        Platform.runLater(() -> {
                    try {
                        SwingUtilities.invokeAndWait(() -> {

                            String filePath = result.getDocumentName();

                            Path baseDir = Paths.get(captureSet.getClassPath());
                            ArrayList<URL> classPath = new ArrayList<>(baseDir.getNameCount());
                            try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir, "*.{class,jar}")) {
                                for (Path file : stream) {
                                    classPath.add(file.normalize().toUri().toURL());
                                }
                            } catch (IOException | DirectoryIteratorException x) {
                                System.err.println(x);
                            }
                            URLClassLoader classLoader = URLClassLoader.newInstance(classPath.toArray(new URL[0]));

                            /*
                              Create a new instance so we can view the modified file.
                             */
                            try {

                                // load the font manager
                                ResourceBundle messageBundle = ResourceBundle.getBundle(MESSAGE_BUNDLE_CLASS,
                                        Locale.ENGLISH, classLoader);
                                Class<?> propertiesManagerClass = classLoader.loadClass(PROPERTIES_MANAGER_CLASS);
                                Constructor propertiesManagerConstructor = propertiesManagerClass.getDeclaredConstructor(
                                        Properties.class, ResourceBundle.class);
                                Object propertiesManagerObject = propertiesManagerConstructor.newInstance(System.getProperties(), messageBundle);

                                Class<?> fontPropertiesManagerClass = classLoader.loadClass(FONT_PROPERTIES_MANAGER_CLASS);
                                Constructor fontPropertiesManagerConstructor = fontPropertiesManagerClass.getDeclaredConstructor(
                                        propertiesManagerClass, Properties.class, ResourceBundle.class);
                                fontPropertiesManagerConstructor.newInstance(propertiesManagerObject, System.getProperties(), messageBundle);

                                Class<?> swingControllerClass = classLoader.loadClass(SWING_CONTROLLER_CLASS);
                                Constructor swingControllerConstructor = swingControllerClass.getDeclaredConstructor();
                                Object swingControllerObject = swingControllerConstructor.newInstance();

                                Class<?> swingViewBuilderClass = classLoader.loadClass(SWING_VIEW_BUILDER_CLASS);
                                Constructor swingViewBuilderConstructor = swingViewBuilderClass.getDeclaredConstructor(swingControllerClass);
                                Object swingViewBuilderObject = swingViewBuilderConstructor.newInstance(swingControllerObject);

                                // build the gui elements.
                                Method buildViewerPanelMethod = swingViewBuilderClass.getMethod(BUILD_VIEWER_PANEL_METHOD);
                                JFrame applicationFrame = (JFrame) buildViewerPanelMethod.invoke(swingViewBuilderObject);

                                // open the document
                                Method openDocumentMethod = swingControllerClass.getMethod(OPEN_DOCUMENT_METHOD, String.class);
                                openDocumentMethod.invoke(swingControllerObject, filePath);

                                applicationFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);


                                // show the document and the new annotations.
                                applicationFrame.pack();
                                applicationFrame.setVisible(true);
                            } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException |
                                     IllegalAccessException | InstantiationException e) {
                                e.printStackTrace();
                            }

                        });
                    } catch (InterruptedException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }

        );

    }

}
