/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.fx.ri.actions.view;

import javafx.beans.value.ObservableBooleanValue;
import org.icepdf.fx.ri.actions.AbstractViewerAction;
import org.icepdf.fx.ri.actions.ToggleAction;
import org.icepdf.fx.ri.actions.ViewerContext;

/** Shows or hides the side panel. */
public final class SidePanelAction extends AbstractViewerAction implements ToggleAction {

    public static final String ID = "view.side-panel";

    public SidePanelAction() {
        super(ID, "F4");
    }

    @Override
    public boolean isAvailable(ViewerContext context) {
        return context.has(ViewerContext.Capability.SIDE_PANEL);
    }

    @Override
    public ObservableBooleanValue selected(ViewerContext context) {
        return context.sidePanelVisibleProperty();
    }

    @Override
    public void execute(ViewerContext context) {
        context.sidePanelVisibleProperty().set(!context.sidePanelVisibleProperty().get());
    }
}
