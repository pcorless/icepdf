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
package org.icepdf.fx.ri.actions.tools;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import org.icepdf.core.pobjects.graphics.text.DocumentSelection;
import org.icepdf.fx.ri.actions.AbstractViewerAction;
import org.icepdf.fx.ri.actions.Conditions;
import org.icepdf.fx.ri.actions.ToggleAction;
import org.icepdf.fx.ri.actions.ViewerContext;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.ToolMode;

import java.util.Locale;

/**
 * What a mouse drag on the page does - select text, pan, or make an annotation or a signature
 * field - as one of the {@value #GROUP} set.  Ids: {@code tool.select}, {@code tool.pan},
 * {@code annotation.<kind>} for the annotation tools ({@code annotation.highlight},
 * {@code annotation.free-text}...), {@code signature.sign} for the signature tool.  The annotation
 * tools need a document that permits annotating, the signature tool one that permits filling in.
 * A text-markup tool chosen while text is selected marks up that selection and leaves the tool
 * as it was.
 */
public final class ToolModeAction extends AbstractViewerAction implements ToggleAction {

    public static final String GROUP = "tool";

    private final ToolMode mode;

    public ToolModeAction(ToolMode mode) {
        super(id(mode));
        this.mode = mode;
    }

    /** The id for a tool. */
    public static String id(ToolMode mode) {
        return switch (mode) {
            case TEXT_SELECT -> "tool.select";
            case PAN -> "tool.pan";
            case SIGNATURE -> "signature.sign";
            default -> "annotation." + mode.name().toLowerCase(Locale.ROOT).replace('_', '-');
        };
    }

    public ToolMode mode() {
        return mode;
    }

    @Override
    public String group() {
        return GROUP;
    }

    @Override
    public ObservableBooleanValue enabled(ViewerContext context) {
        if (mode == ToolMode.SIGNATURE) return Conditions.formFillingAllowed(context);
        if (mode.createsAnnotations()) return Conditions.annotationEditingAllowed(context);
        return Conditions.documentOpen(context);
    }

    @Override
    public ObservableBooleanValue selected(ViewerContext context) {
        return Bindings.equal(mode, context.view().toolModeProperty());
    }

    @Override
    public void execute(ViewerContext context) {
        PdfView view = context.view();
        DocumentSelection selection = view.getTextSelection();
        if (marksUpText() && selection != null && !selection.isCollapsed() && view.isAnnotationEditingAllowed()) {
            switch (mode) {
                case HIGHLIGHT -> view.highlightSelection();
                case UNDERLINE -> view.underlineSelection();
                case STRIKE_OUT -> view.strikeOutSelection();
                default -> view.squigglySelection();
            }
            return;
        }
        view.setToolMode(mode);
    }

    private boolean marksUpText() {
        return mode == ToolMode.HIGHLIGHT || mode == ToolMode.UNDERLINE || mode == ToolMode.STRIKE_OUT
                || mode == ToolMode.SQUIGGLY;
    }
}
