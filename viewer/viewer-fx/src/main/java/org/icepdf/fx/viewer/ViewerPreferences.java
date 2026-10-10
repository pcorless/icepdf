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
package org.icepdf.fx.viewer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The viewer's settings, kept as a properties file - {@code ~/.icepdf/fx/viewer.properties}, or
 * under the directory named by {@code -Dorg.icepdf.fx.viewer.home} - and deliberately not in
 * {@code java.util.prefs}, which the Swing viewer uses: the two viewers' settings stay apart, and a
 * test can point this at a scratch directory (or {@link #inMemory()}) without touching the user's.
 * <p>
 * Values are strings underneath; the typed getters fall back to their default when a value is
 * missing or malformed.  {@link #save()} writes the file (atomically); nothing is written before.
 */
public final class ViewerPreferences {

    private static final Logger logger = Logger.getLogger(ViewerPreferences.class.getName());

    /** The system property naming the settings directory. */
    public static final String HOME_PROPERTY = "org.icepdf.fx.viewer.home";
    private static final int MAX_RECENT = 10;

    // keys
    public static final String RECENT_FILES = "file.recent";
    public static final String LAST_DIRECTORY = "file.lastDirectory";
    public static final String WINDOW_X = "window.x";
    public static final String WINDOW_Y = "window.y";
    public static final String WINDOW_WIDTH = "window.width";
    public static final String WINDOW_HEIGHT = "window.height";
    public static final String WINDOW_MAXIMIZED = "window.maximized";
    public static final String SIDE_PANEL_VISIBLE = "sidePanel.visible";
    public static final String SIDE_PANEL_DIVIDER = "sidePanel.divider";
    public static final String SIDE_PANEL_TAB = "sidePanel.tab";
    public static final String VIEW_MODE = "view.mode";
    public static final String FIT_MODE = "view.fit";
    public static final String ZOOM = "view.zoom";
    public static final String PAGE_GAP = "view.pageGap";
    public static final String VERIFY_SIGNATURES = "signatures.verifyOnOpen";
    public static final String ANNOTATION_AUTHOR = "annotation.author";
    public static final String ANNOTATION_COLOR = "annotation.color";
    public static final String HIGHLIGHT_FIELDS = "forms.highlightFields";
    public static final String PAINT_ANNOTATIONS = "view.paintAnnotations";
    public static final String SEARCH_CASE = "search.caseSensitive";
    public static final String SEARCH_WHOLE_WORD = "search.wholeWord";
    public static final String SEARCH_FOLD_ACCENTS = "search.foldAccents";
    public static final String SEARCH_REGEX = "search.regex";
    public static final String SEARCH_COMMENTS = "search.comments";
    public static final String SEARCH_FORMS = "search.forms";
    public static final String SEARCH_OUTLINES = "search.outlines";
    public static final String SEARCH_DESTINATIONS = "search.destinations";
    public static final String FONT_CACHE = "fonts.cache";

    private final Path file;
    private final Properties properties = new Properties();

    private ViewerPreferences(Path file) {
        this.file = file;
        if (file != null && Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException | IllegalArgumentException e) {
                logger.log(Level.WARNING, "Could not read viewer settings " + file + "; using defaults", e);
            }
        }
    }

    /** The settings in the user's settings directory (see the class comment). */
    public static ViewerPreferences load() {
        return new ViewerPreferences(home().resolve("viewer.properties"));
    }

    /** Settings read from and saved to {@code file}. */
    public static ViewerPreferences load(Path file) {
        return new ViewerPreferences(file);
    }

    /** Settings that are never read or written - for tests and embedding without persistence. */
    public static ViewerPreferences inMemory() {
        return new ViewerPreferences(null);
    }

    /** The settings directory: {@code -Dorg.icepdf.fx.viewer.home}, else {@code ~/.icepdf/fx}. */
    public static Path home() {
        String configured = System.getProperty(HOME_PROPERTY);
        if (configured != null && !configured.isEmpty()) return Paths.get(configured);
        return Paths.get(System.getProperty("user.home"), ".icepdf", "fx");
    }

    /** The file the settings live in, or null when they are in memory only. */
    public Path getFile() {
        return file;
    }

    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public void put(String key, String value) {
        if (value == null) properties.remove(key);
        else properties.setProperty(key, value);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    public void putBoolean(String key, boolean value) {
        put(key, String.valueOf(value));
    }

    public double getDouble(String key, double defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public void putDouble(String key, double value) {
        put(key, String.valueOf(value));
    }

    public <E extends Enum<E>> E getEnum(String key, Class<E> type, E defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    public void putEnum(String key, Enum<?> value) {
        put(key, value != null ? value.name() : null);
    }

    /** Recently opened files, most recent first; files that no longer exist are left out. */
    public List<Path> getRecentFiles() {
        String value = properties.getProperty(RECENT_FILES);
        if (value == null || value.isEmpty()) return Collections.emptyList();
        List<Path> files = new ArrayList<>();
        for (String entry : value.split("\n")) {
            if (entry.isEmpty()) continue;
            Path path = Paths.get(entry);
            if (Files.isRegularFile(path) && !files.contains(path)) files.add(path);
        }
        return files;
    }

    /** Moves {@code file} to the front of the recent files, keeping the last ten. */
    public void addRecentFile(Path file) {
        List<Path> files = new ArrayList<>(getRecentFiles());
        Path absolute = file.toAbsolutePath().normalize();
        files.remove(absolute);
        files.add(0, absolute);
        while (files.size() > MAX_RECENT) files.remove(files.size() - 1);
        StringBuilder joined = new StringBuilder();
        for (Path path : files) joined.append(path).append('\n');
        put(RECENT_FILES, joined.toString());
    }

    public void clearRecentFiles() {
        properties.remove(RECENT_FILES);
    }

    /** Writes the settings file (via a temporary file, so a crash can't leave half of it). */
    public void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                properties.store(out, "ICEpdf JavaFX viewer settings");
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save viewer settings to " + file, e);
        }
    }
}
