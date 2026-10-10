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

import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.icepdf.fx.ri.RailArrangement;
import org.icepdf.fx.ri.UserLayout;
import org.icepdf.fx.ri.ViewerFeatures;
import org.icepdf.fx.ri.actions.ActionControls;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The tool rail: the user's pinned tools (or the product's default) as icon buttons, then a "more"
 * menu with the other tools the product allows, and a settings menu for arranging it.  Rebuilt by
 * {@link #refresh()} when the arrangement changes.
 */
public final class ToolRail extends VBox {

    private final ActionControls actions;
    private final ViewerFeatures features;
    private final UserLayout layout;
    private Consumer<UserLayout> onLayoutChanged = l -> { };

    public ToolRail(ActionControls actions, ViewerFeatures features, UserLayout layout) {
        this.actions = actions;
        this.features = features;
        this.layout = layout;
        getStyleClass().add("tool-rail");
        refresh();
    }

    /** Called after the user changes the rail from its settings menu (save the layout there). */
    public void setOnLayoutChanged(Consumer<UserLayout> onLayoutChanged) {
        this.onLayoutChanged = onLayoutChanged;
    }

    /** The arrangement shown now. */
    public RailArrangement arrangement() {
        List<String> available = new ArrayList<>();
        actions.registry().all().forEach(a -> {
            if (actions.isAvailable(a.id())) available.add(a.id());
        });
        return RailArrangement.of(features, layout, available);
    }

    /** Builds the rail again from the features and the layout. */
    public void refresh() {
        getChildren().clear();
        RailArrangement arrangement = arrangement();
        String lastGroup = null;
        for (String id : arrangement.rail()) {
            String group = id.substring(0, id.indexOf('.'));
            if (lastGroup != null && !group.equals(lastGroup)) getChildren().add(divider());
            lastGroup = group;
            getChildren().add(actions.button(id));
        }
        if (!arrangement.overflow().isEmpty()) {
            MenuButton more = menuButton("ui.more", "More tools");
            more.getItems().setAll(actions.items(arrangement.overflow().toArray(new String[0])));
            getChildren().add(more);
        }
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        getChildren().add(spacer);
        if (features.isUserCustomisable()) getChildren().add(settings(arrangement));
    }

    private MenuButton settings(RailArrangement arrangement) {
        MenuButton settings = menuButton("ui.settings", "Arrange the tools");
        Menu pin = new Menu("Tools on the Rail");
        List<String> all = new ArrayList<>(arrangement.rail());
        all.addAll(arrangement.overflow());
        for (String id : all) {
            CheckMenuItem item = new CheckMenuItem(actions.registry().get(id).label());
            item.setSelected(arrangement.rail().contains(id));
            item.setOnAction(e -> {
                List<String> pinned = new ArrayList<>(arrangement().rail());
                if (item.isSelected()) {
                    if (!pinned.contains(id)) pinned.add(id);
                } else {
                    pinned.remove(id);
                }
                layout.setPinned(pinned);
                changed();
            });
            pin.getItems().add(item);
        }
        MenuItem side = new MenuItem(layout.getRailSide() == UserLayout.Side.LEFT ? "Move Rail to the Right" : "Move Rail to the Left");
        side.setOnAction(e -> {
            layout.setRailSide(layout.getRailSide() == UserLayout.Side.LEFT ? UserLayout.Side.RIGHT : UserLayout.Side.LEFT);
            changed();
        });
        CheckMenuItem autoShow = new CheckMenuItem("Show Rail Only When Pointed At");
        autoShow.setSelected(layout.isRailAutoShow());
        autoShow.setOnAction(e -> {
            layout.setRailAutoShow(autoShow.isSelected());
            changed();
        });
        MenuItem reset = new MenuItem("Reset Tools");
        reset.setOnAction(e -> {
            layout.resetRail();
            changed();
        });
        settings.getItems().addAll(pin, new SeparatorMenuItem(), side, autoShow, new SeparatorMenuItem(), reset);
        return settings;
    }

    private void changed() {
        refresh();
        onLayoutChanged.accept(layout);
    }

    private MenuButton menuButton(String iconId, String tooltip) {
        MenuButton button = new MenuButton();
        javafx.scene.Node icon = actions.icon(iconId, 20);
        if (icon != null) button.setGraphic(icon);
        else button.setText("…");
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setPopupSide(Side.RIGHT);
        button.setId(iconId);
        return button;
    }

    private static Separator divider() {
        Separator separator = new Separator(Orientation.HORIZONTAL);
        separator.getStyleClass().add("rail-divider");
        return separator;
    }
}
