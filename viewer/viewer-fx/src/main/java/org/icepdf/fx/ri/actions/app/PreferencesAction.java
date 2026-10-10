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
package org.icepdf.fx.ri.actions.app;

import org.icepdf.fx.ri.actions.AbstractViewerAction;
import org.icepdf.fx.ri.actions.ViewerContext;

/** Shows the viewer preferences. */
public final class PreferencesAction extends AbstractViewerAction {

    public static final String ID = "app.preferences";

    public PreferencesAction() {
        super(ID, "Shortcut+Comma");
    }

    @Override
    public boolean isAvailable(ViewerContext context) {
        return context.has(ViewerContext.Capability.PREFERENCES);
    }

    @Override
    public void execute(ViewerContext context) {
        context.showPreferences();
    }
}
