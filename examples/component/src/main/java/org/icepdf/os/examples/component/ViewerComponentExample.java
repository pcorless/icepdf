package org.icepdf.os.examples.component;
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

import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.SwingViewBuilder;
import org.icepdf.ri.util.FontPropertiesManager;
import org.icepdf.ri.util.ViewerPropertiesManager;

import javax.swing.*;

/**
 * The <code>ViewerComponentExample</code> class is an example of how to use
 * <code>SwingController</code> and <code>SwingViewBuilder</code>
 * to build a PDF viewer component.  A file specified at the command line is
 * opened in a JFrame which contains the viewer component.
 *
 * @since 2.0
 */
public class ViewerComponentExample {
    public static void main(String[] args) {
        // Get a file from the command line to open
        final String filePath = args[0];

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                // build a component controller
                SwingController controller = new SwingController();
                controller.setIsEmbeddedComponent(true);

                // read stored system font properties.
                FontPropertiesManager.getInstance().loadOrReadSystemFonts();

                ViewerPropertiesManager properties = ViewerPropertiesManager.getInstance();
                properties.getPreferences().putFloat(ViewerPropertiesManager.PROPERTY_DEFAULT_ZOOM_LEVEL, 1.25f);

                SwingViewBuilder factory = new SwingViewBuilder(controller, properties);

                // add interactive mouse link annotation support via callback
                controller.getDocumentViewController().setAnnotationCallback(
                        new org.icepdf.ri.common.MyAnnotationCallback(controller.getDocumentViewController()));
                JPanel viewerComponentPanel = factory.buildViewerPanel();
                JFrame applicationFrame = new JFrame();
                applicationFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                applicationFrame.getContentPane().add(viewerComponentPanel);
                // Now that the GUI is all in place, we can try openning a PDF
                controller.openDocument(filePath);

                // add the window event callback to dispose the controller and
                // currently open document.
                applicationFrame.addWindowListener(controller);

                // show the component
                applicationFrame.pack();
                applicationFrame.setVisible(true);
            }
        });


    }
}
