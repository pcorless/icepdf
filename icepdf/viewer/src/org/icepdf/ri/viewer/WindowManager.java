/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
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
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * An implementation of WindowManagementCallback to manage the viewer applications
 * windows.
 *
 * @since 1.0
 */
public class WindowManager implements WindowManagementCallback {
    private PropertiesManager properties;

    private ArrayList<SwingController> controllers;

    private long newWindowInvokationCounter = 0;

    private ResourceBundle messageBundle = null;

    public WindowManager(PropertiesManager properties) {
        this(properties, null);
    }

    //window management functions
    public WindowManager(PropertiesManager properties, ResourceBundle messageBundle) {
        this.properties = properties;
        controllers = new ArrayList<SwingController>();

        if (messageBundle != null) {
            this.messageBundle = messageBundle;
        } else {
            this.messageBundle = ResourceBundle.getBundle(
                    PropertiesManager.DEFAULT_MESSAGE_BUNDLE);
        }

        // Annouce ourselves...
        if (Defs.booleanProperty("org.icepdf.core.verbose", true)) {
            System.out.println("\nICEsoft ICEpdf Viewer " + Document.getLibraryVersion());
            System.out.println("Copyright ICEsoft Technologies, Inc.\n");
        }
    }

    public PropertiesManager getProperties() {
        return properties;
    }

    public long getNumberOfWindows() {
        return newWindowInvokationCounter;
    }


    public void newWindow(final String location) {
        SwingController controller = commonWindowCreation();
        controller.openDocument(location);
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
        try {
            viewType = getProperties().getInt("document.viewtype",
                    DocumentViewControllerImpl.ONE_PAGE_VIEW);
            pageFit = getProperties().getInt(
                    PropertiesManager.PROPERTY_DEFAULT_PAGEFIT,
                    DocumentViewController.PAGE_FIT_WINDOW_WIDTH);
        } catch (NumberFormatException e) {
            // eating error, as we can continue with out alarm
        }

        SwingViewBuilder factory =
                new SwingViewBuilder(controller, viewType, pageFit);

        JFrame frame = factory.buildViewerFrame();
        if (frame != null) {
            int width = getProperties().getInt("application.width", 800);
            int height = getProperties().getInt("application.height", 600);
            frame.setSize(width, height);

            int x = getProperties().getInt("application.x", 1);
            int y = getProperties().getInt("application.y", 1);

            frame.setLocation((int) (x + (newWindowInvokationCounter * 10)),
                    (int) (y + (newWindowInvokationCounter * 10)));
            ++newWindowInvokationCounter;
            frame.setVisible(true);
        }

        return controller;
    }

    public void disposeWindow(SwingController controller, JFrame viewer,
                              Properties properties) {
        if (controllers.size() <= 1) {
            quit(controller, viewer, properties);
            return;
        }

        //gets the window to close from the list
        int index = controllers.indexOf(controller);
        if (index >= 0) {
            controllers.remove(index);
            newWindowInvokationCounter--;
            if (viewer != null) {
                viewer.setVisible(false);
                viewer.dispose();
            }
        }
    }

    public void quit(SwingController controller, JFrame viewer,
                     Properties properties) {
        if (controller != null && viewer != null) {
            //save width & height
            Rectangle sz = viewer.getBounds();
            getProperties().setInt("application.x", sz.x);
            getProperties().setInt("application.y", sz.y);
            getProperties().setInt("application.height", sz.height);
            getProperties().setInt("application.width", sz.width);
            if (properties != null) {
                getProperties().set(PropertiesManager.PROPERTY_DEFAULT_PAGEFIT,
                        properties.getProperty(PropertiesManager.PROPERTY_DEFAULT_PAGEFIT));
                getProperties().set("document.viewtype", properties.getProperty("document.viewtype"));
            }
            getProperties().setDefaultFilePath(ViewModel.getDefaultFilePath());
            getProperties().setDefaultURL(ViewModel.getDefaultURL());
        }

        // save all the rest, cookies, bookmarks, etc.
        getProperties().saveAndEnd();

        // make sure all the controllers have been disposed.
        for (int i = 0; i < controllers.size(); i++) {
            SwingController c = controllers.get(i);
            if (c == null)
                continue;
            c.dispose();
        }

        System.exit(0);
    }

    public void minimiseAllWindows() {
        for (int i = 0; i < controllers.size(); i++) {
            SwingController controller = controllers.get(i);
            JFrame frame = controller.getViewerFrame();
            if (frame != null)
                frame.setState(Frame.ICONIFIED);
        }
    }

    public void bringAllWindowsToFront(SwingController frontMost) {
        JFrame frontMostFrame = null;
        for (int i = 0; i < controllers.size(); i++) {
            SwingController controller = controllers.get(i);
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
        List list = new ArrayList(count + 1);
        for (int i = 0; i < count; i++) {
            Object toAdd = null;
            SwingController controller = controllers.get(i);
            if (giveIndex == controller)
                foundIndex = new Integer(i);
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
        for (int i = 0; i < controllers.size(); i++) {
            SwingController controller = controllers.get(i);
            JFrame frame = controller.getViewerFrame();
            if (frame != null)
                SwingUtilities.updateComponentTreeUI(frame);
        }
    }
}
