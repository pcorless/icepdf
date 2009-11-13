/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.icepdf.ri.common;

import com.apple.mrj.*;
import javax.swing.SwingUtilities;

/**
 * This class is used to register for native Mac OS Application menu callback
 * events for the "About" and "Quit" menuitems.
 *
 * This class requires the AppleJavaExtensions.jar to be on the classpath.
 *
 */
public class MacOSAppMenuEventHandler implements MRJAboutHandler,                                                
                                                 MRJQuitHandler {

    SwingController swingController;

    public MacOSAppMenuEventHandler(SwingController controller) {
        swingController = controller;
        MRJApplicationUtils.registerAboutHandler(this);
        MRJApplicationUtils.registerQuitHandler(this);

    }

    public void handleAbout() {
        Runnable doSwingWork = new Runnable() {

            public void run() {
                swingController.showAboutDialog();
            }
        };
        SwingUtilities.invokeLater(doSwingWork);
    }

    public void handleQuit() throws IllegalStateException {
        swingController.exit();
    }
}
