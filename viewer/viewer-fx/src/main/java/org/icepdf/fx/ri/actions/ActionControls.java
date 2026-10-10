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
    private final java.util.function.Predicate<String> allowed;
    private org.icepdf.fx.ri.icons.IconProvider icons = org.icepdf.fx.ri.icons.IconProvider.none();
    private double buttonIconSize = 18;
    private double menuIconSize = 16;
    // one toggle group per action group, shared by every radio item made here.

    private final Map<String, ToggleGroup> groups = new HashMap<>();
    // every control made here, so they can follow the context when its view changes.
    private final List<Wiring> wirings = new java.util.ArrayList<>();

    public ActionControls(ActionRegistry registry, ViewerContext context) {
        this(registry, context, id -> true);
    }

    /**
     * @param allowed which ids the product offers (its {@code ViewerFeatures::allows}); the others
     *                are left out like unavailable ones
     */
    public ActionControls(ActionRegistry registry, ViewerContext context, java.util.function.Predicate<String> allowed) {
        this.registry = registry;
        this.context = context;
        this.allowed = allowed;
    }

    /**
     * Icons for the controls made from now on: buttons show only the icon (the label becomes the
     * tool tip), menu items show it beside their text.  None by default (text only).
     */
    public ActionControls setIcons(org.icepdf.fx.ri.icons.IconProvider icons, double buttonSize, double menuSize) {
        this.icons = icons != null ? icons : org.icepdf.fx.ri.icons.IconProvider.none();
        this.buttonIconSize = buttonSize;
        this.menuIconSize = menuSize;
        return this;
    }

    /** An action's icon: its own, else the provider's for its id; null when there's none. */
    public javafx.scene.Node icon(String id, double size) {
        ViewerAction action = registry.find(id).orElse(null);
        javafx.scene.Node own = action != null ? action.icon(size) : null;
        return own != null ? own : icons.icon(id, size);
    }

    public ActionRegistry registry() {
        return registry;
    }

    public ViewerContext context() {
        return context;
    }

    /** Whether the id names an action the product offers and that can run in this context. */
    public boolean isAvailable(String id) {
        return id != null && allowed.test(id) && registry.find(id).map(a -> a.isAvailable(context)).orElse(false);
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
        } else {
            item = new MenuItem(action.label());
        }
        item.setId(id);
        item.setMnemonicParsing(false);
        javafx.scene.Node graphic = icon(id, menuIconSize);
        if (graphic != null) item.setGraphic(graphic);
        if (action.accelerator() != null) item.setAccelerator(action.accelerator());
        item.setOnAction(e -> run(action, item));
        wire(item, action);
        return item;
    }

    /** A button for an action: a toggle button for a toggle action.  Text is the short label. */
    public ButtonBase button(String id) {
        ViewerAction action = registry.get(id);
        ButtonBase button;
        if (action instanceof ToggleAction toggle) {
            ToggleButton toggleButton = new ToggleButton(action.shortLabel());
            if (toggle.group() != null) toggleButton.setToggleGroup(group(toggle.group()));
            toggleButton.setOnAction(e -> {
                run(action, null);
                toggleButton.setSelected(toggle.selected(context).get());
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
        javafx.scene.Node graphic = icon(id, buttonIconSize);
        if (graphic != null) {
            button.setGraphic(graphic);
            button.setText(null);
            button.getStyleClass().add("icon-button");
            button.setAccessibleText(action.label());
        }
        wire(button, action);
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
            scene.getAccelerators().put(key, () -> {
                if (action.enabled(context).get()) action.execute(context);
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

    private void wire(Object control, ViewerAction action) {
        Wiring wiring = new Wiring(control, action);
        wiring.bind();
        wirings.add(wiring);
    }

    /**
     * Points every control made here at the context's view as it is now.  Call it after the
     * context's {@link ViewerContext#view()} starts answering with another view (the selected tab
     * changed): enabled and selected states follow the new one.
     */
    public void refresh() {
        wirings.removeIf(w -> !w.bind());
    }

    /** A control's enabled and selected state, bound to whatever the context's view is now. */
    private final class Wiring {
        private final java.lang.ref.WeakReference<Object> control;
        private final ViewerAction action;
        private ObservableBooleanValue enabled, selected;
        private javafx.beans.value.ChangeListener<Boolean> selectedListener;

        Wiring(Object control, ViewerAction action) {
            this.control = new java.lang.ref.WeakReference<>(control);
            this.action = action;
        }

        /** @return false once the control is gone */
        boolean bind() {
            Object target = control.get();
            if (target == null) return false;
            enabled = action.enabled(context);
            if (target instanceof MenuItem item) {
                item.disableProperty().unbind();
                item.disableProperty().bind(not(enabled));
            } else if (target instanceof ButtonBase button) {
                button.disableProperty().unbind();
                button.disableProperty().bind(not(enabled));
            }
            if (action instanceof ToggleAction toggle) {
                if (selected != null && selectedListener != null) selected.removeListener(selectedListener);
                selected = toggle.selected(context);
                selectedListener = (o, was, now) -> show(target, now);
                selected.addListener(selectedListener);
                show(target, selected.get());
                // the bindings are only weakly listened to: keep them reachable as long as the control is.
                if (target instanceof MenuItem item) item.getProperties().put(ToggleAction.class, this);
                else if (target instanceof ButtonBase button) button.getProperties().put(ToggleAction.class, this);
            }
            return true;
        }

        private void show(Object target, boolean on) {
            if (target instanceof CheckMenuItem check) check.setSelected(on);
            else if (target instanceof RadioMenuItem radio) radio.setSelected(on);
            else if (target instanceof ToggleButton button) button.setSelected(on);
        }
    }

    private ToggleGroup group(String name) {
        return groups.computeIfAbsent(name, n -> new ToggleGroup());
    }

    private static javafx.beans.binding.BooleanBinding not(ObservableBooleanValue value) {
        return javafx.beans.binding.Bindings.createBooleanBinding(() -> !value.get(), value);
    }
}
