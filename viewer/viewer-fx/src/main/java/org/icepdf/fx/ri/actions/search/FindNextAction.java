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
package org.icepdf.fx.ri.actions.search;

import javafx.beans.value.ObservableBooleanValue;
import org.icepdf.fx.ri.actions.AbstractViewerAction;
import org.icepdf.fx.ri.actions.Conditions;
import org.icepdf.fx.ri.actions.ViewerContext;

/** Goes to the next search hit. */
public final class FindNextAction extends AbstractViewerAction {

    public static final String ID = "search.next";

    public FindNextAction() {
        super(ID, "F3");
    }

    @Override
    public ObservableBooleanValue enabled(ViewerContext context) {
        return Conditions.hasSearchHits(context);
    }

    @Override
    public void execute(ViewerContext context) {
        context.view().nextSearchHit();
    }
}
