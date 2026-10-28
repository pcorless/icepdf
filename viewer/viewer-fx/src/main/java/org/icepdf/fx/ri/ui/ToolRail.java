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

import org.icepdf.fx.i18n.Messages;

import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.beans.value.ObservableValue;
import org.icepdf.fx.ri.RailArrangement;
import org.icepdf.fx.ri.UserLayout;
import org.icepdf.fx.ri.ViewerFeatures;
import org.icepdf.fx.ri.actions.ActionControls;

import java.util.ArrayList;
import java.util.List;

/**
 * The tool rail: the user's pinned tools (or the product's default) as icon buttons, then a "more"
 * menu with the other tools the product allows, and a settings menu for arranging it.  Rebuilt by
 * {@link #refresh()} when the arrangement changes.
 */
public final class ToolRail extends VBox {

    private final ActionControls actions;
    private final ViewerFeatures features;
    private final UserLayout layout;
    private Runnable onCustomise = () -> { };
    private ToolColors toolColors;
    private final Runnable layoutListener = this::refresh;

    public ToolRail(ActionControls actions, ViewerFeatures features, UserLayout layout) {
        this.actions = actions;
        this.features = features;
        this.layout = layout;
        getStyleClass().add("tool-rail");
        layout.addListener(layoutListener);
        refresh();
    }

    /** What the settings menu's "Customise Tools" runs. */
    public void setOnCustomise(Runnable onCustomise) {
        this.onCustomise = onCustomise;
    }

    /** The colours of the drawing tools, as the rail shows and changes them. */
    public interface ToolColors {

        /** The colour of the tool with action {@code id}, or null if it has none. */
        ObservableValue<Color> colour(String id);

        /** The tool's right-click menu: choosing its colour. */
        ContextMenu menu(String id);
    }

    /**
     * Shows the drawing tools' colours: a bar under each one's icon, and a right-click menu to
     * change it.  Null shows none.
     */
    public void setToolColors(ToolColors toolColors) {
        this.toolColors = toolColors;
        refresh();
    }

    /** Stops following the layout (call when the viewer is thrown away). */
    public void dispose() {
        layout.removeListener(layoutListener);
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
            getChildren().add(withColour(actions.button(id), id));
        }
        if (!arrangement.overflow().isEmpty()) {
            MenuButton more = menuButton("ui.more", Messages.get("toolRail.more"));
            more.getItems().setAll(actions.items(arrangement.overflow().toArray(new String[0])));
            getChildren().add(more);
        }
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        getChildren().add(spacer);
        if (features.isUserCustomisable()) getChildren().add(settings(arrangement));
    }

    /** Adds the colour bar and menu to a drawing tool's button. */
    private ButtonBase withColour(ButtonBase button, String id) {
        ObservableValue<Color> colour = toolColors != null ? toolColors.colour(id) : null;
        if (colour == null) return button;
        // a hairline: enough to tell the colour, quiet enough for the rail.
        Rectangle bar = new Rectangle(14, 1);
        bar.getStyleClass().add("tool-colour");
        bar.fillProperty().bind(colour);
        javafx.scene.Node icon = button.getGraphic();
        if (icon != null) {
            VBox graphic = new VBox(2, icon, bar);
            graphic.setAlignment(javafx.geometry.Pos.CENTER);
            button.setGraphic(graphic);
        }
        button.setOnContextMenuRequested(e -> {
            ContextMenu menu = toolColors.menu(id);
            if (menu == null) return;
            menu.show(button, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        return button;
    }

    private MenuButton settings(RailArrangement arrangement) {
        MenuButton settings = menuButton("ui.settings", Messages.get("toolRail.arrange"));
        MenuItem customise = new MenuItem(Messages.get("toolRail.customise"));
        customise.setOnAction(e -> onCustomise.run());
        MenuItem side = new MenuItem(Messages.get(layout.getRailSide() == UserLayout.Side.LEFT ? "toolRail.moveRight" : "toolRail.moveLeft"));
        side.setOnAction(e -> {
            layout.setRailSide(layout.getRailSide() == UserLayout.Side.LEFT ? UserLayout.Side.RIGHT : UserLayout.Side.LEFT);
            changed();
        });
        CheckMenuItem autoShow = new CheckMenuItem(Messages.get("toolRail.autoShow"));
        autoShow.setSelected(layout.isRailAutoShow());
        autoShow.setOnAction(e -> {
            layout.setRailAutoShow(autoShow.isSelected());
            changed();
        });
        MenuItem reset = new MenuItem(Messages.get("toolRail.reset"));
        reset.setOnAction(e -> {
            layout.resetRail();
            changed();
        });
        settings.getItems().addAll(customise, new SeparatorMenuItem(), side, autoShow, new SeparatorMenuItem(), reset);
        return settings;
    }

    // the layout tells every listener (this rail among them) when it changes.
    private void changed() {
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
