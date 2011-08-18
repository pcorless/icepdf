/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2011 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */

import org.icepdf.ri.common.MyAnnotationCallback;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.SwingViewBuilder;

import javax.swing.*;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>Use this applet on your site to launch the PDF Viewer in a browser.</p>
 * <p/>
 * <p>A sample HTML applet tag for starting this class:</p>
 * <p/>
 * <pre>
 *      &lt;applet
 *              width="800"
 *              height="600"
 *              archive="icepdf-core.jar, icepdf-viewer.jar, icepdf-applet.jar"
 *              title="ICEpdf Applet Example" &gt;
 *          &lt;param name="type" value="application/x-java-applet;jpi-version=1.5.0" /&gt;
 *          &lt;param  name="java_arguments" value="-Xmx128m" /&gt;
 *          &lt;param name="classloader_cache" value="false" /&gt;
 *          &lt;param name="url" value="http://www.icepdf.org/pdf/ICEpdf-Datasheet.pdf" /&gt;
 *          &lt;param name="code" value="ViewerApplet.class" /&gt;
 *      &lt;/applet&gt;
 * </pre>
 * <p/>
 * <p><b>Note:</b><br/>
 * If you would like to load none local URLs, this class will have to
 * be added to a signed jar.</p>
 *
 * @since 1.0
 */
public class ViewerApplet extends JApplet {

    private static final Logger logger =
            Logger.getLogger(ViewerApplet.class.toString());

    SwingController controller;

    public ViewerApplet() throws HeadlessException {
        super();
    }

    /**
     * Creates an Applet which contains the default viewer.
     */
    public void init() {
        logger.fine("Initializing ICEpdf Viewer Applet");
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    // create a controller and a swing factory
                    controller = new SwingController();
                    SwingViewBuilder factory = new SwingViewBuilder(controller);
                    // add interactive mouse link annotation support via callback
                    controller.getDocumentViewController().setAnnotationCallback(
                            new org.icepdf.ri.common.MyAnnotationCallback(
                                    controller.getDocumentViewController()));

                    // build viewer component and add it to the applet content pane.
                    MyAnnotationCallback myAnnotationCallback = new MyAnnotationCallback(
                            controller.getDocumentViewController());
                    controller.getDocumentViewController().setAnnotationCallback(myAnnotationCallback);

                    // build the viewer with a menubar
                    getContentPane().setLayout(new BorderLayout());
                    getContentPane().add(factory.buildViewerPanel(), BorderLayout.CENTER);
                    getContentPane().add(factory.buildCompleteMenuBar(), BorderLayout.NORTH);
                }
            });
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error creating GUI", e);
        }
    }

    public void start() {
        logger.fine("Starting ICEpdf Viewer Applet");
        // Open a url if available
        final String url = getParameter("url");
        // resolve the url
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    URL documentURL = null;
                    try {
                        documentURL = new URL(url);
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    }
                    // Now that the GUI is all in place, we can try opening a PDF
                    if (documentURL != null) {
                        controller.openDocument(documentURL);
                    }
                }
            });
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Loading file didn't successfully complete", e);
        }

    }

    public void stop() {
        logger.fine("Stopping ICEpdf Viewer Applet");
        // closing document.
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    if (controller != null) {
                        controller.closeDocument();
                    }
                }
            });
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Closing file didn't successfully complete", e);
        }
    }

    /**
     * Dispose of the document.
     */
    public void destroy() {
        logger.fine("Distroying ICEpdf Viewer Applet");
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    // dispose the viewer component
                    if (controller != null) {
                        controller.dispose();
                        controller = null;
                    }
                    getContentPane().removeAll();
                }
            });
        } catch (Exception e) {
            logger.log(Level.SEVERE,
                    "Destroying ICEpdf controller didn't successfully complete", e);
        }
    }
}
