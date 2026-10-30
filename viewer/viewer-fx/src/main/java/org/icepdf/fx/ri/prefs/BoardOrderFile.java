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
package org.icepdf.fx.ri.prefs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The annotation board's card orders, one file for every document the user has ordered:
 * {@code board-order.properties} in the settings folder ({@link ViewerPreferences#home()}).  Kept apart
 * from {@code viewer.properties} because it grows with the documents; only the {@link #MAX_DOCUMENTS}
 * most recently ordered are kept.  Written atomically on every change.
 */
public final class BoardOrderFile {

    private static final Logger logger = Logger.getLogger(BoardOrderFile.class.getName());

    /** Documents whose orders are kept; the ones ordered longest ago are dropped first. */
    public static final int MAX_DOCUMENTS = 200;
    private static final String SEPARATOR = "\t";
    private static final String TOUCHED = "~touched";

    private final Path file;
    private final Properties properties = new Properties();

    private BoardOrderFile(Path file) {
        this.file = file;
        if (file != null && Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException | IllegalArgumentException e) {
                logger.log(Level.WARNING, "Could not read board orders " + file + "; starting without them", e);
            }
        }
    }

    /** The orders in the user's settings folder. */
    public static BoardOrderFile load() {
        return new BoardOrderFile(ViewerPreferences.home().resolve("board-order.properties"));
    }

    /** Orders read from and written to {@code file}. */
    public static BoardOrderFile load(Path file) {
        return new BoardOrderFile(file);
    }

    public synchronized List<String> get(String document, String column) {
        String value = properties.getProperty(document + SEPARATOR + column);
        if (value == null || value.isEmpty()) return List.of();
        return List.copyOf(Arrays.asList(value.split("\n")));
    }

    public synchronized void set(String document, String column, List<String> cards) {
        if (document == null || column == null) return;
        String key = document + SEPARATOR + column;
        if (cards == null || cards.isEmpty()) properties.remove(key);
        else properties.setProperty(key, String.join("\n", cards));
        if (has(document)) properties.setProperty(document + SEPARATOR + TOUCHED, Long.toString(System.currentTimeMillis()));
        else properties.remove(document + SEPARATOR + TOUCHED);
        trim();
        save();
    }

    public synchronized boolean has(String document) {
        String prefix = document + SEPARATOR;
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(prefix) && !key.equals(prefix + TOUCHED)) return true;
        }
        return false;
    }

    /** Drops the documents ordered longest ago, past {@link #MAX_DOCUMENTS}. */
    private void trim() {
        Map<String, Long> touched = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.endsWith(SEPARATOR + TOUCHED)) continue;
            long when;
            try {
                when = Long.parseLong(properties.getProperty(key).trim());
            } catch (NumberFormatException e) {
                when = 0;
            }
            touched.put(key.substring(0, key.length() - (SEPARATOR + TOUCHED).length()), when);
        }
        if (touched.size() <= MAX_DOCUMENTS) return;
        List<Map.Entry<String, Long>> oldest = new ArrayList<>(touched.entrySet());
        oldest.sort(Map.Entry.comparingByValue());
        for (Map.Entry<String, Long> drop : oldest.subList(0, touched.size() - MAX_DOCUMENTS)) {
            String prefix = drop.getKey() + SEPARATOR;
            properties.stringPropertyNames().stream().filter(k -> k.startsWith(prefix)).forEach(properties::remove);
        }
    }

    private void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                properties.store(out, "ICEpdf annotation board card orders");
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save board orders to " + file, e);
        }
    }
}
