package org.icepdf.fx.ri.ui.themes;

import javafx.scene.Scene;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Manages application themes (light, dark, high contrast).
 * Handles dynamic theme switching and persistence of user preferences.
 */
public class ThemeManager {

    private static final Logger logger = Logger.getLogger(ThemeManager.class.getName());

    // Singleton instance
    private static ThemeManager instance;

    // Theme types
    public enum Theme {
        LIGHT("Light", "/org/icepdf/fx/ri/styles/light-theme.css"),
        DARK("Dark", "/org/icepdf/fx/ri/styles/dark-theme.css"),
        HIGH_CONTRAST("High Contrast", "/org/icepdf/fx/ri/styles/high-contrast-theme.css");

        private final String displayName;
        private final String cssPath;

        Theme(String displayName, String cssPath) {
            this.displayName = displayName;
            this.cssPath = cssPath;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getCssPath() {
            return cssPath;
        }
    }

    // Preferences
    private static final String PREFS_NODE = "org.icepdf.fx.ri.viewer";
    private static final String PREFS_THEME_KEY = "theme";
    private final Preferences prefs;

    // Current theme
    private Theme currentTheme;
    private Scene activeScene;

    private ThemeManager() {
        this.prefs = Preferences.userRoot().node(PREFS_NODE);
        this.currentTheme = loadThemePreference();
    }

    /**
     * Gets the singleton instance.
     */
    public static synchronized ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    /**
     * Loads the theme preference from storage.
     */
    private Theme loadThemePreference() {
        String themeName = prefs.get(PREFS_THEME_KEY, Theme.LIGHT.name());
        try {
            return Theme.valueOf(themeName);
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Invalid theme preference: " + themeName + ", defaulting to LIGHT");
            return Theme.LIGHT;
        }
    }

    /**
     * Saves the theme preference to storage.
     */
    private void saveThemePreference(Theme theme) {
        prefs.put(PREFS_THEME_KEY, theme.name());
        logger.info("Saved theme preference: " + theme.name());
    }

    /**
     * Gets the current theme.
     */
    public Theme getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Sets the active scene to apply themes to.
     */
    public void setActiveScene(Scene scene) {
        this.activeScene = scene;
        applyTheme(currentTheme);
    }

    /**
     * Applies a theme to the active scene.
     */
    public void applyTheme(Theme theme) {
        if (activeScene == null) {
            logger.warning("No active scene set, cannot apply theme");
            return;
        }

        // Clear existing stylesheets
        activeScene.getStylesheets().clear();

        // Try to load the theme CSS
        try {
            String cssPath = theme.getCssPath();
            String cssUrl = getClass().getResource(cssPath).toExternalForm();
            activeScene.getStylesheets().add(cssUrl);

            currentTheme = theme;
            saveThemePreference(theme);

            logger.info("Applied theme: " + theme.getDisplayName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load theme: " + theme.getDisplayName(), e);

            // Fallback to light theme if loading fails
            if (theme != Theme.LIGHT) {
                logger.warning("Falling back to light theme");
                applyTheme(Theme.LIGHT);
            }
        }
    }

    /**
     * Switches to the next theme in the cycle.
     */
    public void cycleTheme() {
        Theme[] themes = Theme.values();
        int currentIndex = currentTheme.ordinal();
        int nextIndex = (currentIndex + 1) % themes.length;
        applyTheme(themes[nextIndex]);
    }

    /**
     * Checks if a theme is currently applied.
     */
    public boolean isThemeApplied(Theme theme) {
        return currentTheme == theme;
    }

    /**
     * Gets all available themes.
     */
    public Theme[] getAvailableThemes() {
        return Theme.values();
    }

    /**
     * Detects system theme preference (if supported).
     * Currently returns null as system detection is not implemented.
     */
    public Theme detectSystemTheme() {
        // TODO: Implement system theme detection
        // This would require platform-specific code or JavaFX 17+ features
        return null;
    }

    /**
     * Applies system theme if available, otherwise uses saved preference.
     */
    public void applySystemThemeIfAvailable() {
        Theme systemTheme = detectSystemTheme();
        if (systemTheme != null) {
            applyTheme(systemTheme);
        } else {
            applyTheme(currentTheme);
        }
    }

    /**
     * Reloads the current theme (useful for development).
     */
    public void reloadTheme() {
        applyTheme(currentTheme);
    }
}

