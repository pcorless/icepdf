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
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

/**
 * The redaction tool ({@link ToolMode#REDACT}): a gesture that starts over text selects text, which
 * becomes a redaction on release (the text-select handler, with the Redact subtype); one that starts
 * anywhere else drags out a box (the create handler, in REDACT mode) - for images and drawings, as
 * Swing's RedactionAnnotationHandler does.  The choice is made at the press and kept for the gesture.
 */
final class RedactHandler implements ToolHandler {

    private final PdfViewSkin skin;
    private final TextSelectHandler text;
    private final AnnotationCreateHandler box;
    private ToolHandler gesture;

    RedactHandler(PdfViewSkin skin, TextSelectHandler text, AnnotationCreateHandler box) {
        this.skin = skin;
        this.text = text;
        this.box = box;
    }

    @Override
    public void uninstall() {
        box.uninstall();
        gesture = null;
    }

    @Override
    public void pressed(MouseEvent e) {
        // an annotation under the pointer (to select or move) goes to the text handler, as text does.
        boolean overText = skin.isOverText(e.getX(), e.getY())
                || skin.annotationAtViewport(e.getX(), e.getY()) != null;
        gesture = overText ? text : box;
        gesture.pressed(e);
    }

    @Override
    public void dragged(MouseEvent e) {
        if (gesture != null) gesture.dragged(e);
    }

    @Override
    public void released(MouseEvent e) {
        if (gesture != null) gesture.released(e);
        gesture = null;
    }

    @Override
    public void moved(MouseEvent e) {
        text.moved(e);
        // the I-beam over text; a crosshair elsewhere, where a drag draws a box.
        if (!skin.isOverText(e.getX(), e.getY()) && skin.annotationAtViewport(e.getX(), e.getY()) == null) {
            skin.setViewportCursor(Cursor.CROSSHAIR);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        return text.keyPressed(e);
    }

    @Override
    public Cursor idleCursor() {
        return Cursor.CROSSHAIR;
    }
}
