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
package org.icepdf.fx.view.internal.tool;

import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import org.icepdf.fx.view.PagePoint;
import org.icepdf.fx.view.internal.ViewHost;
import org.icepdf.fx.view.internal.annot.AnnotationController;
import org.icepdf.fx.view.internal.annot.AnnotationEdits;
import org.icepdf.fx.view.internal.annot.AnnotationHit;
import org.icepdf.fx.view.internal.form.FormFields;
import org.icepdf.fx.view.internal.form.SignatureFields;
import org.icepdf.fx.view.internal.text.CaretNavigator;

/**
 * What a {@link ToolHandler} may ask of the view it runs in, beyond {@link ViewHost}: the
 * annotation, form field and signature controllers, viewport scrolling and cursors, hit testing,
 * annotation drags and the caret.  The view's skin implements it; points are in viewport
 * coordinates unless a name says otherwise.
 */
public interface ToolContext extends ViewHost {

    AnnotationController annotations();

    FormFields fields();

    SignatureFields signatures();

    // ---- viewport

    double getViewportWidth();

    double getViewportHeight();

    void scrollBy(double dx, double dy);

    void setViewportCursor(Cursor cursor);

    /** Back to the active tool's idle cursor. */
    void restoreViewportCursor();

    // ---- hit testing

    /** Like {@link #pageAtViewport}, but a point off the pages snaps to the nearest page edge. */
    PagePoint nearestPageAtViewport(double vx, double vy);

    /** Converts a viewport point into a page's view space. */
    Point2D toPageView(int pageIndex, double vx, double vy);

    /** True if the viewport point is over a glyph of a page whose text is loaded. */
    boolean isOverText(double vx, double vy);

    /** The selected annotation's handle under a viewport point, or -1. */
    int handleAtViewport(double vx, double vy);

    // ---- moving and resizing annotations

    /** Starts a move (handle -1) or resize. */
    void beginAnnotationDrag(AnnotationHit hit, int handle);

    /** Follows the pointer: (dx, dy) is the total viewport movement since the drag began. */
    void updateAnnotationDrag(double dx, double dy);

    /** Ends the drag; commits it unless {@code cancel}, and returns the edit (or null). */
    AnnotationEdits.Edit endAnnotationDrag(double dx, double dy, boolean cancel);

    // ---- caret

    /** Keyboard caret movement over the loaded text. */
    CaretNavigator caretNavigator();

    /** Brings the selection's focus caret into view. */
    void revealCaret();
}
