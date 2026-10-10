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

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import org.icepdf.fx.view.PdfView;

/** The enabled-states actions commonly need, as bindings over a context's {@link PdfView}. */
public final class Conditions {

    private Conditions() {
    }

    /** A document is open. */
    public static BooleanBinding documentOpen(ViewerContext context) {
        return context.view().documentProperty().isNotNull();
    }

    /** A document is open and has unsaved changes. */
    public static BooleanBinding modified(ViewerContext context) {
        return documentOpen(context).and(context.view().modifiedProperty());
    }

    /** A document is open and permits printing. */
    public static BooleanBinding printAllowed(ViewerContext context) {
        return documentOpen(context).and(context.view().printAllowedProperty());
    }

    /** A document is open and permits adding and changing annotations. */
    public static BooleanBinding annotationEditingAllowed(ViewerContext context) {
        return documentOpen(context).and(context.view().annotationEditingAllowedProperty());
    }

    /** A document is open and permits filling in forms (and so signing). */
    public static BooleanBinding formFillingAllowed(ViewerContext context) {
        return documentOpen(context).and(context.view().formFillingAllowedProperty());
    }

    /** Some text is selected and the document permits copying it. */
    public static BooleanBinding canCopy(ViewerContext context) {
        PdfView view = context.view();
        return Bindings.createBooleanBinding(() -> view.getTextSelection() != null
                        && !view.getTextSelection().isCollapsed() && view.isCopyAllowed(),
                view.textSelectionProperty(), view.copyAllowedProperty());
    }

    /** The current search has hits. */
    public static BooleanBinding hasSearchHits(ViewerContext context) {
        return Bindings.isNotEmpty(context.view().getSearchHits());
    }
}
