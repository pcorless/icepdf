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
 * Text selection tool: an I-beam over text.  Selection gestures arrive with the selection controller
 * (JAVAFX-SELECTION-PLAN.md step 4); until then drags do nothing, so pan via the middle button or
 * Space+drag.
 */
final class TextSelectHandler implements ToolHandler {

    private final PdfViewSkin skin;

    TextSelectHandler(PdfViewSkin skin) {
        this.skin = skin;
    }

    @Override
    public void moved(MouseEvent e) {
        skin.setViewportCursor(skin.isOverText(e.getX(), e.getY()) ? Cursor.TEXT : Cursor.DEFAULT);
    }

    @Override
    public void pressed(MouseEvent e) {
    }

    @Override
    public void dragged(MouseEvent e) {
    }

    @Override
    public void released(MouseEvent e) {
    }

    @Override
    public Cursor idleCursor() {
        return Cursor.DEFAULT;
    }
}
