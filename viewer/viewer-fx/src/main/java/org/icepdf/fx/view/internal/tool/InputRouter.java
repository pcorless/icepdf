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
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.TextMarkupAnnotation;
import org.icepdf.fx.view.FitMode;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.ToolMode;
import org.icepdf.fx.view.internal.annot.AnnotationHit;

/**
 * Routes the view's mouse, wheel, pinch and key input: primary-button gestures to the active
 * tool's handler, middle-button and Space+drag to panning whatever the tool, wheel and keys to
 * scrolling, zoom and page turns, and the field, undo and annotation-menu shortcuts.  A gesture
 * belongs to the handler it started on until its button is released.
 */
public final class InputRouter {

    /** What the router needs of the view beyond {@link ToolContext}: the scroll position. */
    public interface Host extends ToolContext {

        /** False until a document is laid out. */
        boolean hasLayout();

        double scrollX();

        double scrollY();

        /** Scrolls to a document-space position, clamped to the document. */
        void scrollTo(double x, double y);

        /** Zooms by {@code factor} holding the document point under a viewport point still. */
        void zoomAround(double vx, double vy, double factor);

        /** The node mouse events arrive on; event coordinates are in its parent's (the control's). */
        Node viewportNode();
    }

    public static final double SCROLL_UNIT = 40;

    private final Host host;
    private final PanHandler panHandler;
    private final TextSelectHandler textSelectHandler;
    private final AnnotationCreateHandler createHandler;
    private final RedactHandler redactHandler;
    private ToolHandler activeHandler;
    // owns a press-drag-release sequence from start to end.
    private ToolHandler gestureHandler;
    private MouseButton gestureButton;
    private boolean spaceDown;
    // a Space press that panned is not also a page-down when released.
    private boolean spacePanned;
    private ContextMenu annotationMenu;

    public InputRouter(Host host) {
        this.host = host;
        panHandler = new PanHandler(host);
        textSelectHandler = new TextSelectHandler(host);
        createHandler = new AnnotationCreateHandler(host);
        redactHandler = new RedactHandler(host, textSelectHandler, createHandler);
    }

    /** Makes the tool mode's handler the active one. */
    public void installTool(ToolMode mode) {
        textSelectHandler.setMarkupSubtype(switch (mode) {
            case HIGHLIGHT -> TextMarkupAnnotation.SUBTYPE_HIGHLIGHT;
            case UNDERLINE -> TextMarkupAnnotation.SUBTYPE_UNDERLINE;
            case STRIKE_OUT -> TextMarkupAnnotation.SUBTYPE_STRIKE_OUT;
            case SQUIGGLY -> TextMarkupAnnotation.SUBTYPE_SQUIGGLY;
            case REDACT -> Annotation.SUBTYPE_REDACT;
            default -> null;
        });
        createHandler.setMode(mode);
        ToolHandler next = mode == ToolMode.PAN ? panHandler : mode == ToolMode.REDACT ? redactHandler
                : mode.selectsText() ? textSelectHandler : createHandler;
        if (next == activeHandler) return;
        if (activeHandler != null) activeHandler.uninstall();
        activeHandler = next;
        gestureHandler = null;
        activeHandler.install();
        host.restoreViewportCursor();
    }

    public void dispose() {
        if (activeHandler != null) activeHandler.uninstall();
    }

    /** The cursor for the current state: hand while Space is held, else the active tool's. */
    public Cursor idleCursor() {
        return spaceDown ? Cursor.OPEN_HAND : activeHandler.idleCursor();
    }

    // ---- wheel and pinch

    public void onScroll(ScrollEvent e) {
        if (!host.hasLayout()) return;
        if (e.isShortcutDown()) {
            if (e.getDeltaY() == 0) return;
            zoomAround(e.getX(), e.getY(), Math.pow(1.1, e.getDeltaY() / 40));
        } else {
            double dx = e.getDeltaX();
            double dy = e.getDeltaY();
            if (e.isShiftDown() && dx == 0) {
                dx = dy;
                dy = 0;
            }
            host.scrollTo(host.scrollX() - dx, host.scrollY() - dy);
        }
        e.consume();
    }

    public void onPinch(ZoomEvent e) {
        zoomAround(e.getX(), e.getY(), e.getZoomFactor());
        e.consume();
    }

    /** (x, y) in the control's coordinates. */
    private void zoomAround(double x, double y, double factor) {
        Point2D local = host.viewportNode().parentToLocal(x, y);
        host.zoomAround(local.getX(), local.getY(), factor);
    }

    // ---- mouse

    /** Right-click on an editable annotation: select it and show the application's menu for it. */
    public void onContextMenu(ContextMenuEvent e) {
        PdfView view = host.view();
        var factory = view.annotationMenuFactoryProperty().get();
        if (factory == null || host.document() == null) return;
        Point2D local = host.viewportNode().sceneToLocal(e.getSceneX(), e.getSceneY());
        AnnotationHit hit = local != null ? host.annotations().annotationAtViewport(local.getX(), local.getY()) : null;
        if (hit == null || !host.annotations().canEdit(hit.annotation())) return;
        ContextMenu menu = factory.apply(hit.annotation());
        if (menu == null || menu.getItems().isEmpty()) return;
        if (annotationMenu != null) annotationMenu.hide();
        view.selectAnnotation(hit.annotation());
        annotationMenu = menu;
        menu.show(view, e.getScreenX(), e.getScreenY());
        e.consume();
    }

    public void onPress(MouseEvent e) {
        PdfView view = host.view();
        if (!view.isFocused()) view.requestFocus();
        if (gestureHandler != null) {
            if (e.getButton() != gestureButton) return; // a second button during a gesture
            // the same button again: its release went elsewhere (a popup the press opened, such as a
            // combo field's drop-down, takes it), so that gesture is over.
            gestureHandler = null;
        }
        if (e.getButton() == MouseButton.MIDDLE || (spaceDown && e.getButton() == MouseButton.PRIMARY)) {
            gestureHandler = panHandler;
            spacePanned |= spaceDown;
        } else if (e.getButton() == MouseButton.PRIMARY) {
            gestureHandler = activeHandler;
        } else {
            return;
        }
        gestureButton = e.getButton();
        gestureHandler.pressed(e);
        e.consume();
    }

    public void onDrag(MouseEvent e) {
        if (gestureHandler == null) return;
        gestureHandler.dragged(e);
        e.consume();
    }

    public void onRelease(MouseEvent e) {
        // only the button that started the gesture ends it.
        if (gestureHandler == null || e.getButton() != gestureButton) return;
        ToolHandler handler = gestureHandler;
        gestureHandler = null;
        handler.released(e);
        host.restoreViewportCursor();
        e.consume();
    }

    public void onMoved(MouseEvent e) {
        if (!spaceDown) activeHandler.moved(e);
    }

    // ---- keys

    public void onKey(KeyEvent e) {
        // typing in a popup note (or any text input inside the view) is not a view command.
        if (e.getTarget() instanceof TextInputControl) return;
        PdfView control = host.view();
        if (host.fields().actuateFocused(e.getCode())) {
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.SPACE) {
            // hold Space to pan with the primary button; a tap without a drag pages down on release.
            if (!spaceDown) {
                spaceDown = true;
                spacePanned = false;
                if (gestureHandler == null) host.restoreViewportCursor();
            }
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.TAB && host.fields().editable()
                && control.getToolMode() != null && !control.getToolMode().createsAnnotations()
                && !host.fields().fieldsInTabOrder().isEmpty()) {
            if (e.isShiftDown()) control.focusPreviousField();
            else control.focusNextField();
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.ESCAPE && control.getFocusedField() != null) {
            control.clearFieldFocus();
            e.consume();
            return;
        }
        if (e.isShortcutDown() && (e.getCode() == KeyCode.Z || e.getCode() == KeyCode.Y)) {
            // undo/redo annotation edits, in any tool
            if (e.getCode() == KeyCode.Y || e.isShiftDown()) control.redo();
            else control.undo();
            e.consume();
            return;
        }
        if (activeHandler.keyPressed(e)) {
            e.consume();
            return;
        }
        boolean continuous = control.getViewMode().isContinuous();
        double x = host.scrollX();
        double y = host.scrollY();
        switch (e.getCode()) {
            case UP -> host.scrollTo(x, y - SCROLL_UNIT);
            case DOWN -> host.scrollTo(x, y + SCROLL_UNIT);
            case LEFT -> host.scrollTo(x - SCROLL_UNIT, y);
            case RIGHT -> host.scrollTo(x + SCROLL_UNIT, y);
            case PAGE_UP -> {
                if (continuous) host.scrollTo(x, y - host.getViewportHeight() * 0.9);
                else control.previousPage();
            }
            case PAGE_DOWN -> pageDown();
            case HOME -> {
                if (continuous) host.scrollTo(x, 0);
                else control.setCurrentPageIndex(0);
            }
            case END -> {
                if (continuous) host.scrollTo(x, Double.MAX_VALUE);
                else control.setCurrentPageIndex(control.getPageCount() - 1);
            }
            case PLUS, EQUALS, ADD -> {
                if (!e.isShortcutDown()) return;
                control.zoomIn();
            }
            case MINUS, SUBTRACT -> {
                if (!e.isShortcutDown()) return;
                control.zoomOut();
            }
            case DIGIT0, NUMPAD0 -> {
                if (!e.isShortcutDown()) return;
                control.setFitMode(FitMode.NONE);
                control.setZoom(1);
            }
            default -> {
                return;
            }
        }
        e.consume();
    }

    public void onKeyReleased(KeyEvent e) {
        if (e.getCode() != KeyCode.SPACE || !spaceDown) return;
        spaceDown = false;
        if (!spacePanned) pageDown();
        if (gestureHandler == null) host.restoreViewportCursor();
        e.consume();
    }

    private void pageDown() {
        if (host.view().getViewMode().isContinuous()) {
            host.scrollTo(host.scrollX(), host.scrollY() + host.getViewportHeight() * 0.9);
        } else {
            host.view().nextPage();
        }
    }
}
