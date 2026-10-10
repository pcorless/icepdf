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
package org.icepdf.fx.ri.actions;

import org.icepdf.fx.ri.actions.app.AboutAction;
import org.icepdf.fx.ri.actions.app.ExitAction;
import org.icepdf.fx.ri.actions.app.NewWindowAction;
import org.icepdf.fx.ri.actions.app.PreferencesAction;
import org.icepdf.fx.ri.actions.document.CloseAction;
import org.icepdf.fx.ri.actions.document.OpenAction;
import org.icepdf.fx.ri.actions.document.PrintAction;
import org.icepdf.fx.ri.actions.document.PropertiesAction;
import org.icepdf.fx.ri.actions.document.SaveAction;
import org.icepdf.fx.ri.actions.document.SaveAsAction;
import org.icepdf.fx.ri.actions.edit.CopyAction;
import org.icepdf.fx.ri.actions.edit.DeleteAnnotationAction;
import org.icepdf.fx.ri.actions.edit.RedoAction;
import org.icepdf.fx.ri.actions.edit.SelectAllAction;
import org.icepdf.fx.ri.actions.edit.UndoAction;
import org.icepdf.fx.ri.actions.forms.ResetFormAction;
import org.icepdf.fx.ri.actions.navigation.FirstPageAction;
import org.icepdf.fx.ri.actions.navigation.GoToPageAction;
import org.icepdf.fx.ri.actions.navigation.LastPageAction;
import org.icepdf.fx.ri.actions.navigation.NextPageAction;
import org.icepdf.fx.ri.actions.navigation.PreviousPageAction;
import org.icepdf.fx.ri.actions.search.FindAction;
import org.icepdf.fx.ri.actions.search.FindNextAction;
import org.icepdf.fx.ri.actions.search.FindPreviousAction;
import org.icepdf.fx.ri.actions.search.SearchPanelAction;
import org.icepdf.fx.ri.actions.tools.ToolModeAction;
import org.icepdf.fx.ri.actions.view.*;
import org.icepdf.fx.view.ToolMode;
import org.icepdf.fx.view.ViewMode;

/** Every built-in action, grouped as they're listed in a full menu. */
public final class StandardActions {

    private StandardActions() {
    }

    /** A new registry holding every built-in action. */
    public static ActionRegistry registry() {
        ActionRegistry registry = new ActionRegistry();
        // document
        registry.register(new OpenAction()).register(new SaveAction()).register(new SaveAsAction())
                .register(new PrintAction()).register(new PropertiesAction()).register(new CloseAction());
        // application
        registry.register(new NewWindowAction()).register(new PreferencesAction()).register(new AboutAction())
                .register(new ExitAction());
        // edit
        registry.register(new UndoAction()).register(new RedoAction()).register(new CopyAction())
                .register(new SelectAllAction()).register(new DeleteAnnotationAction());
        // search
        registry.register(new FindAction()).register(new SearchPanelAction()).register(new FindNextAction())
                .register(new FindPreviousAction());
        // view
        registry.register(new SidePanelAction()).register(new ShowCommentsAction()).register(new FullScreenAction())
                .register(new ZoomInAction()).register(new ZoomOutAction()).register(new ActualSizeAction())
                .register(new FitPageAction()).register(new FitWidthAction())
                .register(new RotateClockwiseAction()).register(new RotateCounterclockwiseAction());
        for (ViewMode mode : ViewMode.values()) registry.register(new ViewModeAction(mode));
        registry.register(new CoverPageAction()).register(new ShowAnnotationsAction()).register(new HighlightFieldsAction());
        // navigation
        registry.register(new FirstPageAction()).register(new PreviousPageAction()).register(new NextPageAction())
                .register(new LastPageAction()).register(new GoToPageAction());
        // tools, annotation tools, signing
        for (ToolMode mode : ToolMode.values()) registry.register(new ToolModeAction(mode));
        // forms
        registry.register(new ResetFormAction());
        return registry;
    }
}
