/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common;

import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import java.net.URL;
import java.util.List;
import java.util.prefs.Preferences;

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
    void newWindow(String path);

    void newWindow(URL url);

    void disposeWindow(Controller controller, JFrame viewer, Preferences preferences);

    void minimiseAllWindows();

    void bringAllWindowsToFront(Controller frontMost);

    void bringWindowToFront(int index);

    List getWindowDocumentOriginList(Controller giveIndex);

    void quit(Controller controller, JFrame viewer,
              Preferences preferences);

    PropertiesManager getProperties();
}
