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

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.input.KeyCombination;

import java.util.function.Consumer;

/**
 * A viewer command: what a menu item, a tool bar or tool rail button, and a keyboard shortcut all
 * run.  The viewer's menus, bars and rail are built from lists of action ids, so a product picks
 * what its users get by picking ids, and adds its own commands by registering actions of its own
 * ({@link ActionRegistry#register}).
 * <p>
 * Actions are stateless: everything they work on comes from the {@link ViewerContext} they're given,
 * so one registry serves any number of viewers.  Run on the FX thread.
 */
public interface ViewerAction {

    /** Unique, stable id: area dot name, lower case ("document.print", "view.zoom-in"). */
    String id();

    /** Menu text. */
    String label();

    /** Tool tip; the label by default. */
    default String tooltip() {
        return label();
    }

    /** Short text for a compact button when there's no icon ("+", "Hand"); the label by default. */
    default String shortLabel() {
        return label();
    }

    /** Keyboard shortcut, or null. */
    default KeyCombination accelerator() {
        return null;
    }

    /**
     * Whether the context can run this action at all - a host without windows can't open a new
     * one.  Unavailable actions are left out of menus and bars.
     */
    default boolean isAvailable(ViewerContext context) {
        return true;
    }

    /** When the action can run right now (a document is open, the selection isn't empty...). */
    default ObservableBooleanValue enabled(ViewerContext context) {
        return Bindings.createBooleanBinding(() -> true);
    }

    /** Does the work. */
    void execute(ViewerContext context);

    /**
     * A simple action for an application's own command - "Upload to DMS" - enabled while a
     * document is open.
     */
    static ViewerAction of(String id, String label, Consumer<ViewerContext> run) {
        return new ViewerAction() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String label() {
                return label;
            }

            @Override
            public ObservableBooleanValue enabled(ViewerContext context) {
                return Conditions.documentOpen(context);
            }

            @Override
            public void execute(ViewerContext context) {
                run.accept(context);
            }
        };
    }
}
