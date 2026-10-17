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

import org.icepdf.fx.view.PdfView;

import org.icepdf.fx.view.internal.form.FormController;
import org.icepdf.fx.view.internal.annot.AnnotationHit;
import org.icepdf.fx.view.internal.text.CaretNavigator;
import org.icepdf.fx.view.internal.text.SelectionController;

import org.icepdf.fx.view.internal.annot.AnnotationGeometry;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Cursor;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;
import org.icepdf.core.pobjects.graphics.text.DocumentSelection;

/**
 * Text selection tool.  Converts mouse events to page points and delegates the selection logic to
 * {@link SelectionController}; adds an I-beam cursor over text and auto-scroll while a drag holds
 * the pointer near or past the viewport edge.
 */
public final class TextSelectHandler implements ToolHandler {

    // auto-scroll starts this close to the edge, in logical px, and speeds up with distance past it.
    private static final double EDGE = 24;
    private static final double MAX_STEP = 60;

    private final ToolContext context;
    private final SelectionController controller;
    private final Timeline autoScroll;
    private double lastX;
    private double lastY;

    public TextSelectHandler(ToolContext context) {
        this.context = context;
        this.controller = new SelectionController(context::textSequence);
        autoScroll = new Timeline(new KeyFrame(Duration.millis(16), e -> autoScrollTick()));
        autoScroll.setCycleCount(Animation.INDEFINITE);
    }

    @Override
    public void uninstall() {
        autoScroll.stop();
    }

    // a press on a link: followed on release unless the pointer moved (then it was a text drag).
    private AnnotationHit pendingLink;
    private double pressX;
    private double pressY;
    // a press that selected an annotation: no text selection for this gesture.
    private boolean annotationGesture;
    // an editable annotation pressed: becomes a move once the pointer travels a few px.
    private AnnotationHit pendingMove;
    private boolean draggingAnnotation;
    // in a text-markup tool, the subtype the selection becomes on release; null for plain select.
    private org.icepdf.core.pobjects.Name markupSubtype;

    public void setMarkupSubtype(org.icepdf.core.pobjects.Name subtype) {
        markupSubtype = subtype;
    }

    @Override
    public void moved(MouseEvent e) {
        AnnotationHit field = context.fields().fieldAtViewport(e.getX(), e.getY());
        if (field != null) {
            context.annotations().setHovered(null);
            context.setViewportCursor(fieldCursor((org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation) field.annotation()));
            return;
        }
        int handle = context.handleAtViewport(e.getX(), e.getY());
        if (handle >= 0) {
            context.annotations().setHovered(null);
            context.setViewportCursor(resizeCursor(handle));
            return;
        }
        if (context.signatures().fieldAtViewport(e.getX(), e.getY()) != null) {
            context.annotations().setHovered(null);
            context.setViewportCursor(Cursor.HAND);
            return;
        }
        AnnotationHit hit = context.annotations().annotationAtViewport(e.getX(), e.getY());
        context.annotations().setHovered(hit != null && !hit.isActionable() ? hit : null);
        if (hit != null) {
            boolean selectedEditable = hit.annotation() == context.view().getSelectedAnnotation()
                    && context.annotations().canMove(hit.annotation());
            context.setViewportCursor(hit.isActionable() ? Cursor.HAND
                    : selectedEditable ? Cursor.MOVE : Cursor.DEFAULT);
        } else {
            context.setViewportCursor(context.isOverText(e.getX(), e.getY()) ? Cursor.TEXT : Cursor.DEFAULT);
        }
    }

    private static Cursor resizeCursor(int handle) {
        return switch (AnnotationGeometry.direction(handle)) {
            case "nw" -> Cursor.NW_RESIZE;
            case "n" -> Cursor.N_RESIZE;
            case "ne" -> Cursor.NE_RESIZE;
            case "e" -> Cursor.E_RESIZE;
            case "se" -> Cursor.SE_RESIZE;
            case "s" -> Cursor.S_RESIZE;
            case "sw" -> Cursor.SW_RESIZE;
            default -> Cursor.W_RESIZE;
        };
    }

    @Override
    public void pressed(MouseEvent e) {
        lastX = e.getX();
        lastY = e.getY();
        pressX = e.getX();
        pressY = e.getY();
        PdfView view = context.view();
        annotationGesture = false;
        pendingLink = null;
        pendingMove = null;
        draggingAnnotation = false;
        // a form field: give it focus (its editor opens); no text selection for this gesture.
        AnnotationHit field = markupSubtype == null ? context.fields().fieldAtViewport(e.getX(), e.getY()) : null;
        if (field != null) {
            view.clearAnnotationSelection();
            context.fields().press(field);
            annotationGesture = true;
            return;
        }
        if (view.getFocusedField() != null) view.clearFieldFocus();
        // a signature field: its properties (or the application's handler); no text selection.
        AnnotationHit signature = markupSubtype == null && e.getClickCount() == 1
                ? context.signatures().fieldAtViewport(e.getX(), e.getY()) : null;
        if (signature != null) {
            view.clearAnnotationSelection();
            annotationGesture = true;
            context.signatures().clicked(context.signatures().statusOf(signature.annotation()));
            return;
        }
        // a handle of the selected annotation: resize straight away.
        int handle = context.handleAtViewport(e.getX(), e.getY());
        if (handle >= 0) {
            AnnotationHit selected = context.annotations().selectedHit();
            if (selected != null) {
                annotationGesture = true;
                draggingAnnotation = true;
                context.beginAnnotationDrag(selected, handle);
                return;
            }
        }
        AnnotationHit hit = context.annotations().annotationAtViewport(e.getX(), e.getY());
        if (hit != null && hit.isActionable()) {
            pendingLink = hit;
        } else if (hit != null) {
            // annotations win over text: select it, no text selection for this gesture.
            view.selectAnnotation(hit.annotation());
            // double-click a free text box to edit its text in place.
            if (e.getClickCount() == 2 && hit.annotation() instanceof org.icepdf.core.pobjects.annotations.FreeTextAnnotation) {
                context.annotations().editFreeText(hit);
                annotationGesture = true;
                return;
            }
            // double-click opens (or closes) a markup annotation's popup note, as in Acrobat.
            if (e.getClickCount() == 2 && hit.annotation() instanceof org.icepdf.core.pobjects.annotations.MarkupAnnotation m
                    && m.getPopupAnnotation() != null) {
                view.setPopupOpen(m, !m.getPopupAnnotation().isOpen());
                annotationGesture = true;
                return;
            }
            annotationGesture = true;
            if (context.annotations().canMove(hit.annotation())) pendingMove = hit;
            return;
        }
        view.clearAnnotationSelection();
        view.setTextSelection(controller.press(view.getTextSelection(), context.pageAtViewport(e.getX(), e.getY()),
                e.getClickCount(), e.isShiftDown()));
    }

    @Override
    public void dragged(MouseEvent e) {
        if (annotationGesture) {
            double dx = e.getX() - pressX;
            double dy = e.getY() - pressY;
            if (pendingMove != null && Math.hypot(dx, dy) > 3) {
                context.beginAnnotationDrag(pendingMove, -1);
                pendingMove = null;
                draggingAnnotation = true;
            }
            if (draggingAnnotation) context.updateAnnotationDrag(dx, dy);
            return;
        }
        if (pendingLink != null && Math.hypot(e.getX() - pressX, e.getY() - pressY) > 4) pendingLink = null;
        lastX = e.getX();
        lastY = e.getY();
        extendToLast();
        if (edgeStep(lastX, context.getViewportWidth()) != 0 || edgeStep(lastY, context.getViewportHeight()) != 0) {
            if (autoScroll.getStatus() != Animation.Status.RUNNING) autoScroll.play();
        } else {
            autoScroll.stop();
        }
    }

    @Override
    public void released(MouseEvent e) {
        autoScroll.stop();
        controller.release();
        if (pendingLink != null) {
            AnnotationHit link = pendingLink;
            pendingLink = null;
            context.view().performAnnotationAction(link.annotation());
        }
        if (draggingAnnotation) {
            draggingAnnotation = false;
            context.recordEdit(context.endAnnotationDrag(e.getX() - pressX, e.getY() - pressY, false));
        }
        if (markupSubtype != null && !annotationGesture) context.annotations().markupSelection(markupSubtype);
        pendingMove = null;
        annotationGesture = false;
    }

    /**
     * Ctrl+A select all, Ctrl+C copy, Esc clear; with a selection, arrows move the caret (shift
     * extends, ctrl by word) and Home/End go to the line's edges.  Without a selection the keys
     * fall through to the view's scrolling.
     */
    @Override
    public boolean keyPressed(KeyEvent e) {
        PdfView view = context.view();
        DocumentSelection selection = view.getTextSelection();
        boolean shortcut = e.isShortcutDown();
        switch (e.getCode()) {
            case A:
                if (!shortcut) return false;
                view.selectAll();
                return true;
            case C:
                if (!shortcut || selection == null || selection.isCollapsed()) return false;
                view.copySelection();
                return true;
            case DELETE:
            case BACK_SPACE:
                if (view.getSelectedAnnotation() == null) return false;
                view.deleteSelectedAnnotation();
                return true;
            case ESCAPE:
                if (draggingAnnotation) {
                    draggingAnnotation = false;
                    context.endAnnotationDrag(0, 0, true);
                    return true;
                }
                if (view.getSelectedAnnotation() != null) {
                    view.clearAnnotationSelection();
                    return true;
                }
                if (selection == null) return false;
                view.clearSelection();
                return true;
            default:
                break;
        }
        if (selection == null) return false;
        CaretNavigator.Move move;
        switch (e.getCode()) {
            case LEFT:
                move = shortcut ? CaretNavigator.Move.WORD_LEFT : CaretNavigator.Move.LEFT;
                break;
            case RIGHT:
                move = shortcut ? CaretNavigator.Move.WORD_RIGHT : CaretNavigator.Move.RIGHT;
                break;
            case UP:
                move = CaretNavigator.Move.UP;
                break;
            case DOWN:
                move = CaretNavigator.Move.DOWN;
                break;
            case HOME:
                if (shortcut) return false; // document start: the view's scrolling
                move = CaretNavigator.Move.LINE_START;
                break;
            case END:
                if (shortcut) return false;
                move = CaretNavigator.Move.LINE_END;
                break;
            default:
                return false;
        }
        DocumentSelection moved = context.caretNavigator().move(selection, move, e.isShiftDown());
        if (moved != selection) {
            view.setTextSelection(moved);
            context.revealCaret();
        }
        // consumed even when nothing moved (page text loading, document edge) so the view doesn't
        // scroll instead.
        return true;
    }

    @Override
    public Cursor idleCursor() {
        return Cursor.DEFAULT;
    }

    private void extendToLast() {
        PdfView view = context.view();
        // between pages, or past the viewport edge, the nearest page edge stands in for the pointer.
        view.setTextSelection(controller.drag(view.getTextSelection(), context.nearestPageAtViewport(lastX, lastY)));
    }

    private void autoScrollTick() {
        double dx = edgeStep(lastX, context.getViewportWidth());
        double dy = edgeStep(lastY, context.getViewportHeight());
        if (dx == 0 && dy == 0) {
            autoScroll.stop();
            return;
        }
        context.scrollBy(dx, dy);
        // the document moved under a still pointer: re-resolve the drag point.
        extendToLast();
    }

    /** Cursor over a fillable field: an I-beam for text, a hand for buttons and lists. */
    private static Cursor fieldCursor(org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation widget) {
        FormController.FieldKind kind = FormController.kindOf(widget);
        return kind == FormController.FieldKind.TEXT || kind == FormController.FieldKind.PASSWORD
                ? Cursor.TEXT : Cursor.HAND;
    }

    /** Scroll step for one axis: 0 inside the edge band, growing with distance into or past it. */
    public static double edgeStep(double position, double size) {
        double step;
        if (position < EDGE) step = position - EDGE;
        else if (position > size - EDGE) step = position - (size - EDGE);
        else return 0;
        return Math.max(-MAX_STEP, Math.min(MAX_STEP, step / 2));
    }
}
