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
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
package org.icepdf.ri.common;

import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import java.net.URL;
import java.util.List;
import java.util.Properties;

/**
 * <p>An interface that describes the necessary methods needed for common
 * window management.  An application may need to centrally manage the process
 * of opening and closing new windows, as well as requests
 * to end the program. This interface facilitates that capability.
 *
 * @author Mark Collette
 * @since 2.0
 */
public interface WindowManagementCallback {
    public void newWindow(String path);

    public void newWindow(URL url);

    public void disposeWindow(SwingController controller, JFrame viewer,
                              Properties properties);

    public void minimiseAllWindows();

    public void bringAllWindowsToFront(SwingController frontMost);

    public void bringWindowToFront(int index);

    public List getWindowDocumentOriginList(SwingController giveIndex);

    public void quit(SwingController controller, JFrame viewer,
                     Properties properties);

    public PropertiesManager getProperties();
}
