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
package org.icepdf.ri.common.tools;

import org.icepdf.ri.common.utility.annotation.destinations.NameTreeEditDialog;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;

import java.awt.*;

/**
 * Utility for creating new destinations
 *
 * @since 6.3
 */
public class DestinationHandler extends CommonToolHandler {

    public DestinationHandler(DocumentViewController documentViewController, AbstractPageViewComponent pageViewComponent) {
        super(documentViewController, pageViewComponent);
    }

    @Override
    protected void checkAndApplyPreferences() {

    }

    public void createNewDestination(String name, int x, int y) {
        // convert bbox and start and end line points.
        Rectangle bBox = new Rectangle(x, y, 1, 1);
        Rectangle tBbox = convertToPageSpace(bBox).getBounds();
        new NameTreeEditDialog(documentViewController.getParentController(),
                pageViewComponent.getPage(), name, tBbox.x, tBbox.y).setVisible(true);
    }

    public void createNewDestination(int x, int y) {
        // convert bbox and start and end line points.
        Rectangle bBox = new Rectangle(x, y, 1, 1);
        Rectangle tBbox = convertToPageSpace(bBox).getBounds();
        new NameTreeEditDialog(documentViewController.getParentController(),
                pageViewComponent.getPage(), tBbox.x, tBbox.y).setVisible(true);
    }
}
