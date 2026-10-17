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
import javafx.scene.input.MouseEvent;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.fx.signature.SignatureStatus;
import org.icepdf.fx.view.PagePoint;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.internal.annot.AnnotationEdits;
import org.icepdf.fx.view.internal.annot.AnnotationHit;
import org.icepdf.fx.view.internal.annot.AnnotationUiLayer;
import org.icepdf.fx.view.internal.text.CaretNavigator;

import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.Collection;

/**
 * What a {@link ToolHandler} may ask of the view it runs in: viewport scrolling and cursors, hit
 * testing, annotation drags and creation, the caret, fields and signatures.  The view's skin
 * implements it; points are in viewport coordinates unless a name says otherwise.
 */
public interface ToolContext {

    /** The control the tools act on, for its public API (selection, clipboard, actions). */
    PdfView view();

    /** Records an edit on the view's undo history. */
    void recordEdit(AnnotationEdits.Edit edit);

    // ---- viewport

    double getViewportWidth();

    double getViewportHeight();

    void scrollBy(double dx, double dy);

    void setViewportCursor(Cursor cursor);

    /** Back to the active tool's idle cursor. */
    void restoreViewportCursor();

    // ---- hit testing

    /** The page and user-space point under a viewport point, or null between or outside pages. */
    PagePoint pageAtViewport(double vx, double vy);

    /** Like {@link #pageAtViewport}, but a point off the pages snaps to the nearest page edge. */
    PagePoint nearestPageAtViewport(double vx, double vy);

    /** Converts a viewport point into a page's view space. */
    Point2D toPageView(int pageIndex, double vx, double vy);

    /** True if the viewport point is over a glyph of a page whose text is loaded. */
    boolean isOverText(double vx, double vy);

    /** The topmost interactive annotation under a viewport point, or null. */
    AnnotationHit annotationAtViewport(double vx, double vy);

    /** The topmost fillable widget under a viewport point, or null. */
    AnnotationHit fieldAtViewport(double vx, double vy);

    /** The signature field with a checked status under a viewport point, or null. */
    AnnotationHit signatureAtViewport(double vx, double vy);

    /** The selected annotation's handle under a viewport point, or -1. */
    int handleAtViewport(double vx, double vy);

    /** Follows the link (or action) under a viewport point; true if there was one. */
    boolean activateLinkAtViewport(double vx, double vy);

    // ---- annotations

    /** The selected annotation and its page, or null. */
    AnnotationHit selectedHit();

    /** Sets the hover outline (null clears). */
    void setHovered(AnnotationHit hit);

    /** True if the annotation may be moved and resized. */
    boolean canMove(Annotation annotation);

    /** Starts a move (handle -1) or resize. */
    void beginAnnotationDrag(AnnotationHit hit, int handle);

    /** Follows the pointer: (dx, dy) is the total viewport movement since the drag began. */
    void updateAnnotationDrag(double dx, double dy);

    /** Ends the drag; commits it unless {@code cancel}, and returns the edit (or null). */
    AnnotationEdits.Edit endAnnotationDrag(double dx, double dy, boolean cancel);

    /** Opens the inline editor on a free text annotation. */
    void editFreeText(AnnotationHit hit);

    /** The UI layer of a visible page, or null. */
    AnnotationUiLayer uiLayer(int pageIndex);

    Collection<AnnotationUiLayer> uiLayers();

    // ---- creation (page view coordinates)

    void createNote(int pageIndex, double viewX, double viewY);

    void createFreeText(int pageIndex, double viewX, double viewY);

    void createShape(int pageIndex, boolean ellipse, Rectangle2D viewRect);

    void createLine(int pageIndex, Point2D start, Point2D end);

    void createInk(int pageIndex, GeneralPath viewPath);

    void createRedactionBox(int pageIndex, Rectangle2D viewRect);

    void createSignatureField(int pageIndex, Rectangle2D viewRect);

    /** Turns the text selection into text markup annotations; returns the number created. */
    int markupSelection(Name subtype);

    // ---- text, fields, signatures

    /** The page's loaded text, or null if it isn't loaded (or has none). */
    TextSequence textSequence(int pageIndex);

    /** Keyboard caret movement over the loaded text. */
    CaretNavigator caretNavigator();

    /** Brings the selection's focus caret into view. */
    void revealCaret();

    /** A click on a field: focus it, toggle it, or run its action. */
    void pressField(AnnotationHit field, MouseEvent e);

    SignatureStatus signatureStatusOf(Annotation annotation);

    /** Reports a signature field click to the application. */
    void signatureClicked(SignatureStatus status);
}
