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

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import org.icepdf.fx.ri.actions.AbstractViewerAction;
import org.icepdf.fx.ri.actions.ToggleAction;
import org.icepdf.fx.ri.actions.ViewerContext;
import org.icepdf.fx.view.ViewMode;

import java.util.Locale;

/**
 * One page layout - single page, continuous, facing, facing continuous - as one of the
 * {@value #GROUP} set.  Ids: {@code view.mode.single-page}, {@code view.mode.continuous},
 * {@code view.mode.facing}, {@code view.mode.facing-continuous}.
 */
public final class ViewModeAction extends AbstractViewerAction implements ToggleAction {

    public static final String GROUP = "view.mode";

    private final ViewMode mode;

    public ViewModeAction(ViewMode mode) {
        super(id(mode));
        this.mode = mode;
    }

    /** The id for a layout. */
    public static String id(ViewMode mode) {
        return GROUP + "." + mode.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public ViewMode mode() {
        return mode;
    }

    @Override
    public String group() {
        return GROUP;
    }

    @Override
    public ObservableBooleanValue selected(ViewerContext context) {
        return Bindings.equal(mode, context.view().viewModeProperty());
    }

    @Override
    public void execute(ViewerContext context) {
        context.view().setViewMode(mode);
    }
}
