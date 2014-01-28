/*
 * Copyright 2006-2013 ICEsoft Technologies Inc.
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


import org.icepdf.ri.common.ComponentKeyBinding;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.SwingViewBuilder;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewControllerImpl;

import javax.swing.*;
import java.awt.*;


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

        // build a component controller
        SwingController controller = new SwingController();
        SwingViewBuilder factory = new SwingViewBuilder(controller);
        controller.setIsEmbeddedComponent(true);

        // set the viewController embeddable flag.
        DocumentViewController viewController =
                controller.getDocumentViewController();

        JPanel viewerComponentPanel = factory.buildViewerPanel();

        // add copy keyboard command
        ComponentKeyBinding.install(controller, viewerComponentPanel);

        // add interactive mouse link annotation support via callback
        controller.getDocumentViewController().setAnnotationCallback(
                new org.icepdf.ri.common.MyAnnotationCallback(
                        controller.getDocumentViewController()));

        // build a containing JFrame for display
        JFrame applicationFrame = new JFrame();
        applicationFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        applicationFrame.getContentPane().setLayout(new BorderLayout());
        applicationFrame.getContentPane().add(viewerComponentPanel, BorderLayout.CENTER);
        applicationFrame.getContentPane().add(factory.buildCompleteMenuBar(), BorderLayout.NORTH);
        // Now that the GUI is all in place, we can try opening a PDF

        // hard set the page view to single page which effectively give a single
        // page view. This should be done after openDocument as it has code that
        // can change the view mode if specified by the file.
        controller.setPageViewMode(
                DocumentViewControllerImpl.ONE_PAGE_VIEW,
                false);

        // show the component
        applicationFrame.pack();
        applicationFrame.setVisible(true);
    }
}
