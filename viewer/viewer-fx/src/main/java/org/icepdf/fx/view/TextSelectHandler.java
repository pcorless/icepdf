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
package org.icepdf.fx.view;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

/**
 * Text selection tool.  Converts mouse events to page points and delegates the selection logic to
 * {@link SelectionController}; adds an I-beam cursor over text and auto-scroll while a drag holds
 * the pointer near or past the viewport edge.
 */
final class TextSelectHandler implements ToolHandler {

    // auto-scroll starts this close to the edge, in logical px, and speeds up with distance past it.
    private static final double EDGE = 24;
    private static final double MAX_STEP = 60;

    private final PdfViewSkin skin;
    private final SelectionController controller;
    private final Timeline autoScroll;
    private double lastX;
    private double lastY;

    TextSelectHandler(PdfViewSkin skin) {
        this.skin = skin;
        this.controller = new SelectionController(skin::textSequence);
        autoScroll = new Timeline(new KeyFrame(Duration.millis(16), e -> autoScrollTick()));
        autoScroll.setCycleCount(Animation.INDEFINITE);
    }

    @Override
    public void uninstall() {
        autoScroll.stop();
    }

    @Override
    public void moved(MouseEvent e) {
        skin.setViewportCursor(skin.isOverText(e.getX(), e.getY()) ? Cursor.TEXT : Cursor.DEFAULT);
    }

    @Override
    public void pressed(MouseEvent e) {
        lastX = e.getX();
        lastY = e.getY();
        PdfView view = skin.getSkinnable();
        view.setTextSelection(controller.press(view.getTextSelection(), skin.pageAtViewport(e.getX(), e.getY()),
                e.getClickCount(), e.isShiftDown()));
    }

    @Override
    public void dragged(MouseEvent e) {
        lastX = e.getX();
        lastY = e.getY();
        extendToLast();
        if (edgeStep(lastX, skin.getViewportWidth()) != 0 || edgeStep(lastY, skin.getViewportHeight()) != 0) {
            if (autoScroll.getStatus() != Animation.Status.RUNNING) autoScroll.play();
        } else {
            autoScroll.stop();
        }
    }

    @Override
    public void released(MouseEvent e) {
        autoScroll.stop();
        controller.release();
    }

    @Override
    public Cursor idleCursor() {
        return Cursor.DEFAULT;
    }

    private void extendToLast() {
        PdfView view = skin.getSkinnable();
        // between pages, or past the viewport edge, the nearest page edge stands in for the pointer.
        view.setTextSelection(controller.drag(view.getTextSelection(), skin.nearestPageAtViewport(lastX, lastY)));
    }

    private void autoScrollTick() {
        double dx = edgeStep(lastX, skin.getViewportWidth());
        double dy = edgeStep(lastY, skin.getViewportHeight());
        if (dx == 0 && dy == 0) {
            autoScroll.stop();
            return;
        }
        skin.scrollBy(dx, dy);
        // the document moved under a still pointer: re-resolve the drag point.
        extendToLast();
    }

    /** Scroll step for one axis: 0 inside the edge band, growing with distance into or past it. */
    static double edgeStep(double position, double size) {
        double step;
        if (position < EDGE) step = position - EDGE;
        else if (position > size - EDGE) step = position - (size - EDGE);
        else return 0;
        return Math.max(-MAX_STEP, Math.min(MAX_STEP, step / 2));
    }
}
