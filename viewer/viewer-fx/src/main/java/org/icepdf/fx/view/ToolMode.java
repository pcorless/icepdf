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

/**
 * What a primary-button drag on a page does in a {@link PdfView}.  Whatever the mode, a middle-button
 * drag or holding Space while dragging pans, and the wheel / pinch zoom and scroll.
 */
public enum ToolMode {
    /** Drag selects text; the cursor is an I-beam over text.  Annotations can be selected and edited. */
    TEXT_SELECT,
    /** Drag pans the view (hand tool). */
    PAN,
    /** Select text as usual; on release the selection becomes a highlight. */
    HIGHLIGHT,
    /** Select text; on release it is underlined. */
    UNDERLINE,
    /** Select text; on release it is struck out. */
    STRIKE_OUT,
    /** Click to place a sticky note; its popup opens for typing. */
    NOTE,
    /** Click to place a free text box and type into it. */
    FREE_TEXT,
    /** Drag to draw freehand ink. */
    INK,
    /** Drag to draw a rectangle. */
    RECTANGLE,
    /** Drag to draw an ellipse. */
    ELLIPSE,
    /** Drag to draw a straight line. */
    LINE;

    /** The tool selects text (plain selection or a text-markup tool). */
    public boolean selectsText() {
        return this == TEXT_SELECT || this == HIGHLIGHT || this == UNDERLINE || this == STRIKE_OUT;
    }

    /** The tool creates annotations. */
    public boolean createsAnnotations() {
        return this != TEXT_SELECT && this != PAN;
    }
}
