package org.icepdf.os.examples;

import javax.swing.*;

/**
 * Launches the Main Viewer RI.
 */
public class WebStart {

    public static void main(final String[] args) {
        // Call the main method of the application's Main class
        // using Reflection so that related classes resoving happens
        // after splash window is shown up
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    Class.forName("org.icepdf.ri.viewer.Main")
                            .getMethod("main", String[].class)
                            .invoke(null, new Object[]{args});
                } catch (Throwable e) {
                    e.printStackTrace();
                    System.err.flush();
                    System.exit(10);
                }
            }
        });
    }
}
