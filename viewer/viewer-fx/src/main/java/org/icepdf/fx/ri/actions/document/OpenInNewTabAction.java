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

import org.icepdf.fx.ri.actions.AbstractViewerAction;
import org.icepdf.fx.ri.actions.ViewerContext;

/** Asks for a PDF and opens it in a new tab, leaving the current document alone. */
public final class OpenInNewTabAction extends AbstractViewerAction {

    public static final String ID = "document.open-new-tab";

    public OpenInNewTabAction() {
        super(ID, "Shortcut+Shift+O");
    }

    @Override
    public boolean isAvailable(ViewerContext context) {
        return context.has(ViewerContext.Capability.NEW_TAB) && context.has(ViewerContext.Capability.OPEN);
    }

    @Override
    public void execute(ViewerContext context) {
        context.openDocumentInNewTab();
    }
}
