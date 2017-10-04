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
package org.icepdf.ri.viewer;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.util.Defs;
import org.icepdf.ri.common.*;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewControllerImpl;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import static org.icepdf.ri.util.PropertiesManager.*;

/**
 * An implementation of WindowManagementCallback to manage the viewer applications
 * windows.
 *
 * @since 1.0
 */
public class WindowManager implements WindowManagementCallback {

    public static final String APPLICATION_WIDTH = "application.width";
    public static final String APPLICATION_HEIGHT = "application.height";
    public static final String APPLICATION_X_OFFSET = "application.x";
    public static final String APPLICATION_Y_OFFSET = "application.y";

    private static WindowManager windowManager;

    private PropertiesManager properties;

    private ArrayList<SwingController> controllers;

    private int newWindowInvocationCounter = 0;

    private ResourceBundle messageBundle = null;

    private WindowManager() {
    }

    public static WindowManager getInstance() {
        return windowManager;
    }

    //window management functions
    public static WindowManager createInstance(PropertiesManager properties, ResourceBundle messageBundle) {

        windowManager = new WindowManager();
        windowManager.properties = properties;
        windowManager.controllers = new ArrayList<SwingController>();

        if (messageBundle != null) {
            windowManager.messageBundle = messageBundle;
        } else {
            windowManager.messageBundle = ResourceBundle.getBundle(
                    PropertiesManager.DEFAULT_MESSAGE_BUNDLE);
        }

        // Anounce ourselves...
        if (Defs.booleanProperty("org.icepdf.core.verbose", true)) {
            System.out.println("\nICEsoft ICEpdf Viewer " + Document.getLibraryVersion());
            System.out.println("Copyright ICEsoft Technologies, Inc.\n");
        }
        return windowManager;
    }

    public PropertiesManager getProperties() {
        return properties;
    }

    public long getNumberOfWindows() {
        return newWindowInvocationCounter;
    }


    public void newWindow(final String location) {
        SwingController controller = commonWindowCreation();
        controller.openDocument(location);
    }

    public void newWindow(final Document document, final String fileName) {
        SwingController controller = commonWindowCreation();
        controller.openDocument(document, fileName);
    }

    public void newWindow(URL location) {
        SwingController controller = commonWindowCreation();
        controller.openDocument(location);
    }

    protected SwingController commonWindowCreation() {
        SwingController controller = new SwingController(messageBundle);
        controller.setWindowManagementCallback(this);

        // assign properties manager.
        controller.setPropertiesManager(properties);

        // add interactive mouse link annotation support
        controller.getDocumentViewController().setAnnotationCallback(
                new MyAnnotationCallback(controller.getDocumentViewController()));

        controllers.add(controller);
        // guild a new swing viewer with remembered view settings.
        int viewType = DocumentViewControllerImpl.ONE_PAGE_VIEW;
        int pageFit = DocumentViewController.PAGE_FIT_WINDOW_WIDTH;
        float pageRotation = 0;
        Preferences viewerPreferences = getProperties().getPreferences();
        try {
            viewType = viewerPreferences.getInt(PROPERTY_DEFAULT_VIEW_TYPE,
                    DocumentViewControllerImpl.ONE_PAGE_VIEW);
            pageFit = viewerPreferences.getInt(PropertiesManager.PROPERTY_DEFAULT_PAGEFIT,
                    DocumentViewController.PAGE_FIT_WINDOW_WIDTH);
            pageRotation = viewerPreferences.getFloat(PropertiesManager.PROPERTY_DEFAULT_ROTATION, pageRotation);
        } catch (NumberFormatException e) {
            // eating error, as we can continue with out alarm
        }

        SwingViewBuilder factory =
                new SwingViewBuilder(controller, viewType, pageFit, pageRotation);

        JFrame frame = factory.buildViewerFrame();
        if (frame != null) {
            newWindowLocation(frame, newWindowInvocationCounter);
            frame.setVisible(true);
        }

        return controller;
    }

    /**
     * Loads the last used windows location as well as other frame related settings and insures the frame is
     * visible.
     *
     * @param frame parent window containers.
     */
    public static void newWindowLocation(JFrame frame) {
        newWindowLocation(frame, 0);
    }

    private static void newWindowLocation(JFrame frame, int newWindowInvocationCounter) {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle bounds = env.getMaximumWindowBounds();
        Preferences prefs = PropertiesManager.getInstance().getPreferences();

        // get the last used window size.
        int width = prefs.getInt(APPLICATION_WIDTH, 800);
        int height = prefs.getInt(APPLICATION_HEIGHT, 600);
        // default center for width on primary screen.
        int x = (int) (bounds.getMinX() + bounds.getWidth() / 2 - width / 2);
        int y = (int) (bounds.getMinY() + bounds.getHeight() / 2 - height / 2);
        // get teh last used window offset.
        int previousX = prefs.getInt(APPLICATION_X_OFFSET, x);
        int previousY = prefs.getInt(APPLICATION_Y_OFFSET, y);

        // quick check to make sure the viewer will be visible in at least one screen, if not we default to primary
        GraphicsDevice[] graphicsDevices = env.getScreenDevices();
        ArrayList<GraphicsDevice> results = new ArrayList<>();
        for (GraphicsDevice screen : graphicsDevices) {
            GraphicsConfiguration config = screen.getDefaultConfiguration();
            if (config.getBounds().contains(previousX, previousY, width, height)) {
                results.add(screen);
            }
        }
        // no intersection with a current screen then we go with the centering coordinates of the primary screen.
        if (results.size() == 0) {
            previousX = x;
            previousY = y;
        }
        prefs.putInt(APPLICATION_X_OFFSET, previousX);
        prefs.putInt(APPLICATION_Y_OFFSET, previousY);
        // apply the corrected size and location.
        frame.setSize(width, height);
        frame.setLocation((previousX + (newWindowInvocationCounter * 10)),
                (previousY + (newWindowInvocationCounter * 10)));
        ++newWindowInvocationCounter;
    }

    public static void saveViewerState(JFrame viewer) {
        if (viewer != null) {
            //save width & height
            Rectangle sz = viewer.getBounds();
            Preferences viewerPreferences = PropertiesManager.getInstance().getPreferences();
            viewerPreferences.putInt(APPLICATION_X_OFFSET, sz.x);
            viewerPreferences.putInt(APPLICATION_Y_OFFSET, sz.y);
            viewerPreferences.putInt(APPLICATION_WIDTH, sz.width);
            viewerPreferences.putInt(APPLICATION_HEIGHT, sz.height);
            viewerPreferences.putInt(PropertiesManager.PROPERTY_DEFAULT_PAGEFIT,
                    viewerPreferences.getInt(PropertiesManager.PROPERTY_DEFAULT_PAGEFIT, 0));
            int viewType = viewerPreferences.getInt(PROPERTY_DEFAULT_VIEW_TYPE, 1);
            // don't save the attachments view as it only applies to specific
            // document types.
            if (viewType != DocumentViewControllerImpl.USE_ATTACHMENTS_VIEW) {
                viewerPreferences.putInt(PROPERTY_DEFAULT_VIEW_TYPE,
                        viewerPreferences.getInt(PROPERTY_DEFAULT_VIEW_TYPE, 1));
            }
            if (ViewModel.getDefaultFilePath() != null) {
                viewerPreferences.put(PROPERTY_DEFAULT_FILE_PATH, ViewModel.getDefaultFilePath());
            }
            if (ViewModel.getDefaultURL() != null) {
                viewerPreferences.put(PROPERTY_DEFAULT_URL, ViewModel.getDefaultURL());
            }
        }
    }

    public void disposeWindow(SwingController controller, JFrame viewer,
                              Preferences preferences) {
        if (controllers.size() <= 1) {
            quit(controller, viewer, preferences);
            return;
        }

        //gets the window to close from the list
        int index = controllers.indexOf(controller);
        if (index >= 0) {
            controllers.remove(index);
            newWindowInvocationCounter--;
            if (viewer != null) {
                viewer.setVisible(false);
                viewer.dispose();
            }
        }
    }

    public void quit(SwingController controller, JFrame viewer,
                     Preferences preferences) {
        saveViewerState(viewer);

        // make sure all the controllers have been disposed.
        for (SwingController c : controllers) {
            if (c == null)
                continue;
            c.dispose();
        }

        System.exit(0);
    }

    public void minimiseAllWindows() {
        for (SwingController controller : controllers) {
            JFrame frame = controller.getViewerFrame();
            if (frame != null)
                frame.setState(Frame.ICONIFIED);
        }
    }

    public void bringAllWindowsToFront(SwingController frontMost) {
        JFrame frontMostFrame = null;
        for (SwingController controller : controllers) {
            JFrame frame = controller.getViewerFrame();
            if (frame != null) {
                if (frontMost == controller) {
                    frontMostFrame = frame;
                    continue;
                }
                frame.setState(Frame.NORMAL);
                frame.toFront();
            }
        }
        if (frontMostFrame != null) {
            frontMostFrame.setState(Frame.NORMAL);
            frontMostFrame.toFront();
        }
    }

    public void bringWindowToFront(int index) {
        if (index >= 0 && index < controllers.size()) {
            SwingController controller = controllers.get(index);
            JFrame frame = controller.getViewerFrame();
            if (frame != null) {
                frame.setState(Frame.NORMAL);
                frame.toFront();
            }
        }
    }

    /**
     * As long as no windows have openned or closed, then the indexes in the
     * returned list should still be valid for doing operations on
     * the respective Controller objects
     *
     * @param giveIndex Give this SwingControllers index in the list as an Integer appended to the List
     * @return List of String objects, each representing an open Document's origin. The last element may be an Integer
     */
    public List getWindowDocumentOriginList(SwingController giveIndex) {
        Integer foundIndex = null;
        int count = controllers.size();
        List<Object> list = new ArrayList<>(count + 1);
        for (int i = 0; i < count; i++) {
            Object toAdd = null;
            SwingController controller = controllers.get(i);
            if (giveIndex == controller)
                foundIndex = i;
            Document document = controller.getDocument();
            if (document != null)
                toAdd = document.getDocumentOrigin();
            list.add(toAdd);
        }
        if (foundIndex != null)
            list.add(foundIndex);
        return list;
    }

    void updateUI() {
        for (SwingController controller : controllers) {
            JFrame frame = controller.getViewerFrame();
            if (frame != null)
                SwingUtilities.updateComponentTreeUI(frame);
        }
    }
}
