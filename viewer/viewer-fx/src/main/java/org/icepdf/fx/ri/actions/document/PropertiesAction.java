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
package org.icepdf.fx.ri.actions.document;

import javafx.beans.value.ObservableBooleanValue;
import org.icepdf.fx.panels.DocumentPropertiesDialog;
import org.icepdf.fx.ri.actions.AbstractViewerAction;
import org.icepdf.fx.ri.actions.Conditions;
import org.icepdf.fx.ri.actions.ViewerContext;

/** Shows the document properties: description, security, fonts, custom. */
public final class PropertiesAction extends AbstractViewerAction {

    public static final String ID = "document.properties";

    public PropertiesAction() {
        super(ID, "Shortcut+D");
    }

    @Override
    public ObservableBooleanValue enabled(ViewerContext context) {
        return Conditions.documentOpen(context);
    }

    @Override
    public void execute(ViewerContext context) {
        new DocumentPropertiesDialog(context.window(), context.view().getDocument()).show();
    }
}
