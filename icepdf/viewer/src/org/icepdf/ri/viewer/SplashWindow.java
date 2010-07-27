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
 * The Original Code is ICEpdf 4.1 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
package org.icepdf.ri.viewer;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Construct a JWindow, paints a splash image, and centers the frame on the
 * screen.
 *
 * @author Icesoft Technologies.
 */
final class SplashWindow extends JWindow {

    private static final Logger logger =
            Logger.getLogger(SplashWindow.class.toString());

    private Image splashImage;
    private MediaTracker mediaTracker;

    /**
     * Constructs a splash window and takes splash image
     *
     * @param image The splash image to be displayed
     */
    public SplashWindow(Image image) {
        splashImage = image;
    }

    /**
     * Shows splash screen and centers it on screen
     */
    public void splash() {
        mediaTracker = new MediaTracker(this);
        setSize(splashImage.getWidth(null), splashImage.getHeight(null));

        mediaTracker.addImage(splashImage, 0);
        try {
            mediaTracker.waitForID(0);
        } catch (InterruptedException ex) {
            logger.log(Level.FINE, "Failed to track splash image load.", ex);
        }

        setSize(splashImage.getWidth(null), splashImage.getHeight(null));
        center();
        setVisible(true);
    }

    /**
     * Centers this frame on the screen.
     */
    private void center() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle frame = getBounds();
        setLocation((screen.width - frame.width) / 2,
                (screen.height - frame.height) / 2);
    }

    /**
     * Paint the splash image to the frame
     */
    public void paint(Graphics graphics) {
        if (splashImage != null) {
            graphics.drawImage(splashImage, 0, 0, this);
        }
    }
}