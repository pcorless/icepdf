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
package org.icepdf.fx.ri.actions;

import javafx.scene.input.KeyCombination;

import org.icepdf.fx.i18n.Messages;

/**
 * Base for the built-in actions: the id, and the label, tool tip and short label from the
 * {@code messages} bundle in this package ({@code <id>}, {@code <id>.tooltip}, {@code <id>.short}).
 */
public abstract class AbstractViewerAction implements ViewerAction {

    private final String id;
    private final KeyCombination accelerator;

    protected AbstractViewerAction(String id) {
        this(id, null);
    }

    /** @param accelerator a {@link KeyCombination#keyCombination} string ("Shortcut+P"), or null */
    protected AbstractViewerAction(String id, String accelerator) {
        this.id = id;
        this.accelerator = accelerator != null ? KeyCombination.keyCombination(accelerator) : null;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public String label() {
        return message(id, id);
    }

    @Override
    public String tooltip() {
        String tip = message(id + ".tooltip", label());
        return accelerator != null ? tip + " (" + accelerator.getDisplayText() + ")" : tip;
    }

    @Override
    public String shortLabel() {
        return message(id + ".short", label());
    }

    @Override
    public KeyCombination accelerator() {
        return accelerator;
    }

    /** A message from the viewer's bundle ({@link Messages}), or {@code fallback}. */
    protected static String message(String key, String fallback) {
        return Messages.get(key, fallback);
    }

    @Override
    public String toString() {
        return id;
    }
}
