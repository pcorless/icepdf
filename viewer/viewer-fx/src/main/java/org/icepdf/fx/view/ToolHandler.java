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
 * The behaviour behind one {@link ToolMode}.  The skin routes viewport mouse events (coordinates in
 * viewport space) to the active handler for the whole of a gesture, and offers it key presses
 * before its own scrolling keys.
 * <p>
 * Deliberately thin: handlers translate events and delegate, so their logic can live in
 * toolkit-free classes that are unit-testable without a display.
 */
interface ToolHandler {

    /** Becomes the active tool. */
    default void install() {
    }

    /** Stops being the active tool; drop any gesture in progress. */
    default void uninstall() {
    }

    void pressed(MouseEvent e);

    void dragged(MouseEvent e);

    void released(MouseEvent e);

    /** Pointer moved with no button down; typically just updates the cursor. */
    default void moved(MouseEvent e) {
    }

    /**
     * @return true if the key was handled and the skin should not process it
     */
    default boolean keyPressed(KeyEvent e) {
        return false;
    }

    /** Cursor to show while idle over the viewport. */
    Cursor idleCursor();
}
