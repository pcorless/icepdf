/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.util.font;

import org.icepdf.ri.util.FontPropertiesManager;

import javax.swing.*;
import java.awt.*;

/**
 * Common swing worker for clear the font cache
 *
 * @since 6.3
 */
public class ClearFontCacheWorker extends SwingWorker<Void, Void> {

    private final JComponent callingComponent;

    public ClearFontCacheWorker(JComponent callingComponent) {
        this.callingComponent = callingComponent;
        callingComponent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    }

    @Override
    protected Void doInBackground() {
        FontPropertiesManager fontPropertiesManager = FontPropertiesManager.getInstance();
        fontPropertiesManager.clearProperties();
        fontPropertiesManager.readDefaultFontProperties();
        fontPropertiesManager.saveProperties();
        return null;
    }

    @Override
    protected void done() {
        callingComponent.setEnabled(true);
        callingComponent.setCursor(null);
    }
}
