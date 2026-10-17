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

import javafx.beans.value.ObservableBooleanValue;

/**
 * An action with an on/off state: a check menu item or toggle button.  With a {@link #group()} it's
 * one of a set where exactly one is on - a radio menu item - such as the view modes or the tools.
 * {@link #execute} switches it (or, in a group, selects it).
 */
public interface ToggleAction extends ViewerAction {

    /** Whether it's on, for the given context. */
    ObservableBooleanValue selected(ViewerContext context);

    /** The set it belongs to, or null for a plain on/off toggle. */
    default String group() {
        return null;
    }
}
