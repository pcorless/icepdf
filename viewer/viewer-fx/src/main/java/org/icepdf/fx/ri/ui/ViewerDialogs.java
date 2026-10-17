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
package org.icepdf.fx.ri.ui;

import javafx.scene.control.Dialog;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.Optional;

/**
 * Where the viewer's file choosers and questions (replace a file, unsaved redactions, follow a
 * link, save changes) are shown.  The viewer builds each chooser or alert and hands it here;
 * {@link #SYSTEM} shows it as usual.  A product can put its own in place - a file picker of its
 * own, say - and a test answers them without a window.
 * <pre>{@code
 * viewer.setDialogs(new ViewerDialogs() {
 *     public File showSaveDialog(FileChooser chooser, Window owner) {
 *         return myPicker.save(chooser.getInitialFileName());
 *     }
 * });
 * }</pre>
 * FX thread only.
 */
public interface ViewerDialogs {

    /** Shows each chooser and dialog itself. */
    ViewerDialogs SYSTEM = new ViewerDialogs() {
    };

    /** @return the file to open, or null to cancel */
    default File showOpenDialog(FileChooser chooser, Window owner) {
        return chooser.showOpenDialog(owner);
    }

    /** @return the file to write, or null to cancel */
    default File showSaveDialog(FileChooser chooser, Window owner) {
        return chooser.showSaveDialog(owner);
    }

    /** A question: its answer, or empty when it was closed without one. */
    default <R> Optional<R> showAndWait(Dialog<R> dialog) {
        return dialog.showAndWait();
    }

    /** A dialog that just informs (a redaction report): shown without waiting. */
    default void show(Dialog<?> dialog) {
        dialog.show();
    }
}
