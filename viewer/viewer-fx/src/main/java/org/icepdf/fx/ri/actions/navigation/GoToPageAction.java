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
package org.icepdf.fx.ri.actions.navigation;

import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.control.TextInputDialog;
import org.icepdf.fx.ri.actions.AbstractViewerAction;
import org.icepdf.fx.ri.actions.Conditions;
import org.icepdf.fx.ri.actions.ViewerContext;
import org.icepdf.fx.view.PdfView;

/** Asks for a page number and goes there. */
public final class GoToPageAction extends AbstractViewerAction {

    public static final String ID = "navigation.go-to";

    public GoToPageAction() {
        super(ID, "Shortcut+G");
    }

    @Override
    public ObservableBooleanValue enabled(ViewerContext context) {
        return Conditions.documentOpen(context);
    }

    @Override
    public void execute(ViewerContext context) {
        PdfView view = context.view();
        TextInputDialog dialog = new TextInputDialog(String.valueOf(view.getCurrentPageIndex() + 1));
        if (context.window() != null) dialog.initOwner(context.window());
        dialog.setTitle(label());
        dialog.setHeaderText(null);
        dialog.setContentText("Page (1 - " + view.getPageCount() + "):");
        dialog.showAndWait().ifPresent(text -> {
            try {
                view.setCurrentPageIndex(Integer.parseInt(text.trim()) - 1);
            } catch (NumberFormatException ignored) {
                // not a page number
            }
        });
    }
}
