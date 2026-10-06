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

import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;

/**
 * Hand tool: dragging moves the document with the pointer.  Also used by the skin for the
 * middle-button and Space+drag pans available in every tool.
 */
final class PanHandler implements ToolHandler {

    private final PdfViewSkin skin;
    private double lastX;
    private double lastY;

    private double pressX;
    private double pressY;

    PanHandler(PdfViewSkin skin) {
        this.skin = skin;
    }

    @Override
    public void pressed(MouseEvent e) {
        lastX = e.getX();
        lastY = e.getY();
        pressX = e.getX();
        pressY = e.getY();
        skin.setViewportCursor(Cursor.CLOSED_HAND);
    }

    @Override
    public void dragged(MouseEvent e) {
        skin.scrollBy(lastX - e.getX(), lastY - e.getY());
        lastX = e.getX();
        lastY = e.getY();
    }

    @Override
    public void released(MouseEvent e) {
        skin.restoreViewportCursor();
        // a click (not a drag) with the hand tool follows a link, as in Acrobat.
        if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY
                && Math.hypot(e.getX() - pressX, e.getY() - pressY) <= 4) {
            // a click with the hand tool fills a form field or follows a link, as in Acrobat.
            PdfViewSkin.AnnotationHit field = skin.fieldAtViewport(e.getX(), e.getY());
            if (field != null) skin.pressField(field, e);
            else skin.activateLinkAtViewport(e.getX(), e.getY());
        }
    }

    @Override
    public Cursor idleCursor() {
        return Cursor.OPEN_HAND;
    }
}
