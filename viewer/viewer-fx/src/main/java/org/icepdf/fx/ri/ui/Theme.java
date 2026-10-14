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
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.Locale;

/**
 * The viewer's look: light, dark, high contrast (black on white) or high contrast dark (white on
 * black), or following the desktop ({@link Mode#SYSTEM}, the default; JavaFX 22's
 * {@code Platform.Preferences}).  SYSTEM picks light or dark from the colour scheme, and one of the
 * high-contrast looks when the desktop asks for high contrast.
 * <p>
 * The style sheet ({@code viewer.css}) keys everything off one style class on the viewer's root -
 * {@code theme-light}, {@code theme-dark}, {@code theme-high-contrast} or
 * {@code theme-high-contrast-dark}, and both high-contrast looks also carry {@code high-contrast} -
 * and a product can add its own sheet on top.  Dialogs owned by the viewer's window get the same
 * sheet and classes, so the preferences, print and signing dialogs follow the theme.
 */
public final class Theme {

    public enum Mode { SYSTEM, LIGHT, DARK, HIGH_CONTRAST, HIGH_CONTRAST_DARK }

    /** The style class all the high-contrast looks carry. */
    public static final String HIGH_CONTRAST_CLASS = "high-contrast";

    private static final String SHEET = Theme.class.getResource("/org/icepdf/fx/ri/ui/viewer.css").toExternalForm();
    private static final List<String> CLASSES = List.of("theme-light", "theme-dark", "theme-high-contrast",
            "theme-high-contrast-dark", HIGH_CONTRAST_CLASS);
    /** Windows' Settings > Accessibility > Contrast themes (JavaFX 22 preference key). */
    private static final String WINDOWS_HIGH_CONTRAST = "Windows.SPI.HighContrast";

    private final Parent root;
    private final ObjectProperty<Mode> mode = new SimpleObjectProperty<>(this, "mode", Mode.SYSTEM);
    private final ReadOnlyObjectWrapper<Mode> effective = new ReadOnlyObjectWrapper<>(this, "effective", Mode.LIGHT);
    private final MapChangeListener<String, Object> systemListener = change -> apply();
    private final ChangeListener<ColorScheme> schemeListener = (o, was, now) -> apply();
    private final ListChangeListener<Window> windowListener = change -> {
        while (change.next()) {
            for (Window window : change.getAddedSubList()) {
                if (isOwnedDialog(window)) style(window.getScene().getRoot());
            }
        }
    };

    public Theme(Parent root) {
        this.root = root;
        root.getStylesheets().add(SHEET);
        root.getStyleClass().add("icepdf-viewer");
        mode.addListener((o, was, now) -> apply());
        Platform.getPreferences().addListener(systemListener);
        Platform.getPreferences().colorSchemeProperty().addListener(schemeListener);
        Window.getWindows().addListener(windowListener);
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

    /** Whether what's showing is one of the high-contrast looks. */
    public boolean isHighContrast() {
        return isHighContrast(effective.get());
    }

    public static boolean isHighContrast(Mode mode) {
        return mode == Mode.HIGH_CONTRAST || mode == Mode.HIGH_CONTRAST_DARK;
    }

    /**
     * Gives {@code node} (a dialog's pane, say) the viewer's sheet and current theme classes.
     * Dialogs owned by the viewer's window get this without asking; use it for a window that isn't.
     */
    public void style(Parent node) {
        if (!node.getStylesheets().contains(SHEET)) node.getStylesheets().add(SHEET);
        if (!node.getStyleClass().contains("icepdf-viewer")) node.getStyleClass().add("icepdf-viewer");
        node.getStyleClass().removeAll(CLASSES);
        node.getStyleClass().addAll(classes(effective.get()));
    }

    /** Stops following the system setting (call when the viewer is thrown away). */
    public void dispose() {
        Platform.getPreferences().removeListener(systemListener);
        Platform.getPreferences().colorSchemeProperty().removeListener(schemeListener);
        Window.getWindows().removeListener(windowListener);
    }

    /** Whether the desktop asks for high contrast: Windows' contrast themes, or a GTK HighContrast theme. */
    public static boolean isSystemHighContrast() {
        Object windows = Platform.getPreferences().get(WINDOWS_HIGH_CONTRAST);
        if (windows instanceof Boolean on) return on;
        String gtk = System.getenv("GTK_THEME");
        return gtk != null && gtk.toLowerCase(Locale.ROOT).contains("highcontrast");
    }

    private void apply() {
        Mode chosen = mode.get();
        boolean dark = Platform.getPreferences().getColorScheme() == ColorScheme.DARK;
        Mode shown = chosen != Mode.SYSTEM ? chosen
                : isSystemHighContrast() ? (dark ? Mode.HIGH_CONTRAST_DARK : Mode.HIGH_CONTRAST)
                : dark ? Mode.DARK : Mode.LIGHT;
        effective.set(shown);
        style(root);
        for (Window window : Window.getWindows()) {
            if (isOwnedDialog(window)) style(window.getScene().getRoot());
        }
    }

    private static List<String> classes(Mode shown) {
        return switch (shown) {
            case DARK -> List.of("theme-dark");
            case HIGH_CONTRAST -> List.of("theme-high-contrast", HIGH_CONTRAST_CLASS);
            case HIGH_CONTRAST_DARK -> List.of("theme-high-contrast-dark", HIGH_CONTRAST_CLASS);
            default -> List.of("theme-light");
        };
    }

    /** A stage (not a popup: those take their owner node's styles) that the viewer's window owns. */
    private boolean isOwnedDialog(Window window) {
        if (!(window instanceof Stage stage) || window instanceof PopupWindow) return false;
        Scene ours = root.getScene();
        if (ours == null || ours.getWindow() == null || window.getScene() == null || window == ours.getWindow()) {
            return false;
        }
        for (Window owner = stage.getOwner(); owner != null;
             owner = owner instanceof Stage s ? s.getOwner() : null) {
            if (owner == ours.getWindow()) return true;
        }
        return false;
    }
}
