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
package org.icepdf.fx.testing;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.icepdf.fx.ri.ui.ViewerDialogs;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Answers the viewer's choosers and questions from a script, never showing one, and keeps what
 * was asked.  Anything asked with no answer queued is cancelled (and still recorded), so a test
 * can't hang on an unexpected dialog.
 */
public final class ScriptedDialogs implements ViewerDialogs {

    private final Deque<Path> files = new ArrayDeque<>();
    private final Deque<ButtonBar.ButtonData> answers = new ArrayDeque<>();
    /** The chooser titles and question texts, in order. */
    public final List<String> asked = new ArrayList<>();
    /** The informing dialogs shown (a redaction report). */
    public final List<Dialog<?>> shown = new ArrayList<>();
    /** The initial file name each save chooser offered. */
    public final List<String> suggested = new ArrayList<>();

    /** The next chooser picks {@code file}. */
    public ScriptedDialogs choose(Path file) {
        files.add(file);
        return this;
    }

    /** The next question is answered with its button of this kind ({@code YES} replace/open/save, {@code NO} don't). */
    public ScriptedDialogs answer(ButtonBar.ButtonData button) {
        answers.add(button);
        return this;
    }

    @Override
    public File showOpenDialog(FileChooser chooser, Window owner) {
        asked.add(chooser.getTitle());
        Path file = files.poll();
        return file != null ? file.toFile() : null;
    }

    @Override
    public File showSaveDialog(FileChooser chooser, Window owner) {
        asked.add(chooser.getTitle());
        suggested.add(chooser.getInitialFileName());
        Path file = files.poll();
        return file != null ? file.toFile() : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> Optional<R> showAndWait(Dialog<R> dialog) {
        asked.add(dialog.getContentText());
        ButtonBar.ButtonData wanted = answers.poll();
        if (wanted == null || !(dialog instanceof Alert)) return Optional.empty();
        for (ButtonType button : dialog.getDialogPane().getButtonTypes()) {
            if (button.getButtonData() == wanted) return Optional.of((R) button);
        }
        throw new AssertionError("no " + wanted + " button on \"" + dialog.getContentText() + "\"");
    }

    @Override
    public void show(Dialog<?> dialog) {
        shown.add(dialog);
    }
}
