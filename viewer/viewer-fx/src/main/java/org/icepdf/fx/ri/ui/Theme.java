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

import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.Parent;

/**
 * The viewer's look: light, dark or high contrast, or following the system's light/dark setting
 * ({@link Mode#SYSTEM}, the default; JavaFX 22's {@code Platform.Preferences}).  The style sheet
 * ({@code viewer.css}) keys everything off one style class on the viewer's root - {@code theme-light},
 * {@code theme-dark} or {@code theme-high-contrast} - and a product can add its own sheet on top.
 */
public final class Theme {

    public enum Mode { SYSTEM, LIGHT, DARK, HIGH_CONTRAST }

    private static final String SHEET = Theme.class.getResource("/org/icepdf/fx/ri/ui/viewer.css").toExternalForm();

    private final Parent root;
    private final ObjectProperty<Mode> mode = new SimpleObjectProperty<>(this, "mode", Mode.SYSTEM);
    private final ReadOnlyObjectWrapper<Mode> effective = new ReadOnlyObjectWrapper<>(this, "effective", Mode.LIGHT);
    private final ChangeListener<ColorScheme> systemListener = (o, was, now) -> apply();

    public Theme(Parent root) {
        this.root = root;
        root.getStylesheets().add(SHEET);
        root.getStyleClass().add("icepdf-viewer");
        mode.addListener((o, was, now) -> apply());
        Platform.getPreferences().colorSchemeProperty().addListener(systemListener);
        apply();
    }

    /** The chosen mode; {@link Mode#SYSTEM} follows the desktop. */
    public ObjectProperty<Mode> modeProperty() {
        return mode;
    }

    /** What's showing: never SYSTEM. */
    public ReadOnlyObjectProperty<Mode> effectiveProperty() {
        return effective.getReadOnlyProperty();
    }

    /** Stops following the system setting (call when the viewer is thrown away). */
    public void dispose() {
        Platform.getPreferences().colorSchemeProperty().removeListener(systemListener);
    }

    private void apply() {
        Mode chosen = mode.get();
        Mode shown = chosen != Mode.SYSTEM ? chosen
                : Platform.getPreferences().getColorScheme() == ColorScheme.DARK ? Mode.DARK : Mode.LIGHT;
        root.getStyleClass().removeAll("theme-light", "theme-dark", "theme-high-contrast");
        root.getStyleClass().add(switch (shown) {
            case DARK -> "theme-dark";
            case HIGH_CONTRAST -> "theme-high-contrast";
            default -> "theme-light";
        });
        effective.set(shown);
    }
}
