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

import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Makes ready-wired controls for a registry's actions in one viewer: menu items and buttons with the
 * action's text, tool tip and shortcut, enabled and (for toggles) selected as the action says, and
 * running it when used.  Applications with their own menus and tool bars use this to put ICEpdf's
 * commands in them: {@code controls.menuItem("document.print")}.
 * <p>
 * Ids the registry doesn't have, or whose action isn't {@linkplain ViewerAction#isAvailable
 * available} in this context, are skipped by the list methods ({@link #menu}, {@link #items});
 * a {@code null} id in a list is a separator.
 */
public final class ActionControls {

    private final ActionRegistry registry;
    private final ViewerContext context;
    // one toggle group per action group, shared by every radio item made here.
    private final Map<String, ToggleGroup> groups = new HashMap<>();

    public ActionControls(ActionRegistry registry, ViewerContext context) {
        this.registry = registry;
        this.context = context;
    }

    public ActionRegistry registry() {
        return registry;
    }

    public ViewerContext context() {
        return context;
    }

    /** Whether the id names an action that can run in this context. */
    public boolean isAvailable(String id) {
        return id != null && registry.find(id).map(a -> a.isAvailable(context)).orElse(false);
    }

    /** Runs an action now, if it's available and enabled. */
    public void execute(String id) {
        if (!isAvailable(id)) return;
        ViewerAction action = registry.get(id);
        if (action.enabled(context).get()) action.execute(context);
    }

    /** A menu item for an action: plain, check or radio, as the action is. */
    public MenuItem menuItem(String id) {
        ViewerAction action = registry.get(id);
        MenuItem item;
        if (action instanceof ToggleAction toggle) {
            if (toggle.group() != null) {
                RadioMenuItem radio = new RadioMenuItem(action.label());
                radio.setToggleGroup(group(toggle.group()));
                item = radio;
            } else {
                item = new CheckMenuItem(action.label());
            }
            followSelected(toggle, item);
        } else {
            item = new MenuItem(action.label());
        }
        item.setId(id);
        item.setMnemonicParsing(false);
        if (action.accelerator() != null) item.setAccelerator(action.accelerator());
        item.disableProperty().bind(not(action.enabled(context)));
        item.setOnAction(e -> run(action, item));
        return item;
    }

    /** A button for an action: a toggle button for a toggle action.  Text is the short label. */
    public ButtonBase button(String id) {
        ViewerAction action = registry.get(id);
        ButtonBase button;
        if (action instanceof ToggleAction toggle) {
            ToggleButton toggleButton = new ToggleButton(action.shortLabel());
            if (toggle.group() != null) toggleButton.setToggleGroup(group(toggle.group()));
            ObservableBooleanValue selected = toggle.selected(context);
            toggleButton.setSelected(selected.get());
            selected.addListener((o, was, now) -> toggleButton.setSelected(now));
            toggleButton.setOnAction(e -> {
                run(action, null);
                toggleButton.setSelected(selected.get());
            });
            button = toggleButton;
        } else {
            Button plain = new Button(action.shortLabel());
            plain.setOnAction(e -> run(action, null));
            button = plain;
        }
        button.setId(id);
        button.setMnemonicParsing(false);
        button.setTooltip(new Tooltip(action.tooltip()));
        button.disableProperty().bind(not(action.enabled(context)));
        return button;
    }

    /** Menu items for a list of ids; null is a separator; unavailable ids are skipped. */
    public List<MenuItem> items(String... ids) {
        List<MenuItem> items = new java.util.ArrayList<>();
        for (String id : ids) {
            if (id == null) {
                if (!items.isEmpty() && !(items.get(items.size() - 1) instanceof SeparatorMenuItem)) {
                    items.add(new SeparatorMenuItem());
                }
            } else if (isAvailable(id)) {
                items.add(menuItem(id));
            }
        }
        while (!items.isEmpty() && items.get(items.size() - 1) instanceof SeparatorMenuItem) {
            items.remove(items.size() - 1);
        }
        return items;
    }

    /** A menu of actions; see {@link #items}. */
    public Menu menu(String text, String... ids) {
        Menu menu = new Menu(text);
        menu.setMnemonicParsing(false);
        menu.getItems().setAll(items(ids));
        return menu;
    }

    /**
     * Puts the shortcuts of these actions on a scene, for a viewer embedded without a menu bar
     * (a menu bar's items carry their own; don't install those twice).
     */
    public void installAccelerators(Scene scene, String... ids) {
        for (String id : ids) {
            if (!isAvailable(id)) continue;
            ViewerAction action = registry.get(id);
            KeyCombination key = action.accelerator();
            if (key == null) continue;
            ObservableBooleanValue enabled = action.enabled(context);
            scene.getAccelerators().put(key, () -> {
                if (enabled.get()) action.execute(context);
            });
        }
    }

    private void run(ViewerAction action, MenuItem item) {
        action.execute(context);
        // a toggle the action refused (or changed differently) shows its real state again.
        if (action instanceof ToggleAction toggle && item != null) {
            boolean on = toggle.selected(context).get();
            if (item instanceof CheckMenuItem check) check.setSelected(on);
            else if (item instanceof RadioMenuItem radio) radio.setSelected(on);
        }
    }

    private void followSelected(ToggleAction toggle, MenuItem item) {
        ObservableBooleanValue selected = toggle.selected(context);
        Runnable show = () -> {
            if (item instanceof CheckMenuItem check) check.setSelected(selected.get());
            else if (item instanceof RadioMenuItem radio) radio.setSelected(selected.get());
        };
        show.run();
        selected.addListener((o, was, now) -> show.run());
        // keep the binding reachable as long as the item is.
        item.getProperties().put(ToggleAction.class, selected);
    }

    private ToggleGroup group(String name) {
        return groups.computeIfAbsent(name, n -> new ToggleGroup());
    }

    private static javafx.beans.binding.BooleanBinding not(ObservableBooleanValue value) {
        return javafx.beans.binding.Bindings.createBooleanBinding(() -> !value.get(), value);
    }
}
