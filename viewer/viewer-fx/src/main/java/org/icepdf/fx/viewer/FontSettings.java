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

import org.icepdf.core.pobjects.fonts.FontManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

/**
 * The system fonts core substitutes for fonts a document doesn't embed.  Finding them means reading
 * every font file on the machine, so the list is cached - as the Swing viewer caches it, but in the
 * viewer's own settings directory ({@code fonts.properties}) rather than in {@code java.util.prefs}
 * - and loaded into core's {@link FontManager} at start.  Without a cache the scan runs in the
 * background (a render that needs a substitute meanwhile waits for it).
 * <p>
 * Extra font directories (preference {@code fonts.directories}, one per line) are scanned too.
 */
public final class FontSettings {

    private static final Logger logger = Logger.getLogger(FontSettings.class.getName());

    /** Preference: extra font directories, one per line. */
    public static final String DIRECTORIES = "fonts.directories";
    private static final String VERSION_KEY = "__icepdf.cache.version";
    private static final String CACHE_VERSION = "1";
    private static volatile Thread scan;

    /** One cached font: its name, family, style flags and file. */
    public record FontEntry(String name, String family, int decorations, String path) {
    }

    private FontSettings() {
    }

    /** The cache file. */
    public static Path cacheFile() {
        return ViewerPreferences.home().resolve("fonts.properties");
    }

    /**
     * Loads the cached font list into core, or - with no usable cache - scans in the background and
     * writes one.
     */
    public static void apply(ViewerPreferences preferences) {
        Properties cache = readCache();
        if (cache != null && !cache.isEmpty()) {
            try {
                FontManager.getInstance().setFontProperties(new PropertiesPreferences(cache));
                return;
            } catch (IllegalArgumentException e) {
                logger.log(Level.WARNING, "The font cache could not be read; rescanning", e);
            }
        }
        rescanInBackground(preferences, null);
    }

    /** True while a scan is running. */
    public static boolean isScanning() {
        Thread running = scan;
        return running != null && running.isAlive();
    }

    /**
     * Forgets the font list and reads every font again (system directories plus the extra ones), in
     * the background, then saves the cache.
     *
     * @param done run on the scanning thread when finished; may be null
     */
    public static synchronized void rescanInBackground(ViewerPreferences preferences, Runnable done) {
        if (isScanning()) return;
        String[] extra = directories(preferences).toArray(new String[0]);
        Thread thread = new Thread(() -> {
            try {
                FontManager manager = FontManager.getInstance();
                manager.clearFontList();
                manager.readSystemFonts(extra);
                writeCache(manager.getFontProperties());
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Font scan failed", e);
            } finally {
                if (done != null) done.run();
            }
        }, "icepdf-fx-font-scan");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        scan = thread;
        thread.start();
    }

    /** Deletes the cache file; the next start scans again. */
    public static void clearCache() {
        try {
            Files.deleteIfExists(cacheFile());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not delete the font cache", e);
        }
    }

    /** The fonts core knows, by name. */
    public static List<FontEntry> fonts() {
        Properties properties = FontManager.getInstance().getFontProperties();
        List<FontEntry> fonts = new ArrayList<>(properties.size());
        for (String name : properties.stringPropertyNames()) {
            FontEntry entry = parse(name, properties.getProperty(name));
            if (entry != null) fonts.add(entry);
        }
        fonts.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return fonts;
    }

    /** The extra font directories. */
    public static List<String> directories(ViewerPreferences preferences) {
        String value = preferences.get(DIRECTORIES, "");
        if (value.isEmpty()) return Collections.emptyList();
        List<String> directories = new ArrayList<>();
        for (String line : value.split("\n")) {
            if (!line.trim().isEmpty()) directories.add(line.trim());
        }
        return directories;
    }

    public static void setDirectories(ViewerPreferences preferences, List<String> directories) {
        preferences.put(DIRECTORIES, directories.isEmpty() ? null : String.join("\n", directories));
    }

    static FontEntry parse(String name, String value) {
        if (value == null) return null;
        StringTokenizer tokens = new StringTokenizer(value, "|");
        if (tokens.countTokens() < 3) return null;
        try {
            String family = tokens.nextToken();
            int decorations = Integer.parseInt(tokens.nextToken());
            String path = tokens.nextToken();
            return new FontEntry(name, family, decorations, path);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The cache, without entries whose file has gone; null when there is none or it's another version. */
    static Properties readCache() {
        Path file = cacheFile();
        if (!Files.isRegularFile(file)) return null;
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
        if (!CACHE_VERSION.equals(properties.remove(VERSION_KEY))) return null;
        properties.stringPropertyNames().forEach(name -> {
            FontEntry entry = parse(name, properties.getProperty(name));
            if (entry == null || !new File(entry.path()).isFile()) properties.remove(name);
        });
        return properties;
    }

    static void writeCache(Properties fonts) {
        Path file = cacheFile();
        try {
            Files.createDirectories(file.getParent());
            Properties out = new Properties();
            out.putAll(fonts);
            out.setProperty(VERSION_KEY, CACHE_VERSION);
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream stream = Files.newOutputStream(temp)) {
                out.store(stream, "ICEpdf JavaFX viewer font cache");
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not write the font cache", e);
        }
    }

    /**
     * A {@link java.util.prefs.Preferences} over a {@link Properties}, in memory only - the shape
     * {@link FontManager#setFontProperties} takes - so the cache never goes near the user's
     * {@code java.util.prefs}.
     */
    static final class PropertiesPreferences extends AbstractPreferences {
        private final Properties properties;

        PropertiesPreferences(Properties properties) {
            super(null, "");
            this.properties = properties;
        }

        @Override
        protected void putSpi(String key, String value) {
            properties.setProperty(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return properties.getProperty(key);
        }

        @Override
        protected void removeSpi(String key) {
            properties.remove(key);
        }

        @Override
        protected void removeNodeSpi() {
            properties.clear();
        }

        @Override
        protected String[] keysSpi() {
            return properties.stringPropertyNames().toArray(new String[0]);
        }

        @Override
        protected String[] childrenNamesSpi() {
            return new String[0];
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            throw new UnsupportedOperationException("no children");
        }

        @Override
        protected void syncSpi() throws BackingStoreException {
            // in memory
        }

        @Override
        protected void flushSpi() throws BackingStoreException {
            // in memory
        }
    }
}
